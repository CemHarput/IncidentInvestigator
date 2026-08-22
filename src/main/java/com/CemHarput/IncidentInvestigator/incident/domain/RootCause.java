package com.CemHarput.IncidentInvestigator.incident.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public class RootCause {

    private Long id;
    private String summary;
    private String rootCauseType;
    private boolean confirmed;
    private LocalDateTime createdAt;

    public RootCause() {
        this.createdAt = LocalDateTime.now();
    }

    public RootCause(String summary, String rootCauseType, boolean confirmed) {
        this();
        this.summary = summary;
        this.rootCauseType = rootCauseType;
        this.confirmed = confirmed;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getRootCauseType() {
        return rootCauseType;
    }

    public void setRootCauseType(String rootCauseType) {
        this.rootCauseType = rootCauseType;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RootCause rootCause = (RootCause) o;
        return confirmed == rootCause.confirmed
                && Objects.equals(id, rootCause.id)
                && Objects.equals(summary, rootCause.summary)
                && Objects.equals(rootCauseType, rootCauseType)
                && Objects.equals(createdAt, rootCause.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, summary, rootCauseType, confirmed, createdAt);
    }
}
