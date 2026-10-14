# Tasks

## 1. Project setup

- [x] 1.1 Install a Docker runtime (Docker Desktop or Colima) and verify `docker run --rm hello-world` succeeds
- [x] 1.2 Generate the application with Spring Initializr (Gradle - Kotlin, Java 21, Spring Boot 4.1.x, group `io.github.spollito`, artifact `devplane`) with Spring Web, Validation, Spring Data JPA, PostgreSQL Driver, Flyway Migration, Spring Boot Actuator, Docker Compose Support, and Testcontainers; place it at the repository root and verify `./gradlew build` passes
- [x] 1.3 Add `compose.yaml` with PostgreSQL 18 and set `spring.jpa.open-in-view=false`, `spring.jpa.hibernate.ddl-auto=validate`, and `server.address=127.0.0.1`; verify `./gradlew bootRun` starts both the database and the application
- [x] 1.4 Add `.github/workflows/build.yml` that runs `./gradlew build` on Java 21 for pushes to main and pull requests, and verify the workflow passes on GitHub
- [x] 1.5 Document the prerequisites (JDK 21 and a Docker runtime) and the build, test, and run commands in README.md and in a "Commands" section of CLAUDE.md, and verify each command works as written

## 2. Platform API conventions

- [x] 2.1 Add an `@RestControllerAdvice` that extends `ResponseEntityExceptionHandler` and has a generic 500 fallback, and verify the "Unknown endpoint", "Malformed request body", and "Unexpected errors hide internals" scenarios with tests that use a test-only controller
- [x] 2.2 Add the `errors` array to validation failures, and verify the "Reports every invalid field" scenario with the test-only controller
- [x] 2.3 Add springdoc-openapi 3.x, verify the "Serves the OpenAPI description" and "Serves the documentation page" scenarios with tests, and add the documentation URL to README.md
- [x] 2.4 Verify the "Healthy" and "Database unreachable" scenarios with tests, giving the unreachable case its own database container

## 3. Register services

- [x] 3.1 Add a Flyway migration that creates the `services` table (surrogate id, unique name, owner, description, lifecycle with a CHECK constraint, timestamps), and verify the application starts against an empty database with schema validation on
- [x] 3.2 Add `ServiceEntity`, the `Lifecycle` enum, and the Spring Data repository, and verify with a repository test that a service saves and reloads and that the database rejects a duplicate name
- [x] 3.3 Add `POST /api/v1/services` backed by `ServiceCatalog` (timestamps from an injected `Clock`), and verify the "Registers a new service", "Accepts a valid identifier", "Defaults to experimental", and "Accepts an explicit stage" scenarios with integration tests
- [x] 3.4 Add request validation for names, owners, description length, and lifecycle values, and verify the "Rejects missing required fields", "Rejects an overlong description", "Rejects an unknown stage", "Rejects uppercase letters and spaces", "Rejects a name longer than 63 characters", "Rejects a leading digit or a trailing hyphen", and "Applies the same rules to owners" scenarios (parameterized tests where scenarios share a shape)
- [x] 3.5 Turn a duplicate-name constraint violation into 409 Conflict, and verify the "Rejects a duplicate name" scenario
- [x] 3.6 Describe the package layout and the `services` table in docs/architecture.md, and verify the document matches the code

## 4. View and list services

- [x] 4.1 Add `GET /api/v1/services/{name}`, and verify the "Returns a registered service" and "Returns 404 for an unknown service" scenarios
- [x] 4.2 Add `GET /api/v1/services` returning a `PageResponse` sorted by name (0-based pages, default size 20, maximum 100), and verify the "Lists services alphabetically", "Empty catalog", "Returns the requested page", and "Caps the page size" scenarios
- [x] 4.3 Add the `owner` query parameter, and verify the "Lists only the owner's services" and "Owner with no services" scenarios

## 5. Update and unregister services

- [x] 5.1 Add `PUT /api/v1/services/{name}` with the same validation as registration, and verify the "Updates a service", "Rejects an invalid update", and "Rejects an update to an unknown service" scenarios
- [x] 5.2 Add `DELETE /api/v1/services/{name}`, and verify the "Removes a service" and "Rejects deleting an unknown service" scenarios

## 6. Integration checks

- [ ] 6.1 Add an integration test that registers a service, restarts the application against the same database container, and finds the service again, covering the "Service is still listed after a restart" scenario
- [ ] 6.2 Verify with a test that `/v3/api-docs` documents all five catalog endpoints
- [ ] 6.3 Register, list, update, and unregister a service with curl against `./gradlew bootRun`, confirm each response matches the specs, then tick "Service catalog" and "Platform API" in the README roadmap

## Workflow follow-up

- Run `/opsx:verify add-service-catalog`, then `/opsx:archive add-service-catalog` to merge this change's specs into `openspec/specs/`.
