package com.CemHarput.IncidentInvestigator.incident.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public class Incident {

    private Long id;
    private String title;
    private String description;
    private String incidentType;
    private String source;
    private String assignedTo;
    private IncidentStatus status;
    private LocalDateTime reportedAt;
    private LocalDateTime occurredAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
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

    public void assignTo(String assignedTo) {
        this.assignedTo = assignedTo;
        touch();
    }

    public void startInvestigation() {
        this.status = IncidentStatus.IN_INVESTIGATION;
        touch();
    }

    public void identifyRootCause(RootCause rootCause) {
        this.rootCause = rootCause;
        this.status = IncidentStatus.IN_INVESTIGATION;
        touch();
    }

    public void resolve() {
        this.status = IncidentStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
        touch();
    }

    public void close() {
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
