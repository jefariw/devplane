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
├── catalog/    the service catalog: REST controller, request and response records,
│               ServiceCatalog (business rules), ServiceEntity and its repository
└── templates/  ProjectGenerator, which generates new service projects from templates
```

A request flows from the controller, which validates its body, to
`ServiceCatalog`, then through a Spring Data JPA repository to PostgreSQL.
Every error becomes an RFC 9457 problem details response in
`api/ApiExceptionHandler`.

Other features read the catalog through `ServiceCatalog.lookUp(name)`, which
returns a read-only `RegisteredService` (name, owner, and description) and throws
a 404 error for an unknown name. Everything else in the `catalog` package,
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
