package com.porcana.domain.exchangerate.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 환율 정보 엔티티
 * 한국수출입은행 API로부터 수집한 환율 데이터 저장
 */
@Entity
@Table(name = "exchange_rates", indexes = {
        @Index(name = "idx_exchange_rate_currency_date", columnList = "currency_code, exchange_date", unique = true),
        @Index(name = "idx_exchange_rate_date", columnList = "exchange_date")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * 통화 코드 (USD, JPY, EUR, CNY 등)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CurrencyCode currencyCode;

    /**
     * 통화명 (미국 달러, 일본 엔 등)
     */
    @Column(nullable = false, length = 100)
    private String currencyName;

    /**
     * 매매기준율 (KRW 기준)
     * 예: 1 USD = 1,200 KRW
     */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal baseRate;

    /**
     * 송금 받을 때 환율 (살 때)
     */
    @Column(precision = 15, scale = 2)
    private BigDecimal buyRate;

    /**
     * 송금 보낼 때 환율 (팔 때)
     */
    @Column(precision = 15, scale = 2)
    private BigDecimal sellRate;

    /**
     * 환율 기준일
     */
    @Column(nullable = false)
    private LocalDate exchangeDate;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ExchangeRate(CurrencyCode currencyCode, String currencyName, BigDecimal baseRate,
                        BigDecimal buyRate, BigDecimal sellRate, LocalDate exchangeDate) {
        this.currencyCode = currencyCode;
        this.currencyName = currencyName;
        this.baseRate = baseRate;
        this.buyRate = buyRate;
        this.sellRate = sellRate;
        this.exchangeDate = exchangeDate;
    }
}
