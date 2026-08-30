package com.CemHarput.IncidentInvestigator.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "processed_agent_events")
public class ProcessedAgentEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "execution_id", nullable = false, updatable = false)
    private Long executionId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt;

    protected ProcessedAgentEvent() {
    }

    public ProcessedAgentEvent(UUID eventId, Long executionId, String eventType) {
        this.eventId = Objects.requireNonNull(eventId, "Event id is required");
        this.executionId = Objects.requireNonNull(executionId, "Execution id is required");
        this.eventType = Objects.requireNonNull(eventType, "Event type is required");
        this.processedAt = LocalDateTime.now();
    }

    public UUID getEventId() {
        return eventId;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public String getEventType() {
        return eventType;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}
