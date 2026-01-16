package com.porcana.domain.exchangerate;

import com.porcana.domain.exchangerate.entity.CurrencyCode;
import com.porcana.domain.exchangerate.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {

    /**
     * Find exchange rate by currency code and exchange date
     * Used for upsert operations in batch jobs
     */
    Optional<ExchangeRate> findByCurrencyCodeAndExchangeDate(CurrencyCode currencyCode, LocalDate exchangeDate);

    /**
     * Check if exchange rate exists for a specific currency and date
     */
    boolean existsByCurrencyCodeAndExchangeDate(CurrencyCode currencyCode, LocalDate exchangeDate);

    /**
     * Find all exchange rates for a specific date
     * Used to retrieve daily exchange rates
     */
    List<ExchangeRate> findByExchangeDate(LocalDate exchangeDate);

    /**
     * Find latest exchange rate for a currency
     * Used to get the most recent rate for a currency
     */
    Optional<ExchangeRate> findTopByCurrencyCodeOrderByExchangeDateDesc(CurrencyCode currencyCode);

    /**
     * Find all exchange rates for a currency within a date range
     * Used for historical exchange rate queries
     */
    List<ExchangeRate> findByCurrencyCodeAndExchangeDateBetweenOrderByExchangeDateDesc(
            CurrencyCode currencyCode, LocalDate startDate, LocalDate endDate);
}
