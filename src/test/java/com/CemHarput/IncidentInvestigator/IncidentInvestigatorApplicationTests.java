package com.CemHarput.IncidentInvestigator;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class IncidentInvestigatorApplicationTests {

	@Autowired
	Resource resource;

	@Test
	void contextLoads() {
	}

	@Test
	void openTelemetryResource_shouldContainV5ServiceMetadata() {
		assertThat(resource.getAttribute(AttributeKey.stringKey("service.name")))
				.isEqualTo("incident-investigator");
		assertThat(resource.getAttribute(AttributeKey.stringKey("service.version")))
				.isEqualTo("v5");
		assertThat(resource.getAttribute(AttributeKey.stringKey("deployment.environment")))
				.isEqualTo("local");
	}

}
