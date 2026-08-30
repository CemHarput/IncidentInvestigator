package com.CemHarput.IncidentInvestigator.agent.infrastructure;

import com.CemHarput.IncidentInvestigator.agent.domain.ProcessedAgentEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedAgentEventRepository extends JpaRepository<ProcessedAgentEvent, UUID> {
}
