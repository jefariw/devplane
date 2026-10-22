# Design: Add repository provisioning

## Context

See proposal.md for why, and the specs for the behavior. What exists today:

- `ServiceCatalog.lookUp(name)` returns a read-only `RegisteredService` (name,
  owner, and description). Everything else in `catalog`, including `ServiceEntity`
  and the Spring Data `ServiceRepository`, is package-private.
- `ProjectGenerator.generate(template, project, directory)` is public. It writes a
  project into a missing or empty directory and makes `gradlew` executable, as a
  best effort on Windows.
- `ServiceTemplates` lists the templates the API offers. It's package-private, and
  the download endpoint checks its `template` query parameter by hand.
- `ProjectArchiver` works in a `devplane-` temporary directory and deletes it in a
  `finally` block.
- The build passes on Windows, so new code and tests must not assume POSIX file
  permissions.
- Spring's `RestClient` is part of `spring-web`, which DevPlane already has.
  Integration tests share one Spring context through `@IntegrationTest`.

## Goals / Non-Goals

**Goals:**

- Keep package dependencies one-way: `provisioning` uses `catalog` and
  `templates`, and neither of those knows about it.
- Anyone can build DevPlane and run every automated test without a GitHub token.
- Never hold a database transaction open while waiting for GitHub.

**Non-Goals:**

- A general GitHub client. DevPlane calls one GitHub endpoint.
- Handling GitHub's rate limits beyond reporting a 502. One provisioning request
  makes one API call.

## Decisions

### 1. A `provisioning` package

The new package holds:

- `RepositoryController`: the endpoint and its request record
- `RepositoryProvisioner`: runs the steps in order
- `GitHubClient`: the REST call
- `ProjectPusher`: the Git commit and push
- the package's exceptions

The package is named for the activity because "repository" already means a Spring
Data repository in this codebase (`ServiceRepository`). For the same reason, the
catalog's record of a GitHub repository is called `SourceRepository`.

Alternatives:

- Putting this in `templates` was rejected. That package generates files, and
  adding GitHub calls and catalog writes would turn it into a grab bag.
- Putting this in `catalog` was rejected. `catalog` would then depend on
  `templates`, which already depends on `catalog`.

### 2. The catalog stores the repository in three nullable columns

Migration `V2__add_service_repository.sql` adds three columns to `services`:
`repository_url varchar(255)`, `repository_template varchar(63)`, and
`repository_created_at timestamptz`. A check constraint requires all three to be
null or all three to be set.

- `ServiceCatalog` gains a public `recordRepository(name, url, template)` method:
  - It sets the three columns and `updatedAt` to the same time, and returns a
    public `SourceRepository(url, template, createdAt)` record.
  - It throws `ServiceNotFoundException` for an unknown service.
  - It throws `IllegalStateException` if the service already has a repository. That
    can only happen when two requests race, and GitHub rejects the second one's
    repository name first.
- `RegisteredService` gains a nullable `repository`, so the provisioner can return
  the 409 before contacting GitHub.
- `ServiceResponse` gains `repository` and serializes `SourceRepository` directly.
  The provisioning response is the same record, so the spec's "same `url`,
  `template`, and `createdAt`" holds by construction.
- The catalog stores the template name without checking it, so `catalog` still
  doesn't depend on `templates`.

Alternatives:

- A separate `service_repositories` table was rejected for now. A service has at
  most one repository, so the table would mean a join and a second entity for three
  values. Splitting it out later is a plain migration.
- A table owned by the `provisioning` package was rejected. Every service response
  must show the repository, so the catalog would have to read another package's
  table.
- Storing the visibility was rejected. It can be changed on GitHub at any time, and
  DevPlane would then show a stale value.

### 3. Validating the request

`ProvisionRepositoryRequest(template, visibility)` is bound with
`@RequestBody(required = false)`. A missing body and `{}` both mean the defaults.

- `visibility` is checked with `@Pattern(regexp = "private|public")`, the same way
  `lifecycle` is checked.
- `template` is checked by a new `@ListedTemplate` constraint in the `templates`
  package. Spring creates constraint validators as beans, so the validator can ask
  `ServiceTemplates`, which becomes public, as does its `DEFAULT` constant.
  Because both fields are checked by bean validation, an invalid template and an
  invalid visibility are reported together in one `errors` array, as the
  platform-api "Validation errors list every invalid field" requirement says.
- Checking the template by hand in the controller, as the download endpoint does
  with its query parameter, was rejected. The client would see only one of two
  invalid fields at a time.

### 4. The order of steps

`RepositoryProvisioner.provision(name, template, visibility)` isn't transactional.
Each catalog call runs in its own transaction, so none is open during GitHub calls.
The steps, in order:

1. `catalog.lookUp(name)` returns 404 for an unknown service, and 409 when the
   service already has a repository.
2. If no token is configured, return 503.
3. Generate the project into a new `devplane-` temporary directory. A failure here
   is a DevPlane bug, so it becomes a 500 and nothing is created on GitHub.
4. `GitHubClient.createRepository` creates the repository.
5. `ProjectPusher.push` commits and pushes the project.
6. `catalog.recordRepository` stores the repository, and the response is 201.

A `finally` block deletes the temporary directory, as in `ProjectArchiver`.
Generating before creating the repository means a broken template can never
leave an empty repository behind.

### 5. GitHub's REST API, called with `RestClient`

`GitHubClient.createRepository(name, description, private)` sends
`POST {api-url}/user/repos` with the following:

- body: `name`, `description`, `private`, and `auto_init: false`
- headers: `Authorization: Bearer <token>`,
  `Accept: application/vnd.github+json`, and `X-GitHub-Api-Version: 2022-11-28`

From GitHub's response it reads `html_url`, `clone_url`, `owner.login`, and
`owner.id`. Responses map to errors as follows:

| GitHub's response | DevPlane's response |
| --- | --- |
| 422 saying the name already exists | 409 |
| 401 | 502 "GitHub rejected the token" |
| any other error, a timeout, or a connection failure | 502 |

The client sets a 10-second connect timeout and a 30-second read timeout.

- The token comes from `@ConfigurationProperties("devplane.github")`:
  - Spring maps the `DEVPLANE_GITHUB_TOKEN` environment variable to `token` without
    extra code.
  - `api-url` defaults to `https://api.github.com` in `application.properties`.
  - The properties record overrides `toString()` so the token is never printed.
  - The record strips whitespace around the token, because copying a token often
    adds a line break. The JDK's HTTP client rejects a header value that still
    can't be sent, and its error message quotes the value. The client therefore
    turns that error into a 502 without logging it or keeping it as the cause.
- The client builds its own `RestClient` from `RestClient.builder()`, because only
  the `spring-web` classes are needed.
- The GitHub API library `org.kohsuke:github-api` was rejected: a large dependency
  with its own HTTP stack, for one call.
- The `gh` command-line tool was rejected: every developer would have to install it
  and sign in, and DevPlane would have to parse its output.

### 6. Committing and pushing with JGit

`ProjectPusher` takes the generated directory and the repository's details, then:

1. Runs `git init` with the initial branch `main` and stages every file. It then
   sets `gradlew`'s staged mode to executable (`100755`) explicitly, so the mode is
   right even on Windows, where the file system has no executable bit.
2. Commits with the message `Create <name> from the <template> template`. Author
   and committer are the token's owner, at GitHub's no-reply address
   `<id>+<login>@users.noreply.github.com`. GitHub links that address to the
   account, and DevPlane doesn't need permission to read the owner's email.
3. Pushes `main` to `clone_url`, with the token supplied by JGit's credentials
   provider (user `x-access-token`) and a 60-second timeout. The token is never
   put in the URL or the repository's configuration.
4. Checks every remote ref update in the push result. JGit reports a rejected
   push in the result rather than throwing, so a rejection must be checked for
   explicitly.

The developer's Git configuration must not change the commit. The repository is
configured with `core.autocrlf=false`, and the commit is made unsigned
(`setSign(false)`) and without hooks (`setNoVerify(true)`). Otherwise
`commit.gpgsign` or `core.hooksPath` from `~/.gitconfig` could fail the push or
alter it.

**Dependency:** `org.eclipse.jgit:org.eclipse.jgit:7.8.0.202609011348-r`.

- JGit is the Eclipse Foundation's pure-Java Git library, used by Eclipse and
  Gerrit. It needs Java 17 or later and is BSD-licensed (EDL 1.0).
- Spring Boot doesn't manage its version, so `build.gradle.kts` pins it.

Alternatives:

- Running the `git` command with `ProcessBuilder` was rejected:
  - Git would have to be installed on the machine.
  - The token would have to be passed without appearing in process arguments.
  - The developer's configuration would have to be shut out through environment
    variables.
  - DevPlane would have to parse error output instead of reading typed results.
- GitHub's Git Data API was rejected, even though it needs no new dependency:
  - It takes about 35 blob requests plus tree, commit, and ref requests per push.
  - It refuses to write to an empty repository, which needs a workaround.
  - Tests could only check the requests sent to a fake, not a real Git result.

### 7. Errors

The package's exceptions extend `ErrorResponseException`, like the catalog's:

| Exception | Status |
| --- | --- |
| `RepositoryAlreadyProvisionedException` | 409 |
| `RepositoryNameTakenException` | 409 |
| `GitHubNotConfiguredException` | 503 |
| `GitHubException` | 502 |

- DevPlane writes every `detail` itself and never copies GitHub's response body or
  JGit's messages into it.
- Failures are logged at warn level with GitHub's status code and its
  `X-GitHub-Request-Id`, never with request headers.
- 502 means GitHub failed. 500 stays reserved for DevPlane's own bugs.
- Returning 503 was chosen over refusing to start without a token, because CI and
  new contributors run DevPlane without one.

### 8. Testing without GitHub

`FakeGitHub` is a test-only HTTP server on a random port, built with the JDK's
`com.sun.net.httpserver.HttpServer` (the `jdk.httpserver` module, part of every
JDK). When it receives `POST /user/repos`, it:

1. records the request
2. checks the bearer token
3. creates a bare Git repository in a temporary directory
4. answers like GitHub: `html_url` is `https://github.com/octo-dev/<name>` and
   `clone_url` is the bare repository's `file:` URI

A test can make it answer 401, 422, or 500 instead, or return a `clone_url` that
can't be pushed to.

- The fake is registered in the shared integration test configuration, along with
  `devplane.github.api-url` and a test token. Provisioning tests therefore share
  the Spring context with every other integration test. Only the missing-token test
  starts a context of its own.
- Tests clone the bare repository back with JGit and check four things:
  - the files match what generating into a directory produces, as
    `ProjectArchiverTests` already checks for archives
  - there is one commit, with the expected message and author
  - `gradlew`'s mode is executable
  - the token appears nowhere
- The "token appears nowhere" scenario captures log output with Spring Boot's
  `OutputCaptureExtension`.

Alternatives:

- WireMock was rejected. It would be a new dependency, and its stubs can't create
  the bare repository that the push needs.
- `MockRestServiceServer` was rejected. It replaces the `RestClient` itself, so the
  real HTTP request is never tested, and the push would need a separate test seam.
- A Java interface with an in-memory fake was rejected. It would skip the real
  client entirely.

### Later roadmap items this must not block

- **Web UI:** a "Create repository" action that posts to this endpoint, and a link
  to `repository.url` in the service list.
- **CI/CD (Phase 2):** the pipeline is a template file, pushed with everything
  else. Pushing `.github/workflows/` needs the token's "Workflows" permission, so
  that change updates the README's token setup.
- **Deployments (Phase 2):** they find a service's code through `repository.url`
  in the catalog.
- **MCP server (Phase 4):** the endpoint has a JSON body and an OpenAPI
  description. Because it creates something outside DevPlane, Phase 4's
  human-approval step applies to it.

## Risks / Trade-offs

- [A push that fails after the repository was created leaves an empty repository]
  → The 502 detail names the repository so the developer can delete it. The push
  uses the same token right after the creation succeeded, so this should be rare.
- [The catalog's URL goes stale if the repository is renamed or deleted on GitHub]
  → Accepted. GitHub redirects renamed repositories, and DevPlane doesn't watch
  GitHub.
- [Automated tests push over `file:`, so pushing over HTTPS with the token is only
  checked by the manual task] → JGit's HTTPS transport is mature, and the
  credentials setup is a few lines that the manual task exercises against real
  GitHub.
- [Creating repositories needs a fine-grained token with access to all
  repositories, plus Administration and Contents write permissions] → The README
  lists the exact permissions and suggests a short expiry. DevPlane listens only
  on localhost and never prints the token.
- [Two requests for the same service at once] → GitHub's unique repository names
  serialize them. The second request gets a 409 before anything is pushed.
- [A slow or unreachable GitHub holds a request thread] → Connect, read, and push
  timeouts bound the wait. One developer makes these requests one at a time.

## Migration Plan

`V2` only adds nullable columns, so existing services read back with no
repository. To roll back, drop the three columns. Only repository records are lost,
and the repositories themselves stay on GitHub.
