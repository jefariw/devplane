# Tasks

## 1. Generator

- [ ] 1.1 Make `CatalogConstraints` public, and add the `templates` package with `NewProject`, `ProjectGenerationException`, and `ProjectNames`; verify the "Derives names from a hyphenated name", "Keeps digits in names", and "Avoids Java keywords in the package" scenarios with parameterized unit tests
- [ ] 1.2 Add `ProjectGenerator`: resolve a template from the classpath, fill `{{…}}` and `__…__` placeholders, restore `_`-prefixed dotfiles, copy `.jar` files unchanged, make `gradlew` executable, and fail on leftover placeholders, rendering everything before writing; verify with a test-only template in `src/test/resources/service-templates/` and tests for "Generates the same files for the same inputs", "Refuses a non-empty target directory", "Rejects an unknown template", and "Rejects an invalid service name", plus the dotfile, binary, permission, and leftover-placeholder rules
- [ ] 1.3 Add the `devplane.templates.base-package` property (default `io.github.spollito`) and fill `springBootVersion` from `SpringBootVersion`; verify with a test that generates the test-only template under a custom base package

## 2. Template skeleton and build test

- [ ] 2.1 Add the `java-rest-api` skeleton: settings and build files, the Gradle wrapper copied from DevPlane, `_gitignore`, `_gitattributes`, `compose.yaml`, `application.properties`, the application class, Testcontainers configuration, and a context test; verify the "Generates a java-rest-api project" scenario with a generator test
- [ ] 2.2 Add `GeneratedProjectBuildTests` (tagged `template`), which generates `orders-api` into `build/generated-projects/orders-api`, runs its `./gradlew build` with a timeout, and checks the generated project's JUnit reports for an expected list of passing tests; add a `templateTest` task that `check` depends on, and exclude the tag from `test`; verify the "Builds a freshly generated project" scenario with `./gradlew templateTest`, and that `./gradlew test` skips it
- [ ] 2.3 Document `./gradlew templateTest` and where generated projects appear in README.md and CLAUDE.md, describe the `templates` package and template layout in docs/architecture.md, and verify the documented commands work as written

## 3. Example resource

- [ ] 3.1 Add the items Flyway migration, entity, repository, `POST /api/v1/items`, `GET /api/v1/items/{id}`, and the problem-details exception handler to the template, with template tests for "Creates an item" and "Returns 404 for an unknown item" added to the build test's expected list; verify with `./gradlew templateTest`
- [ ] 3.2 Add `GET /api/v1/items` and name validation with the `errors` array, with template tests for "Lists items" and "Rejects an item without a name" added to the expected list; verify with `./gradlew templateTest`

## 4. Health, API docs, and README

- [ ] 4.1 Add Actuator health and springdoc to the template, with template tests for "Reports healthy", "Reports an unreachable database", and "Serves the API description" added to the expected list; verify with `./gradlew templateTest`
- [ ] 4.2 Add the README template, and verify the "README identifies the service" and "README without a description" scenarios with generator tests

## 5. Integration checks

- [ ] 5.1 Run the generated `orders-api` from `build/generated-projects/orders-api` with `./gradlew bootRun`, create, list, and view items, check `/actuator/health` and `/v3/api-docs` with curl, and confirm each response matches the specs
- [ ] 5.2 Run `./gradlew build` in DevPlane, confirm CI passes, then tick "Golden-path service template" in the README roadmap

## Workflow follow-up

- Run `/opsx:verify add-java-rest-api-template`, then `/opsx:archive add-java-rest-api-template` to merge this change's specs into `openspec/specs/`.
