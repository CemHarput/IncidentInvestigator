package com.CemHarput.IncidentInvestigator;

import com.CemHarput.IncidentInvestigator.incident.domain.Incident;
import com.CemHarput.IncidentInvestigator.incident.domain.IncidentStatus;
import com.CemHarput.IncidentInvestigator.incident.domain.RootCause;
import com.CemHarput.IncidentInvestigator.incident.infrastructure.IncidentRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class IncidentPostgresIntegrationTest {

    @Autowired
    IncidentRepository incidentRepository;

        @Container
        static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15.3")
            .withDatabaseName("testdb")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Test
    void persistenceLifecycle_withPostgres() {
        Incident incident = new Incident("int-title", "int-desc", "type-A", "integration-src", LocalDateTime.now());

        Incident saved = incidentRepository.saveAndFlush(incident);
        Long id = saved.getId();
        assertThat(id).isNotNull();

        Incident loaded = incidentRepository.findById(id).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(loaded.getCreatedAt()).isNotNull();

        // start investigation
        loaded.startInvestigation();
        incidentRepository.saveAndFlush(loaded);
        Incident afterInvest = incidentRepository.findById(id).orElseThrow();
        assertThat(afterInvest.getStatus()).isEqualTo(IncidentStatus.IN_INVESTIGATION);

        // identify root cause
        RootCause rc = new RootCause("summary", "network", true);
        afterInvest.identifyRootCause(rc);
        incidentRepository.saveAndFlush(afterInvest);
        Incident afterRc = incidentRepository.findById(id).orElseThrow();
        assertThat(afterRc.getRootCause()).isNotNull();
        assertThat(afterRc.getRootCause().getId()).isNotNull();

        // resolve
        afterRc.resolve();
        incidentRepository.saveAndFlush(afterRc);
        Incident afterResolve = incidentRepository.findById(id).orElseThrow();
        assertThat(afterResolve.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        LocalDateTime resolvedAt = afterResolve.getResolvedAt();
        assertThat(resolvedAt).isNotNull();

        // close - should not overwrite resolvedAt
        afterResolve.close();
        incidentRepository.saveAndFlush(afterResolve);
        Incident afterClose = incidentRepository.findById(id).orElseThrow();
        assertThat(afterClose.getStatus()).isEqualTo(IncidentStatus.CLOSED);
        assertThat(afterClose.getResolvedAt()).isEqualTo(resolvedAt);
    }
}
