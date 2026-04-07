package com.porcana.domain.admin.dto.request;

import com.porcana.domain.asset.entity.DividendCategory;
import com.porcana.domain.asset.entity.DividendDataStatus;
import com.porcana.domain.asset.entity.DividendFrequency;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request DTO for updating asset dividend information
 */
public record UpdateAssetDividendRequest(
        Boolean dividendAvailable,
        BigDecimal dividendYield,
        DividendFrequency dividendFrequency,
        DividendCategory dividendCategory,
        DividendDataStatus dividendDataStatus,
        LocalDate lastDividendDate
) {}
