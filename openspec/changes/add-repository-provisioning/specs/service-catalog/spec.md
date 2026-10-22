# Service Catalog

## MODIFIED Requirements

### Requirement: View a service
The system SHALL return a registered service's details from `GET /api/v1/services/{name}`. The `repository` field is `null` until the service's repository is provisioned, and then holds the repository's `url`, `template`, and `createdAt`. Every response that returns a service, including lists, registration, and updates, uses this same representation.

#### Scenario: Returns a registered service
- **GIVEN** a service named `orders-api` is registered
- **WHEN** a client sends `GET /api/v1/services/orders-api`
- **THEN** the response status is 200 OK and the body contains the service's `name`, `owner`, `description`, `lifecycle`, `createdAt`, and `updatedAt`
- **AND** `repository` is `null`

#### Scenario: Shows a provisioned repository
- **GIVEN** `orders-api`'s repository was provisioned at `https://github.com/octo-dev/orders-api` from the `java-rest-api` template
- **WHEN** a client sends `GET /api/v1/services/orders-api`, or lists services
- **THEN** the service's `repository` has `url` `https://github.com/octo-dev/orders-api`, `template` `java-rest-api`, and the `createdAt` of the provisioning

#### Scenario: Returns 404 for an unknown service
- **WHEN** a client requests a service name that is not registered
- **THEN** the response status is 404 Not Found
