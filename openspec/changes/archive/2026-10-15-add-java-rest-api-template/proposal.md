# Proposal: Add the Java REST API template

## Why

DevPlane's goal is that a developer can ask for "a Java REST API with PostgreSQL"
and get a working service, but today it can only record services that already
exist. This change adds the golden-path template and the generator that turns it
into a real project, which the service creation workflow and repository
provisioning will build on.

Roadmap: completes the Phase 1 item "Golden-path service template" and builds the
generator that the next item, "Service creation workflow", will expose.

## What Changes

- Add a `java-rest-api` template: a complete Spring Boot (Java 21) service backed
  by PostgreSQL that follows DevPlane's own conventions.
- Every generated service includes:
  - a working example resource at `/api/v1/items` (create, view, and list items,
    stored in PostgreSQL through Flyway migrations)
  - validation and problem-details error responses
  - a health check and OpenAPI documentation
  - Testcontainers tests for all of the above
  - a README naming the service and its owner, with run and test instructions
- Add a generator that fills the template in for a service name, owner, and
  optional description, deriving the Java package, application class, and
  database name from the service name.
- Prove every generated project works: a test generates a project and runs its
  own `./gradlew build`, as part of DevPlane's build and CI.

**Out of scope**

- An API endpoint for generating projects (the next change, "Service creation
  workflow")
- Creating GitHub repositories for generated projects ("Repository provisioning")
- Dockerfiles and CI pipelines in generated services (Phase 2)
- Metrics and structured logs in generated services (Phase 3)
- Other languages or templates, and options for customizing a template
- Updating services that were already generated when the template changes

## Capabilities

### New Capabilities

- `service-templates`: Generating new service projects from golden-path templates,
  and what a project generated from each template contains and does.

### Modified Capabilities

None. The generator doesn't change the catalog or the Platform API.

## Impact

- **Code:** a new `templates` package in DevPlane (the generator), and the template
  files under `src/main/resources/service-templates/java-rest-api/`.
- **API:** no changes.
- **Dependencies:** none for DevPlane itself. Generated services use the same
  libraries DevPlane does.
- **Build and CI:** a new `templateTest` Gradle task that generates and builds a
  project. It runs as part of `./gradlew build` and CI, adding one to two minutes,
  while `./gradlew test` stays fast.
- **Docs:** README.md and CLAUDE.md (the new task), docs/architecture.md (the
  generator).
