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

import java.time.LocalDate;
import java.util.List;

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

                    // Use today's date for exchange rate fetch
                    LocalDate today = LocalDate.now();

                    try {
                        List<ExchangeRate> exchangeRates = koreaEximProvider.fetchExchangeRates(today);
                        log.info("Fetched {} exchange rates for date: {}", exchangeRates.size(), today);

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
