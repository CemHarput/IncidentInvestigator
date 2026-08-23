# IncidentInvestigator

Lightweight Spring Boot service to manage incidents, root-cause investigation and evidence collection.

Core features
- DDD-style domain model for `Incident` and `RootCause`.
- Evidence vertical slice: `Evidence` entity, `EvidenceType` enum, `addEvidence()` lifecycle rules.
- REST API for incident lifecycle and evidence collection.
- JPA/Hibernate persistence with Testcontainers-backed PostgreSQL integration tests.

Tech
- Java 25, Spring Boot 4.1.1
- JPA / Hibernate
- Testcontainers (PostgreSQL) for integration tests

Run tests
- Run full test suite (unit + integration):

```powershell
.\mvnw test -DskipITs=false
```

- Run only the PostgreSQL integration test:

```powershell
.\mvnw -Dtest=IncidentPostgresIntegrationTest test
```

Note: Integration tests require Docker (Testcontainers).

API (important endpoints)

- Create incident
  - POST /api/v1/incidents
  - Request: `CreateIncidentRequest` (title, description, incidentType, source, occurredAt)

- Get incident
  - GET /api/v1/incidents/{id}
  - Response: `IncidentResponse` includes `rootCause` and `evidence` list

- Start investigation
  - POST /api/v1/incidents/{id}/investigation

- Add root cause
  - POST /api/v1/incidents/{id}/root-cause
  - Request: `AddRootCauseRequest` (summary, rootCauseType, confirmed)

- Add evidence
  - POST /api/v1/incidents/{id}/evidence
  - Request: `AddEvidenceRequest` (type: LOG|METRIC|TRACE, source, content, observedAt)

- Resolve / Close
  - POST /api/v1/incidents/{id}/resolve
  - POST /api/v1/incidents/{id}/close

Design notes

- Evidence can only be added while an incident is `IN_INVESTIGATION` — domain enforces this and throws `InvalidIncidentStateException` otherwise.
- `Incident` owns `Evidence` with `@OneToMany(cascade = ALL, orphanRemoval = true)` and `@JoinColumn(name = "incident_id")` so evidence is persisted with the incident.
- Service layer methods are `@Transactional` and mapping to DTOs happens inside the service to avoid lazy-loading issues.

Next steps
- Add a persistence integration test to verify evidence cascade and mapping (todo: `Add persistence integration test for Evidence cascade`).
- Commit and open a PR when ready.

Contact
- For changes or questions, update code under `src/main/java/com/CemHarput/IncidentInvestigator` and run the tests locally.
# IncidentInvestigator

IncidentInvestigator is a Spring Boot-based application for recording, tracking, and managing operational incidents such as failures, errors, and service disruptions.

The main goals of the project are to:
- create new incidents,
- track incident status,
- manage the investigation lifecycle,
- analyze root causes,
- build a foundation for incident-based analysis and reporting.

## Technology Stack

- Java 25
- Spring Boot 4.1.1
- Spring Web MVC
- Spring Data JPA
- Spring Validation
- PostgreSQL
- Kafka
- Testcontainers
- Maven

## Project Structure

```text
src/
├── main/
│   ├── java/
│   │   └── com/
│   │       └── CemHarput/
│   │           └── IncidentInvestigator/
│   │               ├── IncidentInvestigatorApplication.java
│   │               ├── analysis/
│   │               │   ├── application/
│   │               │   ├── client/
│   │               │   └── dto/
│   │               ├── config/
│   │               └── incident/
│   │                   ├── api/
│   │                   ├── application/
│   │                   ├── domain/
│   │                   └── infrastructure/
│   └── resources/
│       └── application.properties
└── test/
    └── java/
```

## Domain Model

### Incident
This is the main domain entity. It contains core details such as the incident title, description, type, source, assigned team, and status.

### RootCause
This model represents the underlying cause of the incident. It stores cause analysis details, confirmation status, and explanatory information.

### IncidentStatus
This enum represents the lifecycle of an incident:
- OPEN
- IN_INVESTIGATION
- RESOLVED
- CLOSED
- REJECTED

## Workflow

1. A user creates a new incident.
2. The incident is stored as an open record.
3. A responsible team or person is assigned.
4. The investigation starts.
5. The root cause is identified.
6. The incident is resolved or closed.

## Running the Project

To run the project locally:

```bash
./mvnw clean install
./mvnw spring-boot:run
```

On Windows:

```powershell
mvnw.cmd clean install
mvnw.cmd spring-boot:run
```

## Configuration

The application uses `src/main/resources/application.properties` for basic configuration. The current application name is defined as:

```properties
spring.application.name=IncidentInvestigator
```

## Testing

Sample tests are included to validate core domain behavior:

```bash
./mvnw test
```

## Notes

This project is still in its early development stage. The domain model and foundation structure are already in place, and the following layers can be expanded later:

- repository and JPA entity mappings,
- service layer business logic,
- REST controller and DTO structure,
- Kafka event production/consumption,
- analysis and reporting modules,
- security and authorization layers.

## Development Goal

This application aims to make operational incident management more organized, traceable, and analyzable. In particular, it supports faster response, root-cause identification, and operational visibility during failures and service disruptions.

## Architecture

The following diagram shows the high-level architecture of the IncidentInvestigator application.

```mermaid
graph LR
  Client[Client / UI / curl]
  subgraph App [IncidentInvestigator (Spring Boot)]
    direction TB
    Controller[REST Controller\n/api/v1/*]
    Service[Service Layer]\n(Transactional)
    Domain[Domain Model\n(Incident, Evidence, RootCause)]
    Repo[Spring Data JPA Repositories]
    Persistence[(JPA / Hibernate)]
  end

  Postgres[(PostgreSQL)]
  Testcontainers[Testcontainers (Postgres) - integration tests]
  Kafka[Kafka / Messaging]
  Admin[Admin / Operators]

  Client -->|HTTP| Controller
  Controller --> Service
  Service --> Domain
  Service -->|calls| Repo
  Repo --> Persistence
  Persistence --> Postgres
  Testcontainers --> Postgres
  Service -->|publishes| Kafka
  Admin -->|monitoring,ops| Postgres
  Admin -->|deploy| App

  classDef db fill:#f9f,stroke:#333,stroke-width:1px;
  class Postgres,Testcontainers db;
```
