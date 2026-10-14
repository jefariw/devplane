# platform-api Specification

## Purpose
Conventions that every DevPlane Platform API endpoint follows, so clients such as
the web UI, scripts, and AI assistants can rely on one error format, one source of
API documentation, and one health check.

## Requirements

### Requirement: Errors use problem details
The system SHALL return every error response for a path under `/api/v1` as an RFC 9457 problem details document, with content type `application/problem+json` and the fields `type`, `title`, `status`, and `detail`.

#### Scenario: Unknown endpoint
- **WHEN** a client sends `GET /api/v1/does-not-exist`
- **THEN** the response status is 404 Not Found
- **AND** the body is a problem details document whose `status` is 404

#### Scenario: Malformed request body
- **WHEN** a client sends a body that is not valid JSON to an endpoint that expects JSON
- **THEN** the response status is 400 Bad Request and the body is a problem details document

#### Scenario: Unexpected errors hide internals
- **WHEN** an unexpected error occurs while the system handles a request
- **THEN** the response status is 500 Internal Server Error and the body is a problem details document
- **AND** the body does not include the exception message, the exception class, or a stack trace

### Requirement: Validation errors list every invalid field
The system SHALL reject a request that has invalid fields with 400 Bad Request and a problem details document whose `errors` array has one entry per invalid field, each with the `field` name and a human-readable `message`.

#### Scenario: Reports every invalid field
- **WHEN** a client sends a request in which two fields are invalid
- **THEN** the response status is 400 Bad Request
- **AND** `errors` has an entry for each of the two fields, each with a `field` and a `message`

### Requirement: OpenAPI documentation
The system SHALL publish an OpenAPI 3 description of every Platform API endpoint at `/v3/api-docs`, and an interactive documentation page at `/swagger-ui.html`.

#### Scenario: Serves the OpenAPI description
- **WHEN** a client sends `GET /v3/api-docs`
- **THEN** the response status is 200 OK and the body is an OpenAPI 3 JSON document
- **AND** every endpoint under `/api/v1` appears in its `paths`

#### Scenario: Serves the documentation page
- **WHEN** a browser opens `/swagger-ui.html`
- **THEN** it shows the interactive API documentation, after following any redirects

### Requirement: Health check
The system SHALL report its health at `GET /actuator/health`, including whether it can reach its database.

#### Scenario: Healthy
- **GIVEN** the database is reachable
- **WHEN** a client sends `GET /actuator/health`
- **THEN** the response status is 200 OK and `status` is `UP`

#### Scenario: Database unreachable
- **GIVEN** the database is not reachable
- **WHEN** a client sends `GET /actuator/health`
- **THEN** the response status is 503 Service Unavailable and `status` is `DOWN`
