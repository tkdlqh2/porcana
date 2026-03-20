package com.porcana.domain.asset.service.personality;

import com.porcana.domain.asset.dto.personality.AssetPersonality;
import com.porcana.domain.asset.entity.*;
import com.porcana.domain.asset.entity.personality.*;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;

/**
 * 자산 성격 판별 규칙 엔진
 * Asset 엔티티로부터 AssetPersonality를 계산
 *
 * 순차 계산 순서:
 * 1. exposureType - 자산이 무엇에 노출되는지
 * 2. dividendProfile - 배당 성격 판단
 * 3. role - 포트폴리오 내 역할
 * 4. persona - UX 페르소나
 */
@UtilityClass
public class AssetPersonalityRuleEngine {

    // 배당 수익률 임계값 상수 (소수 기준: 0.04 = 4%)
    private static final BigDecimal THRESHOLD_INCOME_CORE = new BigDecimal("0.0400");      // 0.04 (4%) 이상 → INCOME_CORE
    private static final BigDecimal THRESHOLD_DIVIDEND_FOCUSED = new BigDecimal("0.0200"); // 0.02 (2%) 이상 → DIVIDEND_FOCUSED

    /**
     * Asset으로부터 성격 전체를 순차적으로 계산
     */
    public static AssetPersonality compute(Asset asset) {
        ExposureType exposureType = determineExposureType(asset);
        DividendProfile dividendProfile = determineDividendProfile(asset, exposureType);
        Role role = determineRole(asset, exposureType, dividendProfile);
        Persona persona = determinePersona(asset, role, dividendProfile);

        return AssetPersonality.builder()
                .role(role)
                .riskLevel(asset.getCurrentRiskLevel())
                .exposureType(exposureType)
                .persona(persona)
                .dividendProfile(dividendProfile)
                .build();
    }

    /**
     * 투자 노출 유형 판별
     */
    private static ExposureType determineExposureType(Asset asset) {
        if (asset.getType() == Asset.AssetType.ETF && asset.getAssetClass() != null) {
            return switch (asset.getAssetClass()) {
                case EQUITY_INDEX -> ExposureType.BROAD_INDEX;
                case SECTOR -> ExposureType.SECTOR;
                case DIVIDEND -> ExposureType.DIVIDEND;
                case BOND -> ExposureType.BOND;
                case COMMODITY -> ExposureType.COMMODITY;
            };
        }
        return ExposureType.SINGLE_STOCK;
    }

    /**
     * 배당 프로필 판별
     * 배당 데이터 기반으로 해석 (순차 계산 2단계)
     */
    private static DividendProfile determineDividendProfile(Asset asset, ExposureType exposureType) {
        // 1. ETF 배당 클래스는 가장 강한 신호
        if (asset.getType() == Asset.AssetType.ETF && asset.getAssetClass() == AssetClass.DIVIDEND) {
            return DividendProfile.INCOME_CORE;
        }

        // 2. 배당 데이터 없으면 보수적으로 판단
        if (!hasReliableDividendData(asset)) {
            // 배당 데이터 없어도 기본 휴리스틱 적용
            return determineDividendProfileByHeuristic(asset);
        }

        BigDecimal yield = defaultZero(asset.getDividendYield());
        DividendCategory category = asset.getDividendCategory();
        DividendFrequency frequency = asset.getDividendFrequency();

        // 3. 인컴 코어 판별
        if (category == DividendCategory.HIGH_DIVIDEND
                || category == DividendCategory.REIT_LIKE
                || category == DividendCategory.COVERED_CALL_LIKE) {
            return DividendProfile.INCOME_CORE;
        }

        if (yield.compareTo(THRESHOLD_INCOME_CORE) >= 0
                && (frequency == DividendFrequency.MONTHLY || frequency == DividendFrequency.QUARTERLY)) {
            return DividendProfile.INCOME_CORE;
        }

        // 4. 배당 중심 판별
        if (category == DividendCategory.DIVIDEND_GROWTH) {
            return DividendProfile.DIVIDEND_FOCUSED;
        }

        if (yield.compareTo(THRESHOLD_DIVIDEND_FOCUSED) >= 0
                && isDividendFriendlySector(asset.getSector())) {
            return DividendProfile.DIVIDEND_FOCUSED;
        }

        // 5. 배당 있음
        if (Boolean.TRUE.equals(asset.getDividendAvailable())
                || yield.compareTo(BigDecimal.ZERO) > 0) {
            return DividendProfile.HAS_DIVIDEND;
        }

        return DividendProfile.NONE;
    }

    /**
     * 배당 데이터 없을 때 휴리스틱 기반 판별 (기존 로직 유지)
     */
    private static DividendProfile determineDividendProfileByHeuristic(Asset asset) {
        // Stock: 안정적인 섹터 + 낮은 리스크 → 배당 가능성
        if (asset.getType() == Asset.AssetType.STOCK) {
            Integer risk = asset.getCurrentRiskLevel();
            Sector sector = asset.getSector();

            // 배당 친화적 섹터 (유틸리티, 필수소비재, 헬스케어, 금융)
            if (isDividendFriendlySector(sector) && risk != null && risk <= 2) {
                return DividendProfile.DIVIDEND_FOCUSED;
            }

            // 대형 안정주 (낮은 리스크)
            if (risk != null && risk <= 2) {
                return DividendProfile.HAS_DIVIDEND;
            }
        }

        return DividendProfile.NONE;
    }

    /**
     * 포트폴리오 내 역할 판별 (순차 계산 3단계)
     */
    private static Role determineRole(Asset asset, ExposureType exposureType, DividendProfile dividendProfile) {
        Integer risk = asset.getCurrentRiskLevel();

        // ETF: assetClass 기반 판별
        if (asset.getType() == Asset.AssetType.ETF && asset.getAssetClass() != null) {
            return switch (asset.getAssetClass()) {
                case EQUITY_INDEX -> Role.CORE;
                case DIVIDEND -> Role.INCOME;
                case BOND -> {
                    if (risk != null && risk <= 2) yield Role.DEFENSIVE;
                    yield Role.HEDGE;
                }
                case COMMODITY -> Role.HEDGE;
                case SECTOR -> Role.SATELLITE;
            };
        }

        // STOCK: dividendProfile 반영
        if (dividendProfile == DividendProfile.INCOME_CORE) {
            return Role.INCOME;
        }

        if (dividendProfile == DividendProfile.DIVIDEND_FOCUSED && isDefensiveSector(asset.getSector())) {
            return Role.DEFENSIVE;
        }

        if (risk == null) {
            if (isDefensiveSector(asset.getSector())) {
                return Role.DEFENSIVE;
            }
            return Role.CORE;
        }

        if (risk >= 4) {
            return Role.GROWTH;
        }

        if (risk == 3) {
            if (isDefensiveSector(asset.getSector())) {
                return Role.DEFENSIVE;
            }
            if (dividendProfile == DividendProfile.DIVIDEND_FOCUSED) {
                return Role.CORE;
            }
            return Role.GROWTH;
        }

        // risk <= 2
        if (dividendProfile == DividendProfile.DIVIDEND_FOCUSED) {
            return Role.INCOME;
        }
        if (isDefensiveSector(asset.getSector())) {
            return Role.DEFENSIVE;
        }
        return Role.CORE;
    }

    /**
     * UX 페르소나 판별 (순차 계산 4단계)
     */
    private static Persona determinePersona(Asset asset, Role role, DividendProfile dividendProfile) {
        Integer risk = asset.getCurrentRiskLevel();

        if (role == Role.INCOME) {
            return Persona.CASHFLOW;
        }
        if (role == Role.DEFENSIVE || role == Role.HEDGE) {
            return Persona.DEFENSIVE;
        }
        if (role == Role.SATELLITE) {
            return Persona.THEMATIC;
        }

        if (risk == null) {
            return Persona.BALANCED;
        }

        if (risk >= 4) {
            return Persona.AGGRESSIVE;
        }
        if (risk == 3) {
            return Persona.GROWTH;
        }
        if (risk <= 2) {
            if (dividendProfile == DividendProfile.HAS_DIVIDEND) {
                return Persona.BALANCED;
            }
            return Persona.STABLE;
        }

        return Persona.BALANCED;
    }

    /**
     * 신뢰할 수 있는 배당 데이터가 있는지 확인
     */
    private static boolean hasReliableDividendData(Asset asset) {
        return asset.getDividendDataStatus() == DividendDataStatus.VERIFIED
                || asset.getDividendDataStatus() == DividendDataStatus.PARTIAL;
    }

    /**
     * BigDecimal null 처리
     */
    private static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 방어적 섹터인지 확인
     */
    private static boolean isDefensiveSector(Sector sector) {
        if (sector == null) return false;
        return sector == Sector.HEALTH_CARE
                || sector == Sector.CONSUMER_STAPLES
                || sector == Sector.UTILITIES;
    }

    /**
     * 배당 친화적 섹터인지 확인
     */
    private static boolean isDividendFriendlySector(Sector sector) {
        if (sector == null) return false;
        return sector == Sector.UTILITIES
                || sector == Sector.CONSUMER_STAPLES
                || sector == Sector.HEALTH_CARE
                || sector == Sector.FINANCIALS
                || sector == Sector.REAL_ESTATE;
    }
}
