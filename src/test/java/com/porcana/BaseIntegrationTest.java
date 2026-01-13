package com.porcana;

import com.porcana.config.MockPasswordEncoder;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DirtiesContext
public abstract class BaseIntegrationTest {

    @LocalServerPort
    private int port;

    @MockBean
    protected PasswordEncoder passwordEncoder;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("porcana_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 5);
        registry.add("spring.datasource.hikari.minimum-idle", () -> 2);
        registry.add("spring.datasource.hikari.connection-timeout", () -> 20000);
        registry.add("spring.datasource.hikari.max-lifetime", () -> 600000);
    }

    @BeforeEach
    void setUpRestAssured() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/v1";

        // Mock passwordEncoder to return plain text
        MockPasswordEncoder mockEncoder = new MockPasswordEncoder();
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation ->
            mockEncoder.encode(invocation.getArgument(0)));
        when(passwordEncoder.matches(anyString(), anyString())).thenAnswer(invocation ->
            mockEncoder.matches(invocation.getArgument(0), invocation.getArgument(1)));
    }
}