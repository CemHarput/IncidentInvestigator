package com.CemHarput.IncidentInvestigator.config;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class AnalysisKafkaConsumerConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisKafkaConsumerConfig.class);

    @Bean
    CommonErrorHandler analysisKafkaErrorHandler(
            KafkaTemplate<String, Object> kafkaTemplate,
            MeterRegistry meterRegistry,
            @Value("${analysis.kafka.consumer.max-attempts:3}") int maxAttempts,
            @Value("${analysis.kafka.consumer.retry-backoff:500ms}") Duration retryBackoff
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        record.topic() + ".DLT",
                        record.partition()
                )
        );
        long retryCount = Math.max(maxAttempts, 1) - 1L;
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(retryBackoff.toMillis(), retryCount)
        );
        errorHandler.setRetryListeners(new RetryListener() {
            @Override
            public void failedDelivery(
                    ConsumerRecord<?, ?> record,
                    Exception exception,
                    int deliveryAttempt
            ) {
                LOGGER.warn(
                        "Retrying analysis result destination={} deliveryAttempt={} errorType={}",
                        record.topic(),
                        deliveryAttempt,
                        exception.getClass().getSimpleName()
                );
            }

            @Override
            public void recovered(ConsumerRecord<?, ?> record, Exception exception) {
                try {
                    meterRegistry.counter("incident.analysis.kafka.dlt.total").increment();
                } catch (RuntimeException metricsException) {
                    LOGGER.warn("Failed to increment analysis DLT metric", metricsException);
                }
                LOGGER.error(
                        "Analysis result published to DLT destination={} errorType={}",
                        record.topic() + ".DLT",
                        exception.getClass().getSimpleName()
                );
            }
        });
        return errorHandler;
    }
}
