package com.CemHarput.IncidentInvestigator.agent.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentExecutionTest {

    @Test
    void shouldMoveThroughHappyPathLifecycle() {
        AgentExecution execution = execution();

        execution.queue();
        execution.start();
        execution.advanceStep();
        execution.complete("Database connection pool exhaustion identified");

        assertThat(execution.getStatus()).isEqualTo(AgentExecutionStatus.COMPLETED);
        assertThat(execution.getCurrentStep()).isEqualTo(1);
        assertThat(execution.getStartedAt()).isNotNull();
        assertThat(execution.getCompletedAt()).isNotNull();
        assertThat(execution.getDurationMs()).isNotNull();
        assertThat(execution.getResultSummary())
                .isEqualTo("Database connection pool exhaustion identified");
    }

    @Test
    void shouldFailRunningExecutionWithClassification() {
        AgentExecution execution = runningExecution();

        execution.fail(AgentExecutionFailureType.CAPABILITY_FAILURE, "Log analyzer failed");

        assertThat(execution.getStatus()).isEqualTo(AgentExecutionStatus.FAILED);
        assertThat(execution.getFailureType())
                .isEqualTo(AgentExecutionFailureType.CAPABILITY_FAILURE);
        assertThat(execution.getFailureReason()).isEqualTo("Log analyzer failed");
    }

    @Test
    void shouldTimeOutRunningExecution() {
        AgentExecution execution = runningExecution();

        execution.timeout("Execution deadline reached");

        assertThat(execution.getStatus()).isEqualTo(AgentExecutionStatus.TIMED_OUT);
        assertThat(execution.getFailureType()).isEqualTo(AgentExecutionFailureType.TIMEOUT);
    }

    @Test
    void shouldMarkRunningExecutionAsStepLimitExceeded() {
        AgentExecution execution = runningExecution();

        execution.markStepLimitExceeded();

        assertThat(execution.getStatus())
                .isEqualTo(AgentExecutionStatus.STEP_LIMIT_EXCEEDED);
        assertThat(execution.getFailureType())
                .isEqualTo(AgentExecutionFailureType.STEP_LIMIT_EXCEEDED);
    }

    @Test
    void shouldRejectEveryMutationAfterTerminalState() {
        AgentExecution execution = runningExecution();
        execution.complete("Done");

        assertThatThrownBy(execution::start).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(execution::advanceStep).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> execution.complete("Again"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> execution.fail(
                AgentExecutionFailureType.RUNTIME_ERROR,
                "Again"
        )).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> execution.timeout("Again"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(execution::markStepLimitExceeded)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectAdvancingPastConfiguredStepLimit() {
        AgentExecution execution = runningExecution();
        for (int step = 0; step < execution.getMaxSteps(); step++) {
            execution.advanceStep();
        }

        assertThatThrownBy(execution::advanceStep)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Agent execution has reached its step limit");
    }

    private AgentExecution runningExecution() {
        AgentExecution execution = execution();
        execution.queue();
        execution.start();
        return execution;
    }

    private AgentExecution execution() {
        AgentDefinition definition = new AgentDefinition(
                "incident-root-cause-agent",
                "1.0",
                List.of("log-analyzer", "metric-analyzer", "trace-analyzer"),
                new AgentLimits(10, Duration.ofSeconds(60))
        );
        return AgentExecution.create(definition, 42L, UUID.randomUUID());
    }
}
