package com.CemHarput.IncidentInvestigator.analysis.infrastructure;

import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecution;
import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecutionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisExecutionRepository extends JpaRepository<AnalysisExecution, Long> {

    List<AnalysisExecution> findByIncidentIdOrderByCreatedAtDesc(Long incidentId);

    Optional<AnalysisExecution> findFirstByIncidentIdAndStatusInOrderByCreatedAtDesc(
            Long incidentId,
            Collection<AnalysisExecutionStatus> statuses
    );
}
