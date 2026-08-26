package com.CemHarput.IncidentInvestigator.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalysisExecutionTest {

    @Test
    void shouldCompleteQueuedExecutionWithResultEventId() {
        AnalysisExecution execution = AnalysisExecution.create(42L);
        UUID eventId = UUID.randomUUID();
        execution.queue();

        execution.completeAsync("DATABASE_CONNECTION_POOL_EXHAUSTION", 0.91d, eventId);

        assertThat(execution.getStatus()).isEqualTo(AnalysisExecutionStatus.COMPLETED);
        assertThat(execution.getResultEventId()).isEqualTo(eventId);
        assertThat(execution.hasProcessedResult(eventId)).isTrue();
    }

    @Test
    void shouldMarkQueuedExecutionInconclusiveWithResultEventId() {
        AnalysisExecution execution = AnalysisExecution.create(42L);
        UUID eventId = UUID.randomUUID();
        execution.queue();

        execution.markAsyncInconclusive(0.20d, eventId);

        assertThat(execution.getStatus()).isEqualTo(AnalysisExecutionStatus.INCONCLUSIVE);
        assertThat(execution.getSelectedRootCause()).isEqualTo("UNKNOWN");
        assertThat(execution.getResultEventId()).isEqualTo(eventId);
    }

    @Test
    void shouldFailQueuedExecutionWithResultEventId() {
        AnalysisExecution execution = AnalysisExecution.create(42L);
        UUID eventId = UUID.randomUUID();
        execution.queue();

        execution.failAsync("Analyzer failed", AnalysisExecutionFailureType.INTERNAL_ERROR, eventId);

        assertThat(execution.getStatus()).isEqualTo(AnalysisExecutionStatus.FAILED);
        assertThat(execution.getFailureReason()).isEqualTo("Analyzer failed");
        assertThat(execution.getResultEventId()).isEqualTo(eventId);
    }

    @Test
    void shouldRejectAsyncResultForNonQueuedExecution() {
        AnalysisExecution execution = AnalysisExecution.create(42L);
        execution.start();

        assertThatThrownBy(() -> execution.completeAsync("X", 0.90d, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Async analysis result can only be applied to a QUEUED execution");
    }

    @Test
    void shouldTransitionFromCreatedToQueued() {
        AnalysisExecution execution = AnalysisExecution.create(42L);

        execution.queue();

        assertThat(execution.getStatus()).isEqualTo(AnalysisExecutionStatus.QUEUED);
        assertThatThrownBy(execution::queue)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only CREATED executions can be queued");
    }

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

    @Test
    void shouldStoreFailureTypeAndAttemptCount() {
        AnalysisExecution execution = AnalysisExecution.create(42L);
        execution.start();

        execution.fail("Read timed out", AnalysisExecutionFailureType.TIMEOUT);

        assertThat(execution.getStatus()).isEqualTo(AnalysisExecutionStatus.FAILED);
        assertThat(execution.getFailureType()).isEqualTo(AnalysisExecutionFailureType.TIMEOUT);
        assertThat(execution.getAttemptCount()).isEqualTo(1);
        assertThat(execution.getFailureReason()).isEqualTo("Read timed out");
    }

    @Test
    void shouldIncrementAttemptCountWhenRetried() {
        AnalysisExecution execution = AnalysisExecution.create(42L);
        execution.start();
        execution.fail("Temporary network issue", AnalysisExecutionFailureType.CONNECTION_FAILURE);

        execution.incrementAttemptCount();

        assertThat(execution.getAttemptCount()).isEqualTo(2);
    }
}
