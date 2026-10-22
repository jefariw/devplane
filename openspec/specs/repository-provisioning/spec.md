# repository-provisioning Specification

## Purpose
Repository provisioning gives a registered service's code a permanent home: it
creates the service's GitHub repository, pushes the project generated from a
template, and records the repository in the catalog.

## Requirements

### Requirement: Provision a service's repository
The system SHALL provision a registered service's repository with `POST /api/v1/services/{name}/repository`. It creates a GitHub repository named after the service, with the service's description, in the account that owns the configured token. It pushes the project generated from the service's current details as a single commit on `main`, records the repository on the service, and responds 201 Created with the repository's `url`, `template`, and `createdAt`.

#### Scenario: Provisions a repository
- **GIVEN** `orders-api` is registered with owner `payments-team` and description `Order management REST API`
- **AND** the configured GitHub token belongs to the GitHub user `octo-dev`
- **WHEN** a client sends `POST /api/v1/services/orders-api/repository` without a body
- **THEN** the response status is 201 Created, `url` is `https://github.com/octo-dev/orders-api`, `template` is `java-rest-api`, and `createdAt` is set
- **AND** the `Location` header equals `url`
- **AND** GitHub has a new repository `orders-api` owned by `octo-dev`, with description `Order management REST API`

#### Scenario: Pushes the generated project as one commit
- **GIVEN** `orders-api` is registered with owner `payments-team` and description `Order management REST API`
- **WHEN** a client provisions its repository
- **THEN** the repository's `main` branch has exactly one commit, with the message `Create orders-api from the java-rest-api template`, attributed to the token owner's GitHub account
- **AND** the commit holds exactly the files DevPlane generates from the `java-rest-api` template for `orders-api`, owned by `payments-team`, with description `Order management REST API`
- **AND** `gradlew` has the Git file mode of an executable file

#### Scenario: Records the repository on the service
- **GIVEN** `orders-api` is registered
- **WHEN** a client provisions its repository
- **THEN** `GET /api/v1/services/orders-api` shows a `repository` with the same `url`, `template`, and `createdAt` as the provisioning response
- **AND** the service's `updatedAt` equals the repository's `createdAt`

#### Scenario: Returns 404 for an unknown service
- **WHEN** a client provisions the repository of a service name that is not registered
- **THEN** the response status is 404 Not Found and the body is a problem details document
- **AND** nothing is created on GitHub

### Requirement: Choose the template and visibility
The system SHALL accept an optional JSON request body with two optional fields: `template`, one of the templates `GET /api/v1/templates` lists and `java-rest-api` by default, and `visibility`, either `private` (the default) or `public`. Invalid fields are rejected with 400 Bad Request and an `errors` entry for each, and nothing is created on GitHub.

#### Scenario: Creates a private repository by default
- **GIVEN** `orders-api` is registered
- **WHEN** a client sends `POST /api/v1/services/orders-api/repository` with the body `{}`
- **THEN** the response status is 201 Created and `template` is `java-rest-api`
- **AND** the GitHub repository is private

#### Scenario: Creates a public repository on request
- **GIVEN** `orders-api` is registered
- **WHEN** a client provisions its repository with the body `{"template": "java-rest-api", "visibility": "public"}`
- **THEN** the response status is 201 Created
- **AND** the GitHub repository is public

#### Scenario: Rejects an unknown template and visibility
- **GIVEN** `orders-api` is registered
- **WHEN** a client provisions its repository with the body `{"template": "cobol-batch", "visibility": "internal"}`
- **THEN** the response status is 400 Bad Request and the `errors` list identifies both the `template` and `visibility` fields
- **AND** nothing is created on GitHub, and the service's `repository` is still `null`

### Requirement: Provision a repository only once
The system SHALL respond 409 Conflict, record nothing, and leave existing repositories untouched when the service already has a repository, or when the token owner's account already has a repository with the service's name.

#### Scenario: Rejects a service that already has a repository
- **GIVEN** `orders-api` has a repository
- **WHEN** a client provisions its repository again
- **THEN** the response status is 409 Conflict and the body is a problem details document
- **AND** nothing is sent to GitHub, and the service's `repository` is unchanged

#### Scenario: Rejects a name already taken on GitHub
- **GIVEN** `orders-api` is registered without a repository
- **AND** the token owner's account already has a repository named `orders-api`
- **WHEN** a client provisions the service's repository
- **THEN** the response status is 409 Conflict and the detail says the account already has a repository with that name
- **AND** nothing is pushed to the existing repository, and the service's `repository` is still `null`

### Requirement: Provisioning needs a GitHub token
The system SHALL start and serve every other endpoint without a GitHub token. When no token is configured, provisioning SHALL respond 503 Service Unavailable with a problem details document that says to set `DEVPLANE_GITHUB_TOKEN`, and record nothing.

#### Scenario: Reports a missing token
- **GIVEN** DevPlane runs without a GitHub token
- **AND** `orders-api` is registered
- **WHEN** a client provisions its repository
- **THEN** the response status is 503 Service Unavailable and the detail mentions `DEVPLANE_GITHUB_TOKEN`
- **AND** `GET /api/v1/services/orders-api` still returns the service, with `repository` `null`

### Requirement: Report GitHub failures
The system SHALL respond 502 Bad Gateway with a problem details document, and record nothing, when GitHub rejects the token, fails, or can't be reached, or when pushing the project fails. When the push fails after the repository was created, the detail SHALL name that repository, so the developer can delete it before trying again.

#### Scenario: GitHub rejects the token
- **GIVEN** `orders-api` is registered
- **AND** GitHub rejects the configured token
- **WHEN** a client provisions its repository
- **THEN** the response status is 502 Bad Gateway and the detail says GitHub rejected the token
- **AND** the service's `repository` is still `null`

#### Scenario: GitHub fails
- **GIVEN** `orders-api` is registered
- **AND** GitHub responds with a server error
- **WHEN** a client provisions its repository
- **THEN** the response status is 502 Bad Gateway
- **AND** the service's `repository` is still `null`

#### Scenario: The push fails
- **GIVEN** `orders-api` is registered
- **AND** GitHub creates the repository, but pushing to it fails
- **WHEN** a client provisions the service's repository
- **THEN** the response status is 502 Bad Gateway and the detail names the repository's URL
- **AND** the service's `repository` is still `null`

### Requirement: The token stays secret
The system SHALL keep the GitHub token out of every API response, its log output, and the pushed repository's files and commit.

#### Scenario: The token appears nowhere
- **GIVEN** the configured GitHub token is `test-token-5f2c`
- **WHEN** a client provisions a repository successfully, and then provisions another while GitHub rejects the token
- **THEN** neither response, nor DevPlane's log output, contains `test-token-5f2c`
- **AND** neither the pushed commit's files nor its message, author, or committer contain `test-token-5f2c`

### Requirement: Unregistering keeps the repository
The system SHALL leave a service's GitHub repository untouched when the service is unregistered from the catalog.

#### Scenario: Unregisters a service that has a repository
- **GIVEN** `orders-api` has a repository
- **WHEN** a client sends `DELETE /api/v1/services/orders-api`
- **THEN** the response status is 204 No Content
- **AND** DevPlane sends no request to GitHub, and the repository still exists
