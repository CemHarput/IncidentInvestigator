package com.CemHarput.IncidentInvestigator.config;

import java.time.Duration;

import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClientCustomizer incidentAnalyzerRestClientCustomizer(
            @Value("${incident-analyzer.connect-timeout:2s}") Duration connectTimeout,
            @Value("${incident-analyzer.read-timeout:5s}") Duration readTimeout
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return builder -> builder.requestFactory(factory);
    }
}
