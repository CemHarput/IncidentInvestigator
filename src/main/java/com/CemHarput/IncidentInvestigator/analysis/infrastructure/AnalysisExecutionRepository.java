package com.CemHarput.IncidentInvestigator.analysis.infrastructure;

import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecution;
import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecutionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisExecutionRepository extends JpaRepository<AnalysisExecution, Long> {

    List<AnalysisExecution> findByIncidentIdOrderByCreatedAtDesc(Long incidentId);

    Optional<AnalysisExecution> findFirstByIncidentIdAndStatusInOrderByCreatedAtDesc(
            Long incidentId,
            Collection<AnalysisExecutionStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select execution from AnalysisExecution execution where execution.id = :id")
    Optional<AnalysisExecution> findByIdForUpdate(@Param("id") Long id);
}
