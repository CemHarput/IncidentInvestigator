package com.CemHarput.IncidentInvestigator.agent.infrastructure;

import com.CemHarput.IncidentInvestigator.agent.domain.AgentExecutionStep;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentExecutionStepRepository extends JpaRepository<AgentExecutionStep, Long> {

    List<AgentExecutionStep> findByExecutionIdOrderByStepNumberAsc(Long executionId);
}
