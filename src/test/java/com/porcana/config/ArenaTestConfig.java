package com.porcana.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Random;

/**
 * Arena 테스트용 Configuration
 * 고정 seed Random을 주입하여 결정적인 테스트 결과 보장
 */
@TestConfiguration
public class ArenaTestConfig {

    /**
     * 테스트용 고정 seed Random
     * 서로 다른 seed를 사용하면 다른 결과가 나옴
     */
    @Bean
    @Primary
    public Random testRandom() {
        // 고정 seed로 결정적인 결과 보장
        return new Random(12345L);
    }
}