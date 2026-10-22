# Design: Add the service creation workflow

## Context

See proposal.md for why, and specs/service-creation/spec.md for the behavior.
What exists today:

- `ProjectGenerator.generate(template, project, targetDirectory)` renders a whole
  template in memory, then writes it into a missing or empty directory through
  `java.nio.file.Files`. It validates names with `catalog.CatalogConstraints`, so
  the `templates` package already depends on `catalog`.
- The catalog keeps everything package-private: `ServiceCatalog`, `ServiceEntity`,
  the repository, and the controller. No other package can read a service yet.
- Feature exceptions extend `ErrorResponseException` (the catalog's 404 and 409),
  and `ApiExceptionHandler` turns anything unexpected into a 500 without details.
- In tests, test-only templates (`test-template`, `broken-path`, and others) sit in
  the same `service-templates/` classpath folder as the real template.

## Goals / Non-Goals

**Goals:**

- Reuse the generator unchanged: a downloaded archive holds exactly the files that
  generating into a directory produces.
- Keep package dependencies one-way: `templates` uses `catalog`, never the reverse.
- Keep the workflow stateless: nothing outlives a request.

**Non-Goals:**

- Streaming archives. A project is about 30 small files, around 100 KB zipped.
- Other archive formats, such as `.tar.gz`.

## Decisions

### 1. GET from the catalog entry, with nothing recorded

`GET /api/v1/services/{name}/project` reads the service and generates its project.
It changes no state, so GET fits: it's safe and repeatable, and a plain link can
download it, which suits the web UI.

- A `template` field on `POST /api/v1/services` that registers the service and
  returns the archive was rejected. One response would mix a JSON resource with a
  binary body, and a failed generation would leave a registration to undo.
- Recording the template on the service when its project is downloaded was
  rejected. A download can be thrown away, so the record could name a template the
  developer never used, and changing templates would need a conflict rule.
  Repository provisioning produces the first permanent result, so it's the place to
  record the template, next to the repository.
- Recording the template at registration was rejected for now. The catalog would
  have to validate template names, making `catalog` depend on `templates`, which
  already depends on `catalog`.

### 2. A zip archive, written through the JDK's zip file system

The response is a zip in the Spring Initializr style: `orders-api.zip` holding an
`orders-api/` folder. A new `ProjectArchiver` builds it:

1. Opens a zip file system (the JDK's `jdk.zipfs` module) on a new archive file in
   a `devplane-` temporary directory, with POSIX file attributes enabled and
   default permissions `rw-r--r--`. The directory also holds any temporary files
   the zip file system writes while it closes.
2. Calls the existing generator with the archive's `/orders-api` path as the
   target directory. The generator's `Files` calls work unchanged on that file
   system, and its step that makes `gradlew` executable is stored as the entry's
   Unix mode (`rwxr-xr-x`).
3. Closes the file system, reads the archive into a byte array, and deletes the
   temporary directory in a `finally` block.

Alternatives:

- `java.util.zip.ZipOutputStream` can't set an entry's Unix mode, so `gradlew`
  would unzip without its executable bit and fail with "permission denied" on Linux
  and macOS, including in GitHub Actions once the project is pushed.
- Apache Commons Compress, which Spring Initializr uses, can set Unix modes, but it
  would be a new dependency for something the JDK already does.
- Generating into a directory on the server works while DevPlane runs on the
  developer's laptop. A server path means nothing to a client once DevPlane runs
  elsewhere, though, and DevPlane would have to manage those directories.

### 3. Available templates are listed in code

A `ServiceTemplates` component holds a list of `ServiceTemplate(name, description)`
records. Today it lists one: `java-rest-api`, "Spring Boot (Java 21) REST API with
PostgreSQL". `GET /api/v1/templates` returns that list, and the download rejects
any template not on it before generating.

- Scanning `service-templates/` on the classpath was rejected. In tests it would
  find the test-only templates, folder listings behave differently inside a jar,
  and each template needs a description anyway.
- A descriptor file per template, such as `service-templates/java-rest-api.properties`,
  is more mechanism than one template needs. It's an easy switch if templates
  multiply.
- A test generates every listed template, so the list can't name a template that
  doesn't exist.

### 4. A public, read-only catalog lookup

`ServiceCatalog` becomes public and gains one public method, `lookUp(name)`. It
returns a new public `RegisteredService` record (`name`, `owner`, `description`)
and throws the existing `ServiceNotFoundException`, which renders as 404 wherever
it's thrown. The other methods, `ServiceEntity`, and the repository stay
package-private, so other features can read services but only the catalog changes
them. Later features add fields to `RegisteredService` as they need them.

- Putting the download endpoint in the catalog package was rejected: it would need
  the generator, so `catalog` and `templates` would depend on each other.
- Making `ServiceEntity` public was rejected: other packages could then change a
  managed JPA entity outside the catalog's rules.

### 5. Endpoints, request handling, and errors

Both endpoints live in the `templates` package, next to the generator:
`TemplateController` serves `GET /api/v1/templates`, and `ProjectController` serves
`GET /api/v1/services/{name}/project`. The project path sits under the service
because the project belongs to it, while the code stays with the generator to keep
the dependency one-way.

`ProjectController` handles a request in this order:

1. If `template` isn't listed, it throws `UnknownTemplateException`, an
   `ErrorResponseException` with status 400 whose problem details carry an `errors`
   entry for `template`, built from `ApiExceptionHandler.InvalidField` so it matches
   other validation errors. This check comes first because it needs no database.
2. `ServiceCatalog.lookUp(name)` returns the service or throws for a 404.
3. `ProjectArchiver` builds the archive. A `ProjectGenerationException` at this
   point is a bug in DevPlane or its template, not a client error, so the existing
   500 fallback handles it.

The mapping declares `produces = "application/zip"`, so the OpenAPI description
shows the binary response, and it sets `Content-Disposition` with Spring's
`ContentDisposition.attachment()`. When a request fails, Spring MVC ignores the
handler's `produces` and renders problem details as `application/problem+json`
even when the `Accept` header allows only `application/zip`. The spec's 404
scenario covers that case, so a test catches it if Spring's behavior changes.

### Later roadmap items this must not block

- **Repository provisioning:** uses `ServiceCatalog.lookUp` and the generator, and
  can record the template together with the repository.
- **Web UI:** a template picker from `GET /api/v1/templates`, and a download link
  to the project endpoint.
- **MCP server (Phase 4):** both endpoints are plain GETs with OpenAPI descriptions.
- **Containerization and CI/CD (Phase 2):** they add template files. The archive
  already keeps `gradlew` executable, which a CI pipeline needs.

## Risks / Trade-offs

- [The zip file system's POSIX support is a less familiar JDK feature] → A unit
  test reads `gradlew`'s mode back from the archive. Commons Compress is the
  fallback if it misbehaves.
- [Two downloads of the same service can differ if its details, or DevPlane's
  Spring Boot version, changed in between] → Intended: the project reflects the
  catalog as it is now ("Uses the service's current details").
- [A crash mid-request can leave a temporary directory behind] → It holds one
  small archive, lives in the OS temporary directory, and has a `devplane-` prefix.
- [The template list and the template folders can drift apart] → A test generates
  every listed template.
- [An unknown service with an unknown template returns 400, not 404, because the
  template is checked first] → Both are client errors, and the client fixes one at
  a time.
