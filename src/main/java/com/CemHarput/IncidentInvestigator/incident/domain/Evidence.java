package com.CemHarput.IncidentInvestigator.incident.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "evidences")
public class Evidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvidenceType type;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false, length = 5000)
    private String content;

    @Column(name = "observed_at")
    private LocalDateTime observedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Evidence() {
    }

    public Evidence(EvidenceType type, String source, String content, LocalDateTime observedAt) {
        this.type = type;
        this.source = source;
        this.content = content;
        this.observedAt = observedAt;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public EvidenceType getType() {
        return type;
    }

    public String getSource() {
        return source;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getObservedAt() {
        return observedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
