package com.CemHarput.IncidentInvestigator.incident.domain;

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
            throw new IllegalStateException("Only OPEN incidents can be investigated");
        }
        this.status = IncidentStatus.IN_INVESTIGATION;
        this.updatedAt = LocalDateTime.now();
    }

    public void identifyRootCause(RootCause rootCause) {
        if (this.status != IncidentStatus.IN_INVESTIGATION) {
            throw new IllegalStateException("Incident must be under investigation before resolving");
        }
        this.rootCause = rootCause;
        touch();
    }

    public void resolve() {
        if (this.status != IncidentStatus.IN_INVESTIGATION) {
            throw new IllegalStateException("Incident must be under investigation before resolving");
        }
        if (this.rootCause == null) {
            throw new IllegalStateException("Incident must have a root cause before resolving");
        }
        this.status = IncidentStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
        touch();
    }

    public void close() {
        if (this.status != IncidentStatus.RESOLVED) {
            throw new IllegalStateException("Only resolved incidents can be closed");
        }
        this.status = IncidentStatus.CLOSED;
        this.resolvedAt = LocalDateTime.now();
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

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIncidentType() {
        return incidentType;
    }

    public void setIncidentType(String incidentType) {
        this.incidentType = incidentType;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public void setStatus(IncidentStatus status) {
        this.status = status;
    }

    public LocalDateTime getReportedAt() {
        return reportedAt;
    }

    public void setReportedAt(LocalDateTime reportedAt) {
        this.reportedAt = reportedAt;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public RootCause getRootCause() {
        return rootCause;
    }

    public void setRootCause(RootCause rootCause) {
        this.rootCause = rootCause;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Incident incident = (Incident) o;
        return Objects.equals(id, incident.id)
                && Objects.equals(title, incident.title)
                && Objects.equals(description, incident.description)
                && Objects.equals(incidentType, incident.incidentType)
                && Objects.equals(source, incident.source)
                && Objects.equals(assignedTo, incident.assignedTo)
                && status == incident.status
                && Objects.equals(reportedAt, incident.reportedAt)
                && Objects.equals(occurredAt, incident.occurredAt)
                && Objects.equals(resolvedAt, incident.resolvedAt)
                && Objects.equals(createdAt, incident.createdAt)
                && Objects.equals(updatedAt, incident.updatedAt)
                && Objects.equals(rootCause, incident.rootCause);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, description, incidentType, source, assignedTo, status,
                reportedAt, occurredAt, resolvedAt, createdAt, updatedAt, rootCause);
    }
}
