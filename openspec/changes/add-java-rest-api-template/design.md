# Design: Add the Java REST API template

## Context

See proposal.md for why. DevPlane is one Spring Boot application organized by
feature (`api/`, `catalog/`), with Flyway, Testcontainers-based integration tests,
and CI that runs `./gradlew build`. This change adds a third feature package,
`templates/`, and the first template. Nothing calls the generator through the API
yet; the next change does.

Two facts shape the design. Gradle's default resource excludes silently drop
`.gitignore` and `.gitattributes` (checked against this build), and classpath
resources lose file permissions, so `gradlew` arrives without its executable bit.

## Goals / Non-Goals

**Goals:**

- A generated project is a complete, standalone repository: it builds, runs, and
  passes its own tests with no DevPlane code on its classpath.
- Generation is all or nothing: on any failure, nothing is written.
- DevPlane's build proves that a freshly generated project works.

**Non-Goals:**

- A general-purpose template engine (loops, conditionals, partials).
- Sharing code between DevPlane and generated services through a library.

## Decisions

### 1. Templates are classpath resources

The template lives in `src/main/resources/service-templates/java-rest-api/` and is
read through Spring's `PathMatchingResourcePatternResolver`, which lists files the
same way from the filesystem (tests, `bootRun`) and from the packaged jar.

- A top-level `templates/` folder was rejected: it isn't packaged, so a deployed
  DevPlane couldn't read it.
- A separate Git repository per template was rejected: a template change and the
  test that proves it would then live in different repositories.
- `src/main/resources/templates/` was avoided because Spring Boot uses that folder
  for web view templates.

### 2. Minimal placeholder substitution, no template engine

File contents use `{{variable}}` and file paths use `__variable__`. Paths avoid
braces because resource URLs and Ant-style patterns treat them specially. The
generator replaces known variables and fails if any `{{` or `__name__` token is
left, so a typo in the template can never ship.

Mustache was rejected because its sections and loops aren't needed, and it would
be DevPlane's only use of it. FreeMarker and Thymeleaf were rejected for the same
reason, and because their `${...}` syntax collides with Gradle and Spring property
syntax inside the template.

Variables: `serviceName`, `owner`, `description` ("No description provided." when
absent), `basePackage`, `packageName`, `packagePath`, `className`, `databaseName`,
and `springBootVersion`.

### 3. Generation renders everything in memory, then writes

The generator validates the inputs, resolves the template, renders every file,
and checks for leftover placeholders before touching the target directory, which
must be missing or empty. Failures throw one `ProjectGenerationException` naming
the reason; the next change maps it to HTTP errors. A template is about 30 small
files, so holding them in memory is cheap. Rendering and writing are deterministic
(files sorted by path), which makes the "same inputs, same files" scenario
testable.

File handling rules:

- Files whose names start with `_` are dotfiles: `_gitignore` becomes
  `.gitignore`. Storing them undotted also keeps the template's ignore rules from
  applying to DevPlane's own repository.
- `.jar` files are copied byte for byte, without substitution.
- `gradlew` is made executable after it is written.

### 4. Names derived in one place

A `ProjectNames` record derives every name from the service name, per the spec. A
package segment that `javax.lang.model.SourceVersion.isKeyword` reports as
reserved gets `service` appended (`switch` becomes `switchservice`). The base
package comes from the `devplane.templates.base-package` property, defaulting to
`io.github.spollito`.

Inputs are validated with the catalog's identifier rule, so `CatalogConstraints`
becomes public instead of the rule being copied.

### 5. What a generated service contains

The template mirrors DevPlane's own conventions, copied into the template rather
than shared through a library, so each service owns and can change its code:

- Gradle (Kotlin DSL) and the same Gradle wrapper version as DevPlane. The Spring
  Boot version is filled in from `SpringBootVersion.getVersion()`, so new services
  start on the version DevPlane runs. springdoc's version is pinned in the
  template.
- `compose.yaml` with PostgreSQL 18, Flyway, `ddl-auto=validate`, and
  `open-in-view=false`.
- An `Item` example resource (entity, repository, controller, request and response
  records). It is named `Item`, not `Example`, to avoid clashing with Spring Data's
  `Example` class.
- An `ApiExceptionHandler` that renders problem details with `type` and an
  `errors` array, as DevPlane's does.
- Tests named after this change's scenarios, against Testcontainers PostgreSQL.

Unlike DevPlane, generated services keep Spring Boot's default binding to all
interfaces, because Phase 2 runs them in containers.

### 6. Proving generated projects work

- Generator unit tests use a small test-only template in `src/test/resources`, so
  substitution, paths, dotfiles, binary files, permissions, and failures are tested
  quickly and independently of the real template.
- `GeneratedProjectBuildTests` generates `orders-api` into
  `build/generated-projects/orders-api`, runs that project's `./gradlew build`
  in a subprocess with a timeout, and fails with the subprocess output if it
  fails. It then reads the generated project's JUnit reports and checks that the
  scenario-named tests ran and passed, so a test can't silently disappear from the
  template. The generated project stays in `build/` for a developer to open.
- That test is tagged `template` and runs in a separate `templateTest` Gradle task
  that `check` depends on. `./gradlew build` (and CI) run it, while `./gradlew
  test` stays fast. The nested build uses the same Gradle version and dependency
  cache, so after the first run it mostly costs the generated project's own tests.

### Later roadmap items this must not block

- **Service creation workflow:** the generator takes plain values (service name,
  owner, description, target directory), not HTTP types, so an endpoint can call
  it for a catalog entry.
- **Repository provisioning:** a generated directory is ready to commit:
  `.gitignore`, `.gitattributes`, and an executable `gradlew`.
- **Containerization and CI/CD (Phase 2):** both become more template files, and
  services already bind to all interfaces.
- **Metrics and logs (Phase 3):** more template dependencies and configuration.

## Risks / Trade-offs

- [The nested build adds one to two minutes to `./gradlew build`] → It's isolated
  in `templateTest`, and `./gradlew test` doesn't run it.
- [Template Java files aren't compiled by DevPlane, so mistakes surface only in
  the nested build] → That's what `templateTest` is for, and keeping the template
  small keeps those failures easy to read.
- [The template drifts from DevPlane's conventions or library versions] → Spring
  Boot's version is injected; other versions are pinned and covered by the
  nested build.
- [Generation is tested from the filesystem, not from the packaged jar] → The
  resource resolver handles both; the next change's manual check runs the
  packaged application.
- [A disk error midway through writing leaves partial output] → Rendering happens
  first, so only I/O failures can do this, and the error names the directory.
