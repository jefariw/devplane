# Tasks

## 1. Catalog records a repository

- [x] 1.1 Add migration `V2__add_service_repository.sql` (three nullable `repository_*` columns with an all-or-none check constraint), the public `SourceRepository` record, the matching `ServiceEntity` fields, a nullable `repository` on `RegisteredService`, and the public `ServiceCatalog.recordRepository(name, url, template)`; verify with integration tests that recording returns the repository and sets `updatedAt` to its `createdAt`, that `lookUp` returns it, that an unknown service throws `ServiceNotFoundException`, that recording twice throws `IllegalStateException`, and that the database rejects a row with only some repository columns set; add the columns to docs/architecture.md
- [x] 1.2 Add `repository` to `ServiceResponse`; verify the service-catalog "Returns a registered service" (`repository` is `null`) and "Shows a provisioned repository" scenarios for both `GET /api/v1/services/{name}` and the list, update the existing catalog tests' expected JSON, and check that `/v3/api-docs` documents the field

## 2. Template validation

- [x] 2.1 Make `ServiceTemplates` and its `DEFAULT` constant public, and add the `@ListedTemplate` constraint and its validator to the `templates` package; verify with tests that `java-rest-api` and `null` are valid, and that `cobol-batch` and the test-only `test-template` are invalid

## 3. GitHub client

- [x] 3.1 Add `GitHubProperties` (`@ConfigurationProperties("devplane.github")` with `token` and `api-url`, and a `toString()` that leaves the token out) and the `api-url` default in application.properties, plus the test-only `FakeGitHub` server (JDK `HttpServer`) that records requests and creates bare repositories; verify that the `DEVPLANE_GITHUB_TOKEN` environment variable name binds to `token` (through Spring's relaxed binding) and that `toString()` doesn't contain the token
- [x] 3.2 Add `GitHubClient.createRepository(name, description, private)` with `RestClient`, GitHub's headers, and connect and read timeouts, plus `RepositoryNameTakenException` (409) and `GitHubException` (502); verify against `FakeGitHub` the request's path, headers, and body for private and public repositories, that a 422 "name already exists" becomes `RepositoryNameTakenException`, that 401, 500, and a refused connection become `GitHubException` with DevPlane's own detail, and that the log names GitHub's status and request id but not the token

## 4. Git push

- [x] 4.1 Add the pinned JGit dependency (`org.eclipse.jgit:org.eclipse.jgit:7.8.0.202609011348-r`) and `ProjectPusher`, which initializes `main`, stages every file with `gradlew` staged as `100755`, commits as the token owner's no-reply address with `core.autocrlf=false`, no signing, and no hooks, pushes with the credentials provider, and checks each ref update; verify by pushing a generated project to a local bare repository and cloning it back, checking one commit with the expected message and author, files identical to the generated directory, `gradlew` mode `100755` even when the working file isn't executable, and an exception when the push is rejected or the remote doesn't exist

## 5. Provisioning endpoint

- [x] 5.1 Add `RepositoryProvisioner` and `RepositoryController` with `POST /api/v1/services/{name}/repository` and the optional `ProvisionRepositoryRequest`, and register `FakeGitHub` and its properties in the shared integration test configuration; verify the "Provisions a repository", "Pushes the generated project as one commit", "Records the repository on the service", "Returns 404 for an unknown service", "Creates a private repository by default", and "Creates a public repository on request" scenarios with integration tests, and check that no `devplane-` temporary directory is left behind
- [x] 5.2 Add `RepositoryAlreadyProvisionedException` (409, checked before contacting GitHub), and wire the request's `@ListedTemplate` and visibility checks; verify the "Rejects an unknown template and visibility", "Rejects a service that already has a repository", "Rejects a name already taken on GitHub", and "Unregisters a service that has a repository" scenarios, checking the fake's request log for "nothing sent to GitHub"
- [x] 5.3 Map push failures to a 502 whose detail names the created repository; verify the "GitHub rejects the token", "GitHub fails", and "The push fails" scenarios, including that the service's `repository` stays `null` and the temporary directory is deleted
- [x] 5.4 Add `GitHubNotConfiguredException` (503); verify the "Reports a missing token" scenario in a test class whose context has no token, and the "The token appears nowhere" scenario with `OutputCaptureExtension`, checking both responses, the log output, and the pushed commit's files and metadata
- [x] 5.5 Check that `/v3/api-docs` documents the endpoint with its optional request body and 201 response; describe the `provisioning` package and the provisioning steps in docs/architecture.md; and add a "Push it to GitHub" part to the README's "Creating a Service" section, covering the fine-grained token (all repositories, Administration and Contents read and write, a short expiry), `DEVPLANE_GITHUB_TOKEN`, and the curl command

## 6. Integration checks

- [ ] 6.1 With a real fine-grained token, follow the README: run `./gradlew bootRun`, register `orders-api`, provision its repository with curl, and confirm on GitHub that it's private, has the service's description, and has one commit attributed to you; clone it and run its `./gradlew build`; provision a second service with `"visibility": "public"`; confirm `GET /api/v1/services/orders-api` shows the repository; then delete the test repositories on GitHub
- [ ] 6.2 Run `./gradlew build` in DevPlane, confirm CI passes, then tick "Repository provisioning" in the README roadmap

## Workflow follow-up

- Run `/opsx:verify add-repository-provisioning`, then `/opsx:archive add-repository-provisioning` to merge this change's specs into `openspec/specs/`.
