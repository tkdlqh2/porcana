package com.porcana.batch.job;

import com.porcana.batch.provider.exchangerate.KoreaEximProvider;
import com.porcana.domain.exchangerate.ExchangeRateRepository;
import com.porcana.domain.exchangerate.entity.ExchangeRate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Daily exchange rate update batch job
 * Fetches and updates exchange rates from Korea Exim Bank API
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ExchangeRateBatchJob {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final KoreaEximProvider koreaEximProvider;
    private final ExchangeRateRepository exchangeRateRepository;

    @Bean
    public Job exchangeRateJob() {
        return new JobBuilder("exchangeRateJob", jobRepository)
                .start(updateExchangeRatesStep())
                .build();
    }

    /**
     * Update daily exchange rates
     * Fetches latest exchange rates and saves to database
     */
    @Bean
    public Step updateExchangeRatesStep() {
        return new StepBuilder("updateExchangeRatesStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Starting daily exchange rate update");

                    // Get timestamp from JobParameters
                    Map<String, Object> jobParameters = chunkContext.getStepContext().getJobParameters();
                    Long timestamp = (Long) jobParameters.get("timestamp");

                    // If timestamp is null, use current time
                    if (timestamp == null) {
                        timestamp = System.currentTimeMillis();
                        log.info("timestamp parameter is null, using current time: {}", timestamp);
                    }

                    // Convert timestamp to LocalDate (KST timezone)
                    LocalDate targetDate = Instant.ofEpochMilli(timestamp)
                            .atZone(ZoneId.of("Asia/Seoul"))
                            .toLocalDate();

                    log.info("Using target date from JobParameters: {}", targetDate);

                    try {
                        List<ExchangeRate> exchangeRates = koreaEximProvider.fetchExchangeRates(targetDate);
                        log.info("Fetched {} exchange rates for date: {}", exchangeRates.size(), targetDate);

                        int created = 0;
                        int updated = 0;
                        int skipped = 0;

                        for (ExchangeRate exchangeRate : exchangeRates) {
                            try {
                                boolean exists = exchangeRateRepository.existsByCurrencyCodeAndExchangeDate(
                                        exchangeRate.getCurrencyCode(), exchangeRate.getExchangeDate());

                                if (exists) {
                                    // Update existing
                                    ExchangeRate existing = exchangeRateRepository
                                            .findByCurrencyCodeAndExchangeDate(
                                                    exchangeRate.getCurrencyCode(),
                                                    exchangeRate.getExchangeDate())
                                            .orElseThrow();

                                    // Note: ExchangeRate is immutable, so we delete and recreate
                                    exchangeRateRepository.delete(existing);
                                    exchangeRateRepository.save(exchangeRate);
                                    updated++;
                                    log.debug("Updated exchange rate: {} = {} KRW",
                                            exchangeRate.getCurrencyCode(), exchangeRate.getBaseRate());
                                } else {
                                    // Create new
                                    exchangeRateRepository.save(exchangeRate);
                                    created++;
                                    log.debug("Created exchange rate: {} = {} KRW",
                                            exchangeRate.getCurrencyCode(), exchangeRate.getBaseRate());
                                }

                            } catch (Exception e) {
                                log.warn("Failed to save exchange rate for {}: {}",
                                        exchangeRate.getCurrencyCode(), e.getMessage());
                                skipped++;
                            }
                        }

                        log.info("Exchange rate update complete: {} created, {} updated, {} skipped",
                                created, updated, skipped);

                    } catch (Exception e) {
                        log.error("Failed to update exchange rates", e);
                        throw new RuntimeException("Exchange rate update failed", e);
                    }

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}
