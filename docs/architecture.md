# DevPlane Architecture

DevPlane will consist of four primary layers:

1. Developer interface
2. Platform API
3. Infrastructure and deployment layer
4. Observability and AI operations layer

## Initial Architecture

Developer
↓
DevPlane UI
↓
DevPlane API
↓
Service Catalog
↓
Platform Integrations

## Platform API Application

The Platform API is a single Spring Boot application (Java 21) at the repository
root, backed by PostgreSQL. Its code is organized by feature, and later features
such as templates and deployments become sibling packages:

```
io.github.spollito.devplane
├── api/       behavior shared by every endpoint: problem details error responses
└── catalog/   the service catalog: REST controller, request and response records,
               ServiceCatalog (business rules), ServiceEntity and its repository
```

A request flows from the controller, which validates its body, to
`ServiceCatalog`, then through a Spring Data JPA repository to PostgreSQL.
Every error becomes an RFC 9457 problem details response in
`api/ApiExceptionHandler`.

The API is described at `/v3/api-docs` (browsable at `/swagger-ui.html`), and
`/actuator/health` reports whether the application can reach its database.

### Database

Flyway migrations in `src/main/resources/db/migration` own the schema, and
Hibernate only validates that the entities match it.

The `services` table holds the catalog:

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `bigint` identity | Internal primary key, never exposed by the API |
| `name` | `varchar(63)` | Unique; how the API identifies a service |
| `owner` | `varchar(63)` | Indexed for listing services by owner |
| `description` | `varchar(300)` | Optional |
| `lifecycle` | `varchar(20)` | `EXPERIMENTAL`, `PRODUCTION`, or `DEPRECATED` (check constraint) |
| `created_at`, `updated_at` | `timestamptz` | Set by the application, at microsecond precision |
