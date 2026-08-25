package com.CemHarput.IncidentInvestigator.incident.infrastructure;

import com.CemHarput.IncidentInvestigator.incident.domain.Incident;
import com.CemHarput.IncidentInvestigator.incident.domain.IncidentStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    List<Incident> findByStatus(IncidentStatus status);

    List<Incident> findByAssignedTo(String assignedTo);

    List<Incident> findByIncidentType(String incidentType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select incident from Incident incident where incident.id = :id")
    Optional<Incident> findByIdForAnalysis(@Param("id") Long id);
}
