# Proposal: Add repository provisioning

## Why

A developer can register a service and download its generated project, but then
has to create a GitHub repository, unzip the project, and push it by hand. Each
time, that step can drift from the golden path: a different repository name, a
missing executable bit on `gradlew`, a first commit nobody can trace back to
DevPlane. With this change, one request puts the project in a new repository and
records that repository in the catalog. A permanent home for each service's code
is also what Phase 2 builds on, because the CI pipeline and deployments start
from that repository.

Roadmap: completes the Phase 1 item "Repository provisioning".

## What Changes

- Add `POST /api/v1/services/{name}/repository`, which:
  - creates a GitHub repository named after the service, in the personal account
    that owns DevPlane's GitHub token, with the service's description
  - pushes the service's generated project to it as a single first commit on
    `main`, with `gradlew` executable
  - records the repository in the catalog
- The optional JSON request body has two fields:
  - `template` picks one of the templates from `GET /api/v1/templates`. The
    default is `java-rest-api`.
  - `visibility` is `private` (the default) or `public`.
- On success, the endpoint returns 201 Created:
  - The body has the repository's `url`, the `template` it was generated from, and
    `createdAt`.
  - `Location` is the repository's GitHub URL.
- Failures are problem details responses, and none of them records anything in the
  catalog:
  - 404 for an unknown service
  - 400 for an unknown template or visibility
  - 409 when the service already has a repository, or the account already has a
    repository with that name
  - 503 when no GitHub token is configured
  - 502 when GitHub rejects the request or the push fails. If the repository was
    created but the push failed, the detail names the empty repository that was
    left behind.
- Services in Platform API responses gain a `repository` field:
  - It is `null` until a repository is provisioned. After that it holds the same
    `url`, `template`, and `createdAt` as the provisioning response.
  - Provisioning updates the service's `updatedAt`.
- Unregistering a service leaves its GitHub repository untouched.
- DevPlane reads the token from the `DEVPLANE_GITHUB_TOKEN` environment variable.
  It should be a fine-grained personal access token.
  - DevPlane still starts, and every other endpoint still works, without a token.
  - The token never appears in logs, responses, or the pushed repository.

**Out of scope**

- GitHub organizations, GitHub Apps, and signing in with GitHub: repositories go to
  the token owner's personal account
- Deleting, renaming, or changing the visibility of a repository, or deleting it
  when its service is unregistered
- Pushing changes to a repository that already exists, such as regenerating a
  project after the template changes, or adopting a repository created outside
  DevPlane
- Cleaning up or retrying automatically after a failed push. The developer deletes
  the empty repository on GitHub and provisions again.
- Branch protection, collaborators, webhooks, and other repository settings
- Provisioning in the background with a job status: the request waits for the push,
  which takes a few seconds
- Git hosts other than GitHub
- The web UI (a later Phase 1 change), and the Dockerfile and CI pipeline that
  Phase 2 adds to generated projects

## Capabilities

### New Capabilities

- `repository-provisioning`: Giving a registered service's code a permanent home:
  creating its GitHub repository from a template, pushing the generated project,
  and how DevPlane behaves when GitHub isn't configured or fails.

### Modified Capabilities

- `service-catalog`: "View a service" adds the `repository` field, which is `null`
  until a repository is provisioned. List, register, and update responses use the
  same service representation, so they show it too.

## Impact

- **Code:**
  - A new `provisioning` package holds the endpoint, a small GitHub REST API
    client, and the Git push.
  - The catalog stores a service's repository and returns it in `RegisteredService`
    and service responses. Other packages still can't change services except
    through `ServiceCatalog`.
  - The `templates` package gains a public `@ListedTemplate` validation constraint,
    so the new package can check a template name.
- **Database:** migration `V2` adds nullable repository columns to `services`.
  Existing services read back with no repository.
- **API:**
  - New endpoint: `POST /api/v1/services/{name}/repository`.
  - Services in all existing responses gain a `repository` field. This is
    additive: no existing field changes.
- **Dependencies:** JGit, the Eclipse Foundation's pure-Java Git library, for the
  commit and push. The GitHub REST API is called with Spring's `RestClient`, which
  DevPlane already has.
- **Configuration:**
  - `DEVPLANE_GITHUB_TOKEN`, which is optional.
  - `devplane.github.api-url`, which defaults to `https://api.github.com`. Tests
    point it at a local fake.
- **Tests:**
  - CI needs no token. Integration tests run against a fake GitHub API served from
    the test JVM, which pushes to local bare Git repositories.
  - One manual task provisions a real repository with the developer's token.
- **Docs:** README.md (setting up a token and provisioning a repository) and
  docs/architecture.md (the provisioning flow and the new columns).
