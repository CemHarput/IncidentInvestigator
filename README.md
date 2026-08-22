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
