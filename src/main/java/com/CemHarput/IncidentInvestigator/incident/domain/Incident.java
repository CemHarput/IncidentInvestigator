package com.CemHarput.IncidentInvestigator.incident.domain;

import com.CemHarput.IncidentInvestigator.incident.exception.InvalidIncidentStateException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "incident_type", nullable = false)
    private String incidentType;

    @Column(nullable = false)
    private String source;

    @Column(name = "assigned_to")
    private String assignedTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status;

    @Column(name = "reported_at", nullable = false)
    private LocalDateTime reportedAt;

    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "root_cause_id")
    private RootCause rootCause;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "incident_id")
    private List<Evidence> evidence = new ArrayList<>();

    public Incident() {
        this.status = IncidentStatus.OPEN;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        this.reportedAt = this.createdAt;
    }

    public Incident(String title, String description, String incidentType, String source) {
        this();
        this.title = title;
        this.description = description;
        this.incidentType = incidentType;
        this.source = source;
    }

    public Incident(String title, String description, String incidentType, String source, LocalDateTime occurredAt) {
        this(title, description, incidentType, source);
        this.occurredAt = occurredAt;
    }

    public void assignTo(String assignedTo) {
        this.assignedTo = assignedTo;
        touch();
    }

    public void startInvestigation() {
        if (this.status != IncidentStatus.OPEN) {
            throw new InvalidIncidentStateException("Only OPEN incidents can be investigated");
        }
        this.status = IncidentStatus.IN_INVESTIGATION;
        this.updatedAt = LocalDateTime.now();
    }

    public void identifyRootCause(RootCause rootCause) {
        if (this.status != IncidentStatus.IN_INVESTIGATION) {
            throw new InvalidIncidentStateException("Incident must be under investigation before resolving");
        }
        this.rootCause = rootCause;
        touch();
    }

    public void resolve() {
        if (this.status != IncidentStatus.IN_INVESTIGATION) {
            throw new InvalidIncidentStateException("Incident must be under investigation before resolving");
        }
        if (this.rootCause == null) {
            throw new InvalidIncidentStateException("Incident must have a root cause before resolving");
        }
        this.status = IncidentStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
        touch();
    }

    public void close() {
        if (this.status != IncidentStatus.RESOLVED) {
            throw new InvalidIncidentStateException("Only resolved incidents can be closed");
        }
        this.status = IncidentStatus.CLOSED;
        // Do not overwrite resolvedAt here; it is set during resolve()
        touch();
    }

    public boolean isResolved() {
        return status == IncidentStatus.RESOLVED || status == IncidentStatus.CLOSED;
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }
    

    public String getTitle() {
        return title;
    }
    

    public String getDescription() {
        return description;
    }
    

    public String getIncidentType() {
        return incidentType;
    }
    

    public String getSource() {
        return source;
    }
    

    public String getAssignedTo() {
        return assignedTo;
    }
    

    public IncidentStatus getStatus() {
        return status;
    }
    

    public LocalDateTime getReportedAt() {
        return reportedAt;
    }
    

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
    

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }
    

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    

    public RootCause getRootCause() {
        return rootCause;
    }

    public List<Evidence> getEvidence() {
        return Collections.unmodifiableList(evidence);
    }

    public void addEvidence(Evidence evidence) {
        if (this.status != IncidentStatus.IN_INVESTIGATION) {
            throw new InvalidIncidentStateException("Evidence can only be added during investigation");
        }
        this.evidence.add(evidence);
        touch();
    }
    

    @Override
    public boolean equals(Object o) {
        // Use identity equality; do not depend on mutable fields for equals/hashCode
        return this == o || (o instanceof Incident other && Objects.equals(this.id, other.id));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
