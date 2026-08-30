package com.CemHarput.IncidentInvestigator.agent.infrastructure;

import com.CemHarput.IncidentInvestigator.agent.domain.AgentExecution;
import com.CemHarput.IncidentInvestigator.agent.domain.AgentExecutionStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentExecutionRepository extends JpaRepository<AgentExecution, Long> {

    Optional<AgentExecution> findFirstByAgentNameAndIncidentIdAndStatusInOrderByCreatedAtDesc(
            String agentName,
            Long incidentId,
            Collection<AgentExecutionStatus> statuses
    );
}
