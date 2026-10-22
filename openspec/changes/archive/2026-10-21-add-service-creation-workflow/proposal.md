# Proposal: Add the service creation workflow

## Why

DevPlane can generate a working service from the `java-rest-api` template, but
only from Java code: no Platform API endpoint reaches the generator. A developer
can register a service but can't get its code. This change exposes generation
through the API, so creating a service works end to end: register it in the
catalog, then download its generated project. The web UI and repository
provisioning will build on these endpoints.

Roadmap: completes the Phase 1 item "Service creation workflow".

## What Changes

- Add `GET /api/v1/templates`, which lists the templates services can be created
  from, each with a name and a short description. Today that is `java-rest-api`.
- Add `GET /api/v1/services/{name}/project`, which generates the project for a
  registered service and returns it as a zip archive (`orders-api.zip`, holding an
  `orders-api/` folder):
  - The project is generated from the service's current name, owner, and
    description in the catalog.
  - An optional `template` query parameter picks one of the listed templates and
    defaults to `java-rest-api`, the golden path.
  - The archive keeps `gradlew` executable, so the unzipped project builds on Linux
    and macOS without a `chmod`.
  - An unknown service returns 404 and an unknown template returns 400, both as
    problem details.
- Downloading changes nothing: the catalog entry stays as it was, and DevPlane
  keeps no copy of the project. Each request generates a fresh one.

**Out of scope**

- Pushing the project to a GitHub repository ("Repository provisioning", the next
  change)
- A web UI for creating services (a later Phase 1 change)
- Recording in the catalog which template a service was created from. Repository
  provisioning can record it together with the repository, once the service's code
  has a permanent home.
- A single request that both registers a service and returns its project: clients
  make the two calls
- Keeping generated projects on the server
- New templates, or options for customizing what a template generates
- Updating projects that were already downloaded when the template or the
  service's details change
- Dockerfiles, CI pipelines, and anything else from Phase 2 onward

## Capabilities

### New Capabilities

- `service-creation`: Getting a new service's code from DevPlane: listing the
  templates to choose from, and downloading the project generated for a registered
  service.

### Modified Capabilities

None. The catalog's records and endpoints are unchanged, generation follows the
existing `service-templates` requirements, and the new endpoints follow the
existing `platform-api` conventions for errors and API documentation.

## Impact

- **Code:** the `templates` package gains the list of available templates, zip
  packaging of a generated project, and controllers for the two endpoints. The
  catalog gains a small public, read-only lookup so that other features can read a
  registered service; its entity stays private to the package.
- **API:** two new endpoints, `GET /api/v1/templates` and
  `GET /api/v1/services/{name}/project`. Existing endpoints don't change.
- **Dependencies:** none. Archives are written with the JDK's built-in zip file
  system.
- **Docs:** README.md (how to create a service), docs/architecture.md (the new
  endpoints and the catalog lookup).
