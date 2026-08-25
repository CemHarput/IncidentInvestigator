package com.CemHarput.IncidentInvestigator.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AnalysisExecutionTest {

    @Test
    void shouldTransitionFromCreatedToRunningToCompleted() {
        AnalysisExecution execution = AnalysisExecution.create(42L);

        execution.start();
        execution.complete("DATABASE_CONNECTION_POOL_EXHAUSTION", 0.91d);

        assertThat(execution.getStatus()).isEqualTo(AnalysisExecutionStatus.COMPLETED);
        assertThat(execution.getSelectedRootCause()).isEqualTo("DATABASE_CONNECTION_POOL_EXHAUSTION");
        assertThat(execution.getSelectedConfidence()).isEqualTo(0.91d);
        assertThat(execution.getStartedAt()).isNotNull();
        assertThat(execution.getCompletedAt()).isNotNull();
        assertThat(execution.getDurationMs()).isNotNull();
    }

    @Test
    void shouldMarkInconclusiveWhenConfidenceDoesNotPassThreshold() {
        AnalysisExecution execution = AnalysisExecution.create(42L);
        execution.start();

        execution.markInconclusive(0.45d);

        assertThat(execution.getStatus()).isEqualTo(AnalysisExecutionStatus.INCONCLUSIVE);
        assertThat(execution.getSelectedConfidence()).isEqualTo(0.45d);
        assertThat(execution.getCompletedAt()).isNotNull();
    }

    @Test
    void shouldRejectIllegalStateTransitions() {
        AnalysisExecution execution = AnalysisExecution.create(42L);
        execution.start();
        execution.complete("X", 0.80d);

        assertThatThrownBy(() -> execution.start())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> execution.complete("Y", 0.90d))
                .isInstanceOf(IllegalStateException.class);
    }
}
