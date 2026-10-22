# DevPlane Architecture

DevPlane will consist of four primary layers:

1. Developer interface
2. Platform API
3. Infrastructure and deployment layer
4. Observability and AI operations layer

## Initial Architecture

Developer
↓
DevPlane UI
↓
DevPlane API
↓
Service Catalog
↓
Platform Integrations

## Platform API Application

The Platform API is a single Spring Boot application (Java 21) at the repository
root, backed by PostgreSQL. Its code is organized by feature, and later features
such as templates and deployments become sibling packages:

```
io.github.spollito.devplane
├── api/        behavior shared by every endpoint: problem details error responses
├── catalog/       the service catalog: REST controller, request and response
│                  records, ServiceCatalog (business rules), ServiceEntity and its
│                  repository
├── provisioning/  repository provisioning: the endpoint that gives a service its
│                  GitHub repository, GitHubClient, and ProjectPusher (Git push)
└── templates/     service templates: ProjectGenerator, which generates new service
                   projects, ProjectArchiver, which zips them, and the endpoints
                   that list templates and download a service's project
```

Dependencies between feature packages point one way: `templates` uses `catalog`,
and `provisioning` uses both. `catalog` depends on neither.

A request flows from the controller, which validates its body, to
`ServiceCatalog`, then through a Spring Data JPA repository to PostgreSQL.
Every error becomes an RFC 9457 problem details response in
`api/ApiExceptionHandler`.

Other features read the catalog through `ServiceCatalog.lookUp(name)`, which
returns a read-only `RegisteredService` (name, owner, description, and repository)
and throws a 404 error for an unknown name. The one change they can make is
`ServiceCatalog.recordRepository(name, url, template)`, which stores a service's
GitHub repository once it exists. Everything else in the `catalog` package,
including the JPA entity, stays package-private, so only the catalog changes
services.

The API is described at `/v3/api-docs` (browsable at `/swagger-ui.html`), and
`/actuator/health` reports whether the application can reach its database.

### Database

Flyway migrations in `src/main/resources/db/migration` own the schema, and
Hibernate only validates that the entities match it.

The `services` table holds the catalog:

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `bigint` identity | Internal primary key, never exposed by the API |
| `name` | `varchar(63)` | Unique; how the API identifies a service |
| `owner` | `varchar(63)` | Indexed for listing services by owner |
| `description` | `varchar(300)` | Optional |
| `lifecycle` | `varchar(20)` | `EXPERIMENTAL`, `PRODUCTION`, or `DEPRECATED` (check constraint) |
| `created_at`, `updated_at` | `timestamptz` | Set by the application, at microsecond precision |
| `repository_url`, `repository_template`, `repository_created_at` | `varchar(255)`, `varchar(63)`, `timestamptz` | The service's GitHub repository and the template it was generated from; all three are null until a repository is provisioned, and a check constraint keeps them set together |

### Service Templates

Golden-path templates are ordinary project files stored as resources in
`src/main/resources/service-templates/<template-name>/`. `ProjectGenerator` copies
a template into an empty directory for a service name, owner, and description:

- `{{variable}}` in file contents and file paths is replaced with values such as
  `serviceName`, `packageName`, and `className`, for example
  `src/main/java/{{packagePath}}/{{className}}Application.java`. An unknown or
  malformed placeholder fails generation before anything is written.
- Files named with a single leading `_` become dotfiles (`_gitignore` becomes
  `.gitignore`), because Gradle leaves `.gitignore` files out of resources. Names
  like `__init__.py` and Flyway's `V1__create_items_table.sql` are kept as they are.
- `.jar` files are copied unchanged, and `gradlew` is made executable.

The only template so far is `java-rest-api`. Because DevPlane doesn't compile
template files, the `templateTest` Gradle task generates a project from it and
runs that project's own build.

The templates the Platform API offers are listed in code, in `ServiceTemplates`,
each with a name and a short description, and `GET /api/v1/templates` returns that
list. Test-only templates sit in the same `service-templates/` classpath folder
during tests, but the API never offers them because they aren't listed.

`ProjectArchiver` packages a generated project as a zip archive, in a folder named
after the service. It opens the JDK's zip file system on a temporary file, with
POSIX file attributes enabled, and points the generator at a folder inside it, so
the generator writes into the archive through the same `Files` calls it uses for a
directory. Making `gradlew` executable becomes that entry's Unix mode, so the
project still builds after it's unzipped on Linux or macOS. The temporary file is
deleted once the archive has been read into memory.

`GET /api/v1/services/{name}/project` ties these together. It checks that the
requested template (`java-rest-api` by default) is listed, looks the service up
with `ServiceCatalog.lookUp`, and returns the archive as `<name>.zip`. Nothing is
stored: every request generates the project from the service's current details.
The endpoint lives in the `templates` package even though its path is under the
service, so that `templates` depends on `catalog` and never the reverse.

### Repository Provisioning

`POST /api/v1/services/{name}/repository` gives a registered service a GitHub
repository holding its generated project. `RepositoryProvisioner` runs the steps
in order:

1. Looks the service up with `ServiceCatalog.lookUp` (404 if it isn't registered,
   409 if it already has a repository), then checks that a GitHub token is
   configured (503 if not).
2. Generates the project into a `devplane-` temporary directory, before anything
   happens on GitHub, so a broken template never leaves an empty repository.
3. `GitHubClient` creates the repository with `POST /user/repos` on GitHub's REST
   API, in the account that owns the token. A name the account already uses is a
   409; any other GitHub failure is a 502.
4. `ProjectPusher` uses JGit to commit the project as the single first commit on
   `main`, as the token owner's GitHub no-reply address, with `gradlew` staged as
   executable, and pushes it. It shuts out the developer's own Git configuration
   (line-ending conversion, commit signing, and hooks) so it can't change the
   commit. A failed push is a 502 whose detail names the empty repository left
   on GitHub.
5. `ServiceCatalog.recordRepository` stores the repository's URL and template, and
   the temporary directory is deleted.

The provisioner isn't transactional: each catalog call has its own transaction,
so none stays open while DevPlane waits for GitHub.

The token comes from the `DEVPLANE_GITHUB_TOKEN` environment variable (bound to
`devplane.github.token`), and `devplane.github.api-url` defaults to
`https://api.github.com`. DevPlane starts without a token, and the token never
appears in logs, responses, or the pushed repository. Tests point `api-url` at
`FakeGitHub`, a small HTTP server in the test JVM that creates bare Git
repositories, so neither CI nor contributors need a token.
