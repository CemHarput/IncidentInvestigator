package com.CemHarput.IncidentInvestigator.incident.infrastructure;

import com.CemHarput.IncidentInvestigator.incident.domain.Incident;
import com.CemHarput.IncidentInvestigator.incident.domain.IncidentStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    List<Incident> findByStatus(IncidentStatus status);

    List<Incident> findByAssignedTo(String assignedTo);

    List<Incident> findByIncidentType(String incidentType);
}
