package com.CemHarput.IncidentInvestigator.config;

import java.time.Duration;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class AnalysisKafkaConsumerConfig {

    @Bean
    CommonErrorHandler analysisKafkaErrorHandler(
            KafkaTemplate<String, Object> kafkaTemplate,
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
        return new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(retryBackoff.toMillis(), retryCount)
        );
    }
}
