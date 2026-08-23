package com.CemHarput.IncidentInvestigator.incident.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "root_causes")
public class RootCause {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String summary;

    @Column(name = "root_cause_type")
    private String rootCauseType;

    @Column(nullable = false)
    private boolean confirmed;

    @Column(name = "created_at", nullable = false)
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


    @Override
    public boolean equals(Object o) {
        // Identity equality on id only to avoid mutable-field issues
        return this == o || (o instanceof RootCause other && Objects.equals(this.id, other.id));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
