# Proposal: Add the service catalog

## Why

Every later DevPlane feature (generating services from templates, provisioning
repositories, deploying, monitoring health) needs to know which services exist and
who owns them, and there is no application yet to hold that record. This change
builds the catalog and, with it, the Platform API that later features extend.

Roadmap: completes the Phase 1 items "Service catalog" and "Platform API".

## What Changes

- Stand up the DevPlane Platform API: a REST API under `/api/v1` with OpenAPI
  documentation, one error format for every endpoint, and a health check.
- Add the service catalog to the API. A client can:
  - register a service with a name, an owner, an optional description, and a
    lifecycle stage (`experimental`, `production`, or `deprecated`)
  - view a service, or list services alphabetically one page at a time,
    optionally filtered by owner
  - update a service's owner, description, and lifecycle stage
  - unregister a service
- Require service names and owners to be lowercase, DNS-safe identifiers, so they
  stay valid as repository names, Kubernetes names, and labels in later phases.
- Add a GitHub Actions workflow that builds and tests the application on every
  push and pull request.

**Out of scope**

- A web UI (a later Phase 1 change)
- Generating services from templates and provisioning their repositories (later
  Phase 1 changes)
- Authentication: DevPlane runs locally for a single user until a change adds it
- Teams or people as records of their own: an owner is just an identifier for now
- Renaming services: a name is fixed at registration
- Search, sorting options, or filters other than owner
- Anything from Phases 2–4, such as deployments or health monitoring of the
  services in the catalog

## Capabilities

### New Capabilities

- `service-catalog`: Registering, viewing, listing, updating, and unregistering
  services, each with a name, owner, description, and lifecycle stage.
- `platform-api`: Conventions every Platform API endpoint follows: the error
  response format, OpenAPI documentation, and a health check.

### Modified Capabilities

None. This is the project's first change.

## Impact

- **Code:** a new Spring Boot application at the repository root (Gradle build and
  `src/`), plus `compose.yaml` for a local PostgreSQL.
- **API:** new endpoints under `/api/v1/services`, plus `/v3/api-docs`,
  `/swagger-ui.html`, and `/actuator/health`.
- **Dependencies:** Spring Boot 4 (web, validation, Spring Data JPA, Actuator,
  Docker Compose support), the PostgreSQL driver, Flyway, springdoc-openapi, and
  Testcontainers.
- **Local setup:** a Docker runtime becomes a prerequisite for running and testing
  DevPlane. None is installed on the development machine yet.
- **CI:** a new `build.yml` workflow next to the existing OpenSpec validation.
- **Docs:** README.md (prerequisites, commands, roadmap), CLAUDE.md (commands), and
  docs/architecture.md.
