package com.porcana.domain.portfolio.service.deck;

import com.porcana.domain.portfolio.dto.deck.DeckMetrics;
import com.porcana.domain.portfolio.entity.deck.DeckSignal;
import com.porcana.domain.portfolio.entity.deck.DeckStyle;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

/**
 * 덱 해설 텍스트 템플릿 생성기
 * DeckStyle과 DeckSignal 기반으로 문장 조합
 */
@UtilityClass
public class DeckTextTemplate {

    /**
     * 한 줄 요약 생성
     */
    public static String generateSummary(DeckStyle style, DeckMetrics metrics, List<DeckSignal> signals) {
        return switch (style) {
            case AGGRESSIVE -> "고위험 고수익을 추구하는 공격적인 포트폴리오입니다";
            case BALANCED -> {
                if (signals.contains(DeckSignal.DIVIDEND_TILT)) {
                    yield "성장과 배당을 균형있게 추구하는 포트폴리오입니다";
                }
                if (signals.contains(DeckSignal.HIGH_INCOME)) {
                    yield "성장주 중심이지만 일부 배당 자산을 포함한 균형형 포트폴리오입니다";
                }
                yield "위험과 수익의 균형을 추구하는 포트폴리오입니다";
            }
            case DEFENSIVE -> {
                if (metrics.getIncomeCoreWeight() >= 20) {
                    yield "안정적인 배당과 방어적 자산 중심의 보수적 포트폴리오입니다";
                }
                yield "안정적인 자산 중심의 방어적 포트폴리오입니다";
            }
            case CASHFLOW -> {
                if (metrics.getIncomeCoreWeight() >= 40) {
                    yield "배당 수익을 핵심으로 하는 인컴 중심 포트폴리오입니다";
                }
                yield "배당 수익을 중심으로 현금흐름을 추구하는 포트폴리오입니다";
            }
            case GROWTH -> {
                if (signals.contains(DeckSignal.DIVIDEND_TILT)) {
                    yield "성장을 추구하면서 배당도 고려한 포트폴리오입니다";
                }
                yield "중장기 성장을 추구하는 성장 중심 포트폴리오입니다";
            }
            case THEMATIC -> "특정 섹터/테마에 집중한 테마형 포트폴리오입니다";
        };
    }

    /**
     * 강점 생성 (최대 2개)
     */
    public static List<String> generateStrengths(DeckStyle style, DeckMetrics metrics, List<DeckSignal> signals) {
        List<String> strengths = new ArrayList<>();

        // 스타일별 강점
        switch (style) {
            case AGGRESSIVE -> strengths.add("상승장에서 높은 수익 잠재력");
            case BALANCED -> strengths.add("성장성과 안정성을 동시에 추구");
            case DEFENSIVE -> strengths.add("하락장에서 손실 방어 가능");
            case CASHFLOW -> strengths.add("정기적인 배당 수익 기대");
            case GROWTH -> strengths.add("중장기 자본 성장 추구");
            case THEMATIC -> strengths.add("특정 트렌드 수혜 기대");
        }

        // 배당 관련 강점
        if (metrics.getIncomeCoreWeight() >= 30) {
            strengths.add("인컴 자산이 안정적인 현금흐름 제공");
        } else if (signals.contains(DeckSignal.DIVIDEND_TILT)) {
            strengths.add("배당 자산이 포트폴리오 안정성 강화");
        } else if (signals.contains(DeckSignal.HIGH_INCOME)) {
            strengths.add("배당 자산이 변동성 완충 역할 수행");
        }

        // 분산 효과 관련 강점
        if (metrics.getHasDividendWeight() >= 40 && metrics.getGrowthWeight() >= 30) {
            strengths.add("성장과 배당의 균형잡힌 구성");
        }
        if (metrics.getEtfWeight() >= 50) {
            strengths.add("ETF 비중이 높아 분산 효과 우수");
        }
        if (metrics.getAssetCount() >= 8) {
            strengths.add("충분한 종목 수로 분산 투자 실현");
        }
        if (!signals.contains(DeckSignal.MARKET_IMBALANCE) && !signals.contains(DeckSignal.MARKET_CONCENTRATION)) {
            if (metrics.getUsWeight() >= 30 && metrics.getKrWeight() >= 30) {
                strengths.add("글로벌 분산 투자 구조");
            }
        }

        // 헤지 자산 강점
        if (metrics.getHedgeWeight() >= 15) {
            strengths.add("헤지 자산이 포트폴리오 리스크 분산");
        }

        return strengths.stream().limit(2).toList();
    }

    /**
     * 약점 생성 (최대 2개)
     */
    public static List<String> generateWeaknesses(DeckStyle style, DeckMetrics metrics, List<DeckSignal> signals) {
        List<String> weaknesses = new ArrayList<>();

        // 시그널 기반 약점
        if (signals.contains(DeckSignal.SECTOR_CONCENTRATION)) {
            weaknesses.add("특정 섹터 편중으로 분산 효과 제한");
        }
        if (signals.contains(DeckSignal.HIGH_RISK_EXPOSURE)) {
            weaknesses.add("고위험 자산 비중으로 변동성 존재");
        }
        if (signals.contains(DeckSignal.LOW_DEFENSE)) {
            weaknesses.add("방어적 자산 부족으로 하락장 취약");
        }
        if (signals.contains(DeckSignal.HIGH_CONCENTRATION)) {
            weaknesses.add("상위 종목 집중으로 개별 종목 리스크 존재");
        }
        if (signals.contains(DeckSignal.MARKET_CONCENTRATION)) {
            weaknesses.add("특정 시장 과도 집중으로 지역 리스크 높음");
        } else if (signals.contains(DeckSignal.MARKET_IMBALANCE)) {
            weaknesses.add("특정 시장 편중으로 지역 리스크 존재");
        }
        if (signals.contains(DeckSignal.LOW_DIVERSIFICATION)) {
            weaknesses.add("종목 수 부족으로 분산 효과 제한");
        }

        // 배당 관련 약점 (스타일별로 다르게)
        if (style == DeckStyle.CASHFLOW && metrics.getGrowthWeight() < 20) {
            weaknesses.add("성장 자산 부족으로 자본 증식 제한");
        }
        if (signals.contains(DeckSignal.DIVIDEND_TILT) && style == DeckStyle.GROWTH) {
            // 성장형인데 배당 성향이 있으면 성장 잠재력 제한 가능
            weaknesses.add("배당 자산 비중으로 성장 잠재력 일부 제한");
        }

        // 스타일별 기본 약점
        if (weaknesses.isEmpty()) {
            switch (style) {
                case AGGRESSIVE -> weaknesses.add("하락장에서 큰 손실 가능성");
                case DEFENSIVE -> weaknesses.add("상승장에서 수익률 제한");
                case CASHFLOW -> weaknesses.add("성장 잠재력 제한");
                case THEMATIC -> weaknesses.add("테마 쇠퇴 시 리스크");
                default -> weaknesses.add("시장 상황에 따른 변동성 존재");
            }
        }

        return weaknesses.stream().limit(2).toList();
    }

    /**
     * 운영 팁 생성 (최대 2개)
     */
    public static List<String> generateTips(DeckStyle style, DeckMetrics metrics, List<DeckSignal> signals) {
        List<String> tips = new ArrayList<>();

        // 시그널 기반 팁
        if (signals.contains(DeckSignal.SECTOR_CONCENTRATION)) {
            tips.add("다른 섹터 자산 추가로 분산 효과 강화 고려");
        }
        if (signals.contains(DeckSignal.HIGH_RISK_EXPOSURE)) {
            tips.add("성장주 과열 시 일부 이익 실현 고려");
        }
        if (signals.contains(DeckSignal.LOW_DEFENSE)) {
            tips.add("방어적 자산(채권, 배당주) 추가 검토");
        }
        if (signals.contains(DeckSignal.HIGH_CONCENTRATION)) {
            tips.add("비중 조절로 개별 종목 리스크 분산 검토");
        }
        if (signals.contains(DeckSignal.MARKET_CONCENTRATION)) {
            tips.add("다른 시장 자산 추가로 지역 분산 권장");
        }
        if (signals.contains(DeckSignal.LOW_DIVERSIFICATION)) {
            tips.add("종목 추가로 분산 효과 강화 권장");
        }

        // 배당 관련 팁
        if (signals.contains(DeckSignal.DIVIDEND_TILT) || signals.contains(DeckSignal.HIGH_INCOME)) {
            if (style != DeckStyle.CASHFLOW) {
                tips.add("배당 재투자로 복리 효과 극대화");
            }
        }
        if (style == DeckStyle.CASHFLOW) {
            if (metrics.getIncomeCoreWeight() < 40) {
                tips.add("고배당 ETF 추가로 인컴 강화 고려");
            } else {
                tips.add("배당 재투자로 복리 효과 극대화");
            }
        }

        // 스타일별 기본 팁
        if (tips.isEmpty()) {
            switch (style) {
                case AGGRESSIVE -> tips.add("주기적인 리밸런싱으로 리스크 관리 필요");
                case BALANCED -> tips.add("목표 비중 유지를 위한 정기 리밸런싱 권장");
                case DEFENSIVE -> tips.add("금리 변동에 따른 채권 비중 조정 검토");
                case CASHFLOW -> tips.add("배당 재투자로 복리 효과 극대화");
                case GROWTH -> tips.add("장기 보유 관점에서 단기 변동성 인내");
                case THEMATIC -> tips.add("테마 트렌드 모니터링 필요");
            }
        }

        // 일반적인 팁 추가
        if (tips.size() < 2 && metrics.getAssetCount() >= 5) {
            tips.add("분기별 리밸런싱으로 목표 비중 유지 권장");
        }

        return tips.stream().limit(2).toList();
    }

    /**
     * 투자자 적합성 생성
     */
    public static String generateInvestorFit(DeckStyle style, DeckMetrics metrics) {
        return switch (style) {
            case AGGRESSIVE -> "고수익을 추구하며 높은 변동성을 감내할 수 있는 공격적 투자자";
            case BALANCED -> {
                if (metrics.getHasDividendWeight() >= 30) {
                    yield "성장과 배당을 균형있게 추구하는 중립적 투자자";
                }
                yield "중장기 성장과 안정성을 동시에 추구하는 투자자";
            }
            case DEFENSIVE -> {
                if (metrics.getIncomeCoreWeight() >= 20) {
                    yield "안정적인 배당 수익과 원금 보존을 중시하는 보수적 투자자";
                }
                yield "원금 보존을 최우선으로 하는 안정 추구 투자자";
            }
            case CASHFLOW -> {
                if (metrics.getIncomeCoreWeight() >= 40) {
                    yield "정기적인 배당 수익을 최우선으로 하는 인컴 중심 투자자";
                }
                yield "정기적인 현금흐름이 필요한 인컴 추구 투자자";
            }
            case GROWTH -> {
                if (metrics.getDividendFocusedWeight() >= 20) {
                    yield "성장을 추구하되 배당 수익도 고려하는 균형 지향 투자자";
                }
                yield "장기적 자본 성장을 목표로 하는 성장 지향 투자자";
            }
            case THEMATIC -> "특정 섹터/트렌드에 확신을 가진 집중 투자자";
        };
    }
}
