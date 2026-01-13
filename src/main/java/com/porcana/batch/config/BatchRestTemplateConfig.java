package com.porcana.batch.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Duration;

/**
 * Configuration for RestTemplate used in batch jobs
 * Provides properly configured HTTP client for external API calls
 */
@Configuration
public class BatchRestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();

        // Custom error handler to treat 404 as normal response for missing symbols
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public void handleError(ClientHttpResponse response) throws IOException {
                // Don't throw exception for 404 - some symbols may not exist in the API
                if (response.getStatusCode().value() == 404) {
                    return;
                }

                // For other errors, use default handling
                super.handleError(response);
            }
        });

        return restTemplate;
    }
}