package com.CemHarput.IncidentInvestigator.agent.infrastructure;

import com.CemHarput.IncidentInvestigator.agent.domain.AgentExecution;
import com.CemHarput.IncidentInvestigator.agent.domain.AgentExecutionStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentExecutionRepository extends JpaRepository<AgentExecution, Long> {

    Optional<AgentExecution> findFirstByAgentNameAndIncidentIdAndStatusInOrderByCreatedAtDesc(
            String agentName,
            Long incidentId,
            Collection<AgentExecutionStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select execution from AgentExecution execution where execution.id = :id")
    Optional<AgentExecution> findByIdForUpdate(@Param("id") Long id);
}
