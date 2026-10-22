# Tasks

## 1. Catalog lookup

- [x] 1.1 Make `ServiceCatalog` public and add `lookUp(name)`, which returns a new public `RegisteredService` record (`name`, `owner`, `description`) and throws `ServiceNotFoundException` for an unknown name, keeping its other methods, `ServiceEntity`, and the repository package-private; verify with an integration test that looks up a registered service and an unknown one, and describe the lookup in docs/architecture.md

## 2. Template list

- [x] 2.1 Add the `ServiceTemplate` record and the `ServiceTemplates` component, listing `java-rest-api` with its description; verify with a unit test that generates every listed template into a temporary directory
- [x] 2.2 Add `TemplateController` with `GET /api/v1/templates`; verify the "Lists the java-rest-api template" scenario with an integration test, check that `/v3/api-docs` documents the endpoint, and add the endpoint to docs/architecture.md

## 3. Project archive

- [x] 3.1 Add `ProjectArchiver`, which opens a zip file system on a temporary file with POSIX attributes enabled and default permissions `rw-r--r--`, generates the project into a `<service-name>/` folder inside it with the existing `ProjectGenerator`, and returns the archive's bytes, deleting its temporary files; verify with unit tests that the archive holds the same paths and bytes as generating into a directory, that `gradlew` is executable in the archive, and that no temporary files are left behind, including when generation fails; describe the archiver in docs/architecture.md

## 4. Download endpoint

- [x] 4.1 Add `ProjectController` with `GET /api/v1/services/{name}/project` and the optional `template` parameter (default `java-rest-api`), returning `application/zip` as a `<name>.zip` attachment; verify the "Downloads a generated project", "Keeps the Gradle wrapper executable", "Uses the service's current details", and "Accepts a listed template" scenarios with integration tests
- [x] 4.2 Add `UnknownTemplateException` (400, with an `errors` entry for `template`), checked before the service lookup; verify the "Rejects an unknown template" and "Returns 404 for an unknown service" scenarios with integration tests, including a 404 request whose `Accept` header allows only `application/zip`, and check that the test-only `test-template` is rejected too
- [x] 4.3 Check that `/v3/api-docs` documents the project endpoint with an `application/zip` response, add the endpoint to docs/architecture.md, and add a "Creating a service" section to README.md that registers a service and downloads its project with curl; verify the README's commands work as written against `./gradlew bootRun`

## 5. Integration checks

- [x] 5.1 Run DevPlane with `./gradlew bootRun`, register `orders-api`, download its project with curl, unzip it, and run the unzipped project's own build (`gradlew.bat build` on Windows); also download it from `/swagger-ui.html` in a browser, and confirm each response matches the specs
- [x] 5.2 Run `./gradlew build` in DevPlane, confirm CI passes, then tick "Service creation workflow" in the README roadmap

## Workflow follow-up

- Run `/opsx:verify add-service-creation-workflow`, then `/opsx:archive add-service-creation-workflow` to merge this change's specs into `openspec/specs/`.
