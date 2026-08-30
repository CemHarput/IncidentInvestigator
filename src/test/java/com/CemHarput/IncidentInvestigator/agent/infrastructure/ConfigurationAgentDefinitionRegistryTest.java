package com.CemHarput.IncidentInvestigator.agent.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.CemHarput.IncidentInvestigator.agent.domain.AgentDefinition;
import com.CemHarput.IncidentInvestigator.agent.exception.AgentDefinitionNotFoundException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ConfigurationAgentDefinitionRegistryTest {

    @Test
    void shouldResolveConfiguredAgentDefinition() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("agents.incident-root-cause-agent.version", "1.0")
                .withProperty(
                        "agents.incident-root-cause-agent.capabilities[0]",
                        "log-analyzer"
                )
                .withProperty(
                        "agents.incident-root-cause-agent.capabilities[1]",
                        "metric-analyzer"
                )
                .withProperty("agents.incident-root-cause-agent.limits.max-steps", "10")
                .withProperty("agents.incident-root-cause-agent.limits.timeout", "60s");
        ConfigurationAgentDefinitionRegistry registry =
                new ConfigurationAgentDefinitionRegistry(environment);

        AgentDefinition definition = registry.getRequired("incident-root-cause-agent");

        assertThat(definition.name()).isEqualTo("incident-root-cause-agent");
        assertThat(definition.version()).isEqualTo("1.0");
        assertThat(definition.capabilities())
                .containsExactly("log-analyzer", "metric-analyzer");
        assertThat(definition.limits().maxSteps()).isEqualTo(10);
        assertThat(definition.limits().timeout()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void shouldRejectUnknownAgent() {
        ConfigurationAgentDefinitionRegistry registry =
                new ConfigurationAgentDefinitionRegistry(new MockEnvironment());

        assertThatThrownBy(() -> registry.getRequired("unknown-agent"))
                .isInstanceOf(AgentDefinitionNotFoundException.class)
                .hasMessage("Agent definition not found: unknown-agent");
    }
}
