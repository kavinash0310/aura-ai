# M1 — Spring backend foundation

## Goal and boundary

Build an API that proves config, security, validation, persistence, migration, errors, and tests. It stores a research request with REQUESTED status. It does not yet search the web or call a model.

## Learn first

Java classes/records/enums/exceptions; HTTP methods/status and JSON; dependency injection; MVC binding; Bean Validation; Spring Security filter chain and HTTP Basic; SQL, JPA entity state, repository, transactions, Flyway; unit vs integration testing.

## Request flow

~~~mermaid
sequenceDiagram
  actor Owner
  participant Filter as Security filter
  participant MVC as Spring MVC
  participant App as Application service
  participant Repo as JPA repository
  participant DB as PostgreSQL
  Owner->>Filter: authenticated POST JSON
  Filter->>MVC: authorized request
  MVC->>MVC: deserialize and validate
  MVC->>App: createResearchJob(command)
  App->>Repo: save(entity)
  Repo->>DB: INSERT in transaction
  DB-->>Repo: row
  App-->>MVC: response DTO
  MVC-->>Owner: 201 Created + Location
~~~

## Implement

Health; job entity/enum/repository/service/DTO/controller; Flyway migration; PostgreSQL config; local-only Basic Auth; validation and Problem Details; Compose and env example; tests for health/auth/create/read/validation; Actions Maven verification.

## Review questions

1. Why response DTO instead of JPA entity?
2. What creates/closes the transaction?
3. What does Flyway do that Hibernate validation does not?
4. Why 401 vs 403?
5. What does REQUESTED mean, and what has not happened?
6. What if the database is unavailable?
7. Which settings must not have insecure defaults?
8. Which tests exercise persistence?

## Interview explanation

Walk one POST through authentication, binding/validation, controller, service, repository, transaction, migration-defined schema, DTO, and Location header.

## Extension

Add owner-scoped list and cursor pagination after understanding resource lifecycle. Add OIDC before remote deployment. Avoid premature generic frameworks.
