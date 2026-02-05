package com.porcana.domain.asset.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class AssetChartResponse {
    private final String assetId;
    private final String range;
    private final List<ChartPoint> points;

    @Getter
    @Builder
    public static class ChartPoint {
        private final LocalDate date;
        private final Double open;
        private final Double high;
        private final Double low;
        private final Double close;
        private final Long volume;
    }
}