package com.CemHarput.IncidentInvestigator.agent.infrastructure;

import com.CemHarput.IncidentInvestigator.agent.application.AgentDefinitionRegistry;
import com.CemHarput.IncidentInvestigator.agent.domain.AgentDefinition;
import com.CemHarput.IncidentInvestigator.agent.domain.AgentLimits;
import com.CemHarput.IncidentInvestigator.agent.exception.AgentDefinitionNotFoundException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ConfigurationAgentDefinitionRegistry implements AgentDefinitionRegistry {

    private final Map<String, AgentDefinition> definitions;

    public ConfigurationAgentDefinitionRegistry(Environment environment) {
        Map<String, AgentConfiguration> configurations = Binder.get(environment)
                .bind("agents", Bindable.mapOf(String.class, AgentConfiguration.class))
                .orElseGet(Map::of);

        this.definitions = configurations.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().toDefinition(entry.getKey())
                ));
    }

    @Override
    public AgentDefinition getRequired(String agentName) {
        AgentDefinition definition = definitions.get(agentName);
        if (definition == null) {
            throw new AgentDefinitionNotFoundException(agentName);
        }
        return definition;
    }

    public record AgentConfiguration(
            String version,
            List<String> capabilities,
            LimitsConfiguration limits
    ) {

        private AgentDefinition toDefinition(String name) {
            if (limits == null) {
                throw new IllegalStateException("Limits are required for agent: " + name);
            }
            return new AgentDefinition(
                    name,
                    version,
                    capabilities == null ? List.of() : capabilities,
                    new AgentLimits(limits.maxSteps(), limits.timeout())
            );
        }
    }

    public record LimitsConfiguration(int maxSteps, Duration timeout) {
    }
}
