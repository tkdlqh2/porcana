package com.porcana.batch.job;

import com.porcana.batch.provider.exchangerate.KoreaEximProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for ExchangeRateBatchJob
 *
 * 핵심 변경 사항 검증:
 * - timestamp 파라미터 기준 당일 날짜에서 전일(minusDays(1)) 날짜로 환율 조회
 */
@SpringBootTest
@Testcontainers
class ExchangeRateBatchJobTest {

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
    }

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job exchangeRateJob;

    @MockBean
    private KoreaEximProvider koreaEximProvider;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Test
    @DisplayName("exchangeRateJob 실행 성공 - 배치 상태 COMPLETED")
    void exchangeRateJob_completesSuccessfully() throws Exception {
        when(koreaEximProvider.fetchExchangeRates(any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        long timestamp = ZonedDateTime.of(2026, 3, 13, 12, 0, 0, 0, KST)
                .toInstant().toEpochMilli();

        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", timestamp)
                .toJobParameters();

        JobExecution jobExecution = jobLauncher.run(exchangeRateJob, jobParameters);

        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    @Test
    @DisplayName("전일 환율 조회 - timestamp 당일 날짜의 하루 전 날짜로 API 호출")
    void exchangeRateJob_fetchesPreviousDayRate() throws Exception {
        when(koreaEximProvider.fetchExchangeRates(any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        // 2026-03-13 12:00:01 KST timestamp
        ZonedDateTime targetDateTime = ZonedDateTime.of(2026, 3, 13, 12, 0, 1, 0, KST);
        long timestamp = targetDateTime.toInstant().toEpochMilli();

        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", timestamp)
                .toJobParameters();

        jobLauncher.run(exchangeRateJob, jobParameters);

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(koreaEximProvider).fetchExchangeRates(dateCaptor.capture());

        // timestamp가 2026-03-13이므로 전일인 2026-03-12로 조회해야 함
        LocalDate expectedDate = LocalDate.of(2026, 3, 12);
        assertThat(dateCaptor.getValue()).isEqualTo(expectedDate);
    }

    @Test
    @DisplayName("전일 환율 조회 - 월요일 timestamp이면 일요일(전일) 환율 조회")
    void exchangeRateJob_fetchesSundayRate_whenTimestampIsMonday() throws Exception {
        when(koreaEximProvider.fetchExchangeRates(any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        // 2026-03-16 (Monday) 07:15 KST - 실제 스케줄 실행 시각 시뮬레이션
        ZonedDateTime mondayDateTime = ZonedDateTime.of(2026, 3, 16, 7, 15, 0, 0, KST);
        long timestamp = mondayDateTime.toInstant().toEpochMilli();

        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", timestamp)
                .toJobParameters();

        jobLauncher.run(exchangeRateJob, jobParameters);

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(koreaEximProvider).fetchExchangeRates(dateCaptor.capture());

        // 월요일(2026-03-16) 기준 전일인 일요일(2026-03-15)로 조회해야 함
        LocalDate expectedDate = LocalDate.of(2026, 3, 15);
        assertThat(dateCaptor.getValue()).isEqualTo(expectedDate);
    }

    @Test
    @DisplayName("전일 환율 조회 - 화요일 timestamp이면 월요일(전일) 환율 조회")
    void exchangeRateJob_fetchesMondayRate_whenTimestampIsTuesday() throws Exception {
        when(koreaEximProvider.fetchExchangeRates(any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        // 2026-03-17 (Tuesday) 07:15 KST - 실제 스케줄 실행 시각 시뮬레이션
        ZonedDateTime tuesdayDateTime = ZonedDateTime.of(2026, 3, 17, 7, 15, 0, 0, KST);
        long timestamp = tuesdayDateTime.toInstant().toEpochMilli();

        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", timestamp)
                .toJobParameters();

        jobLauncher.run(exchangeRateJob, jobParameters);

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(koreaEximProvider).fetchExchangeRates(dateCaptor.capture());

        // 화요일(2026-03-17) 기준 전일인 월요일(2026-03-16)로 조회해야 함
        LocalDate expectedDate = LocalDate.of(2026, 3, 16);
        assertThat(dateCaptor.getValue()).isEqualTo(expectedDate);
    }

    @Test
    @DisplayName("timestamp 없을 때 현재 시각 기준 전일 날짜로 조회")
    void exchangeRateJob_usesCurrentTimeWhenNoTimestamp() throws Exception {
        when(koreaEximProvider.fetchExchangeRates(any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        // timestamp 파라미터 없이 실행
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis()) // unique run id
                .toJobParameters();

        JobExecution jobExecution = jobLauncher.run(exchangeRateJob, jobParameters);

        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(koreaEximProvider).fetchExchangeRates(dateCaptor.capture());

        // 현재 날짜 기준 전일이어야 함
        LocalDate yesterday = LocalDate.now(KST).minusDays(1);
        assertThat(dateCaptor.getValue()).isEqualTo(yesterday);
    }

    @Test
    @DisplayName("전일 날짜가 당일 날짜와 다른 날짜임을 검증 - minusDays(1) 적용 확인")
    void exchangeRateJob_targetDateIsNotSameDayAsTimestamp() throws Exception {
        when(koreaEximProvider.fetchExchangeRates(any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        // Use a different second to avoid duplicate job instance (unique parameters required)
        ZonedDateTime targetDateTime = ZonedDateTime.of(2026, 3, 13, 12, 0, 2, 0, KST);
        long timestamp = targetDateTime.toInstant().toEpochMilli();

        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", timestamp)
                .toJobParameters();

        jobLauncher.run(exchangeRateJob, jobParameters);

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(koreaEximProvider).fetchExchangeRates(dateCaptor.capture());

        LocalDate timestampDate = targetDateTime.toLocalDate(); // 2026-03-13
        LocalDate fetchedDate = dateCaptor.getValue();          // 2026-03-12

        // 가져온 날짜는 timestamp 날짜와 달라야 한다 (전일)
        assertThat(fetchedDate).isNotEqualTo(timestampDate);
        assertThat(fetchedDate).isEqualTo(timestampDate.minusDays(1));
    }
}