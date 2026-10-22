# Service Creation

## Purpose

Service creation turns a registered catalog entry into code a developer can start
from: it lists the templates DevPlane offers, and delivers the project generated
from one of them for a service.

## ADDED Requirements

### Requirement: List available templates
The system SHALL list the templates that services can be created from at `GET /api/v1/templates`, as a JSON array in which each entry has a template's `name` and a short `description`.

#### Scenario: Lists the java-rest-api template
- **WHEN** a client sends `GET /api/v1/templates`
- **THEN** the response status is 200 OK and the body is a JSON array
- **AND** the array includes an entry whose `name` is `java-rest-api` and whose `description` is not empty

### Requirement: Download a service's project
The system SHALL return the project generated for a registered service from `GET /api/v1/services/{name}/project` as a zip archive, with content type `application/zip` and a `Content-Disposition` attachment named `<name>.zip`. The archive holds the project in a `<name>/` folder, generated from the service's current name, owner, and description. Downloading does not change the catalog.

#### Scenario: Downloads a generated project
- **GIVEN** `orders-api` is registered with owner `payments-team` and description `Order management REST API`
- **WHEN** a client sends `GET /api/v1/services/orders-api/project`
- **THEN** the response status is 200 OK, the content type is `application/zip`, and `Content-Disposition` names the attachment `orders-api.zip`
- **AND** the archive's `orders-api/` folder holds exactly the files DevPlane generates from the `java-rest-api` template for `orders-api`, owned by `payments-team`, with description `Order management REST API`
- **AND** the service's catalog entry, including its `updatedAt`, is unchanged

#### Scenario: Keeps the Gradle wrapper executable
- **GIVEN** `orders-api` is registered
- **WHEN** a client downloads its project
- **THEN** the archive marks `orders-api/gradlew` as executable

#### Scenario: Uses the service's current details
- **GIVEN** `orders-api` was registered with owner `payments-team` and then updated to owner `checkout-team`
- **WHEN** a client downloads its project
- **THEN** the project's README names `checkout-team` and not `payments-team`

#### Scenario: Returns 404 for an unknown service
- **WHEN** a client requests the project of a service name that is not registered
- **THEN** the response status is 404 Not Found and the body is a problem details document
- **AND** the same holds when the request's `Accept` header allows only `application/zip`

### Requirement: Choose a project's template
The system SHALL generate a downloaded project from the template named by the optional `template` query parameter, defaulting to `java-rest-api`, and SHALL reject a template that `GET /api/v1/templates` does not list.

#### Scenario: Accepts a listed template
- **GIVEN** `orders-api` is registered
- **WHEN** a client sends `GET /api/v1/services/orders-api/project?template=java-rest-api`
- **THEN** the response status is 200 OK and the archive holds the `java-rest-api` project for `orders-api`

#### Scenario: Rejects an unknown template
- **GIVEN** `orders-api` is registered
- **WHEN** a client sends `GET /api/v1/services/orders-api/project?template=cobol-batch`
- **THEN** the response status is 400 Bad Request and the problem details `errors` list identifies the `template` field
