package com.porcana.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Random;

@Configuration
public class AppConfig {

    /**
     * Random bean for services that need randomness
     * Can be overridden in tests with fixed seed for deterministic results
     */
    @Bean
    public Random random() {
        return new Random();
    }
}