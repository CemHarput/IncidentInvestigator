package com.CemHarput.IncidentInvestigator.agent.infrastructure;

import com.CemHarput.IncidentInvestigator.agent.domain.AgentExecutionStep;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentExecutionStepRepository extends JpaRepository<AgentExecutionStep, Long> {

    List<AgentExecutionStep> findByExecutionIdOrderByStepNumberAsc(Long executionId);

    boolean existsByExecutionIdAndEventId(Long executionId, UUID eventId);

    boolean existsByExecutionIdAndStepNumber(Long executionId, Integer stepNumber);
}
