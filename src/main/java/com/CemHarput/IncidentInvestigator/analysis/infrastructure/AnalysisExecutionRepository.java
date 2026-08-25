package com.CemHarput.IncidentInvestigator.analysis.infrastructure;

import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecution;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisExecutionRepository extends JpaRepository<AnalysisExecution, Long> {

    List<AnalysisExecution> findByIncidentIdOrderByCreatedAtDesc(Long incidentId);
}
