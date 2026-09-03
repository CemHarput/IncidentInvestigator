package com.CemHarput.IncidentInvestigator.incident.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RootCauseDecisionPolicyTest {

    private final RootCauseDecisionPolicy policy = new RootCauseDecisionPolicy();

    @Test
    void shouldApplySharedUnknownAndConfidenceRules() {
        assertThat(policy.isInconclusive("unknown", 0.95d)).isTrue();
        assertThat(policy.isInconclusive("DATABASE_PROBLEM", 0.59d)).isTrue();
        assertThat(policy.isInconclusive("DATABASE_PROBLEM", 0.60d)).isFalse();
    }
}
