package com.porcana.batch.service;

import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.AssetClass;
import com.porcana.domain.asset.entity.DividendCategory;
import com.porcana.domain.asset.entity.DividendFrequency;
import com.porcana.domain.asset.entity.Sector;
import com.porcana.domain.asset.entity.UniverseTag;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class KrAssetDescriptionGenerator {

    public String generate(Asset asset) {
        List<String> sentences = new ArrayList<>();
        sentences.add(buildIntro(asset));

        String exposure = buildExposure(asset);
        if (exposure != null) {
            sentences.add(exposure);
        }

        String dividend = buildDividend(asset);
        if (dividend != null) {
            sentences.add(dividend);
        }

        String role = buildRole(asset);
        if (role != null) {
            sentences.add(role);
        }

        return String.join(" ", sentences);
    }

    private String buildIntro(Asset asset) {
        if (asset.getType() == Asset.AssetType.ETF) {
            return String.format("%s(%s)는 한국 시장에 상장된 ETF입니다.", asset.getName(), asset.getSymbol());
        }

        String sectorLabel = sectorLabel(asset.getSector());
        if (sectorLabel != null) {
            return String.format("%s(%s)는 한국 시장에 상장된 %s 종목입니다.",
                    asset.getName(), asset.getSymbol(), sectorLabel);
        }

        return String.format("%s(%s)는 한국 시장에 상장된 개별 종목입니다.", asset.getName(), asset.getSymbol());
    }

    private String buildExposure(Asset asset) {
        if (asset.getType() == Asset.AssetType.ETF) {
            String assetClassLabel = assetClassLabel(asset.getAssetClass());
            if (assetClassLabel != null) {
                return String.format("이 ETF는 %s 테마에 초점을 둔 상품으로 볼 수 있습니다.", assetClassLabel);
            }
        }

        List<String> tags = new ArrayList<>();
        if (asset.getUniverseTags().contains(UniverseTag.KOSPI200)) {
            tags.add("KOSPI 200");
        }
        if (asset.getUniverseTags().contains(UniverseTag.KOSDAQ150)) {
            tags.add("KOSDAQ 150");
        }

        if (!tags.isEmpty()) {
            return String.format("주요 편입 지수 기준으로는 %s 구성 종목에 포함됩니다.", String.join("와 ", tags));
        }

        return null;
    }

    private String buildDividend(Asset asset) {
        if (Boolean.FALSE.equals(asset.getDividendAvailable())
                || asset.getDividendCategory() == DividendCategory.NONE
                || asset.getDividendFrequency() == DividendFrequency.NONE) {
            return "최근 기준으로 뚜렷한 배당 매력보다는 사업 성장성과 주가 흐름이 더 중요한 자산으로 볼 수 있습니다.";
        }

        if (Boolean.TRUE.equals(asset.getDividendAvailable())) {
            String category = dividendCategoryLabel(asset.getDividendCategory());
            String frequency = dividendFrequencyLabel(asset.getDividendFrequency());

            if (category != null && frequency != null) {
                return String.format("배당 측면에서는 %s 성향이 있으며, %s 배당 흐름을 기대하는 투자자에게 참고할 만합니다.",
                        category, frequency);
            }

            if (category != null) {
                return String.format("배당 측면에서는 %s 성향으로 해석할 수 있습니다.", category);
            }
        }

        return null;
    }

    private String buildRole(Asset asset) {
        if (asset.getType() == Asset.AssetType.ETF) {
            return "포트폴리오에서는 개별 종목 리스크를 낮추면서 특정 지수나 테마 노출을 더하는 역할로 활용할 수 있습니다.";
        }

        if (asset.getCurrentRiskLevel() != null) {
            if (asset.getCurrentRiskLevel() <= 2) {
                return "상대적으로 변동성이 낮은 편이라면 포트폴리오 안정성을 보완하는 카드로 볼 수 있습니다.";
            }
            if (asset.getCurrentRiskLevel() >= 4) {
                return "변동성이 큰 편이라면 수익 기회와 함께 가격 흔들림도 감수해야 하는 성장형 카드에 가깝습니다.";
            }
        }

        return "포트폴리오에서는 섹터 분산과 개별 기업 노출을 조절하는 용도로 해석할 수 있습니다.";
    }

    private String sectorLabel(Sector sector) {
        return sector == null ? null : sector.getKoreanName();
    }

    private String assetClassLabel(AssetClass assetClass) {
        if (assetClass == null) {
            return null;
        }

        return switch (assetClass) {
            case EQUITY_INDEX -> "대표 지수";
            case SECTOR -> "특정 섹터";
            case DIVIDEND -> "배당";
            case BOND -> "채권";
            case COMMODITY -> "원자재";
        };
    }

    private String dividendCategoryLabel(DividendCategory category) {
        if (category == null) {
            return null;
        }

        return switch (category) {
            case HIGH_DIVIDEND -> "고배당";
            case DIVIDEND_GROWTH -> "배당 성장";
            case HAS_DIVIDEND -> "배당 지급";
            case REIT_LIKE -> "리츠형";
            case COVERED_CALL_LIKE -> "커버드콜형";
            case NONE, UNKNOWN -> null;
        };
    }

    private String dividendFrequencyLabel(DividendFrequency frequency) {
        if (frequency == null) {
            return null;
        }

        return switch (frequency) {
            case MONTHLY -> "월별";
            case QUARTERLY -> "분기";
            case SEMI_ANNUAL -> "반기";
            case ANNUAL -> "연간";
            case IRREGULAR -> "비정기";
            case NONE, UNKNOWN -> null;
        };
    }
}
