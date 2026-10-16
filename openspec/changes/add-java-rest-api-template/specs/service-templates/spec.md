# Service Templates

## Purpose

Golden-path templates let DevPlane generate a new service that already builds,
runs, and follows the platform's conventions, so teams start from working code
instead of an empty repository.

## ADDED Requirements

### Requirement: Generate a project from a template
The system SHALL generate a project from a named template into an empty directory, given a service name, an owner, and an optional description. Service names and owners follow the catalog's identifier rules.

#### Scenario: Generates a java-rest-api project
- **WHEN** DevPlane generates the `java-rest-api` template for service `orders-api`, owned by `payments-team`, into an empty directory
- **THEN** the directory contains a Gradle project named `orders-api`, with its Gradle wrapper and a `.gitignore`
- **AND** no generated file contains an unfilled `{{` placeholder

#### Scenario: Generates the same files for the same inputs
- **WHEN** DevPlane generates the same template twice with the same service name, owner, and description
- **THEN** both directories contain identical files

#### Scenario: Refuses a non-empty target directory
- **GIVEN** the target directory already contains a file
- **WHEN** DevPlane generates a project into it
- **THEN** generation fails
- **AND** the directory is unchanged

#### Scenario: Rejects an unknown template
- **WHEN** DevPlane generates a template named `cobol-batch`
- **THEN** generation fails and nothing is written

#### Scenario: Rejects an invalid service name
- **WHEN** DevPlane generates a project for service name `Orders API`
- **THEN** generation fails and nothing is written

### Requirement: Generated names follow the service name
The system SHALL derive a generated project's names from its service name: the Gradle project is the service name, the Java package is the configured base package (`io.github.spollito` by default) plus the service name without hyphens, the application class is the service name in PascalCase plus `Application`, and the database is the service name with hyphens replaced by underscores.

#### Scenario: Derives names from a hyphenated name
- **WHEN** DevPlane generates a project for `orders-api`
- **THEN** the Java package is `io.github.spollito.ordersapi`, the application class is `OrdersApiApplication`, and the database is `orders_api`

#### Scenario: Keeps digits in names
- **WHEN** DevPlane generates a project for `orders-api-2`
- **THEN** the Java package is `io.github.spollito.ordersapi2`, the application class is `OrdersApi2Application`, and the database is `orders_api_2`

#### Scenario: Avoids Java keywords in the package
- **WHEN** DevPlane generates a project for `switch`
- **THEN** the Java package is `io.github.spollito.switchservice`, because `switch` is a Java keyword

### Requirement: Generated projects build and pass their tests
The system SHALL generate projects whose `./gradlew build` succeeds, running all of the project's own tests, on a machine with JDK 21 and a running Docker runtime.

#### Scenario: Builds a freshly generated project
- **GIVEN** DevPlane has generated the `java-rest-api` template for `orders-api`
- **WHEN** `./gradlew build` runs in the generated project
- **THEN** the build succeeds
- **AND** the project's tests for the example resource, health check, and API documentation ran and passed

### Requirement: Generated services include an example resource
The system SHALL include in every `java-rest-api` service an example REST resource at `/api/v1/items` that stores items in PostgreSQL. An item has a numeric `id`, a `name` of 1 to 100 characters, and a `createdAt` timestamp. Errors are RFC 9457 problem details documents.

#### Scenario: Creates an item
- **WHEN** a client sends `POST /api/v1/items` with name `First item`
- **THEN** the response status is 201 Created and the `Location` header points to `/api/v1/items/{id}`
- **AND** the body contains the item's `id`, `name`, and `createdAt`

#### Scenario: Lists items
- **GIVEN** items named `First item` and `Second item` were created in that order
- **WHEN** a client sends `GET /api/v1/items`
- **THEN** the response status is 200 OK and the body is a JSON array listing both items in creation order

#### Scenario: Returns 404 for an unknown item
- **WHEN** a client sends `GET /api/v1/items/{id}` for an id that does not exist
- **THEN** the response status is 404 Not Found and the body is a problem details document

#### Scenario: Rejects an item without a name
- **WHEN** a client sends `POST /api/v1/items` without a `name`
- **THEN** the response status is 400 Bad Request and the problem details `errors` list identifies the `name` field

### Requirement: Generated services report health and publish API docs
The system SHALL generate services that report their health at `GET /actuator/health`, including whether they can reach their database, and publish an OpenAPI description of their endpoints at `GET /v3/api-docs`.

#### Scenario: Reports healthy
- **GIVEN** the generated service can reach its database
- **WHEN** a client sends `GET /actuator/health`
- **THEN** the response status is 200 OK and `status` is `UP`

#### Scenario: Reports an unreachable database
- **GIVEN** the generated service cannot reach its database
- **WHEN** a client sends `GET /actuator/health`
- **THEN** the response status is 503 Service Unavailable and `status` is `DOWN`

#### Scenario: Serves the API description
- **WHEN** a client sends `GET /v3/api-docs`
- **THEN** the response status is 200 OK and the document describes the `/api/v1/items` endpoints

### Requirement: Generated README describes the service
The system SHALL generate a `README.md` that names the service, its owner, and its description, and explains how to run and test the service.

#### Scenario: README identifies the service
- **WHEN** DevPlane generates a project for `orders-api`, owned by `payments-team`, with description `Order management REST API`
- **THEN** the README names `orders-api`, `payments-team`, and `Order management REST API`
- **AND** it shows the commands to run and test the service

#### Scenario: README without a description
- **WHEN** DevPlane generates a project without a description
- **THEN** the README says that no description was provided
