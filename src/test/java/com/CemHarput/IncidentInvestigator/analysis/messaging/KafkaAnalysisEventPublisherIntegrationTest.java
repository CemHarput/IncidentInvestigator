package com.CemHarput.IncidentInvestigator.analysis.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisEvidence;
import com.CemHarput.IncidentInvestigator.analysis.messaging.event.AnalysisRequestedEvent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:kafkaPublisherTest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Testcontainers
class KafkaAnalysisEventPublisherIntegrationTest {

    private static final String TOPIC = "incident.analysis.requested.v1";

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("apache/kafka-native:4.3.1")
    );

    @Autowired
    AnalysisEventPublisher publisher;

    @Autowired
    ObjectMapper objectMapper;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Test
    void shouldPublishRequestedEventToKafkaWithExpectedContract() throws Exception {
        AnalysisRequestedEvent event = new AnalysisRequestedEvent(
                UUID.randomUUID(),
                99L,
                42L,
                "Payment service latency",
                "LATENCY",
                List.of(new AnalysisEvidence(
                        "LOG",
                        "payment-service",
                        "HikariPool - Connection is not available",
                        LocalDateTime.of(2026, 8, 24, 12, 0)
                )),
                LocalDateTime.now()
        );

        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of(TOPIC));
            consumer.poll(Duration.ofMillis(500));

            publisher.publishAnalysisRequested(event);

            ConsumerRecord<String, String> record = awaitRecord(consumer);
            JsonNode payload = objectMapper.readTree(record.value());

            assertThat(record.key()).isEqualTo("42");
            assertThat(payload.get("eventId").asText()).isEqualTo(event.eventId().toString());
            assertThat(payload.get("executionId").asLong()).isEqualTo(99L);
            assertThat(payload.get("incidentId").asLong()).isEqualTo(42L);
            assertThat(payload.get("evidence")).hasSize(1);
            assertThat(payload.get("evidence").get(0).get("observedAt").asText())
                    .startsWith("2026-08-24T12:00");
            assertThat(payload.get("requestedAt").asText()).isNotBlank();
        }
    }

    private KafkaConsumer<String, String> consumer() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "analysis-requested-contract-test");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(properties);
    }

    private ConsumerRecord<String, String> awaitRecord(KafkaConsumer<String, String> consumer) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(250));
            for (ConsumerRecord<String, String> record : records) {
                return record;
            }
        }
        throw new AssertionError("No analysis requested event received from Kafka");
    }
}
