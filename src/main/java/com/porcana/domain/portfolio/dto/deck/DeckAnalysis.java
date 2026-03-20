package com.porcana.domain.portfolio.dto.deck;

import com.porcana.domain.portfolio.entity.deck.DeckSignal;
import com.porcana.domain.portfolio.entity.deck.DeckStyle;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 덱 분석 결과 내부 Value Object
 */
@Getter
@Builder
public class DeckAnalysis {
    private final DeckMetrics metrics;
    private final DeckStyle style;
    private final List<DeckSignal> signals;

    // 텍스트 해설
    private final String summary;
    private final List<String> strengths;
    private final List<String> weaknesses;
    private final List<String> tips;
    private final String investorFit;
}
