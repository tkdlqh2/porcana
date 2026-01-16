package com.porcana.domain.arena.service;

import com.porcana.domain.arena.entity.ArenaRound;
import com.porcana.domain.arena.entity.ArenaSession;
import com.porcana.domain.arena.entity.RiskProfile;
import com.porcana.domain.arena.entity.RoundType;
import com.porcana.domain.arena.repository.ArenaRoundRepository;
import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.Sector;
import com.porcana.global.exception.InsufficientAssetsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Asset recommendation service with weighted selection logic
 * Based on risk profile, sector preferences, and diversity constraints
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetRecommendationService {

    private final AssetRepository assetRepository;
    private final ArenaRoundRepository roundRepository;

    private static final int WILD_COUNT = 1;
    private static final int ENTRY_COUNT = 3;
    private static final int NORMAL_COUNT = ENTRY_COUNT - WILD_COUNT;
    private static final int MAX_RETRY = 5;
    private static final double MIN_WEIGHT = 0.0001;

    /**
     * Generate round options (3 assets) based on session context
     * Optimized to load only necessary assets from DB
     */
    public List<Asset> generateRoundOptions(ArenaSession session, int roundNo) {
        RiskProfile riskProfile = session.getRiskProfile();
        List<Sector> preferredSectors = session.getSelectedSectors();
        Set<UUID> deckAssetIds = getDeckAssetIds(session.getId());
        Set<UUID> shownAssetIds = getShownAssetIds(session.getId());
        Set<UUID> excludeIds = new HashSet<>();
        excludeIds.addAll(deckAssetIds);
        excludeIds.addAll(shownAssetIds);

        // Load only preferred sector assets (for normal picks)
        List<Asset> preferredCandidates = assetRepository.findBySectorInAndActiveTrue(preferredSectors);
        preferredCandidates = filterExcludedAssets(preferredCandidates, excludeIds);

        // Load wild candidates (other sectors) - for diversity
        List<Asset> wildCandidates = assetRepository.findBySectorNotInAndActiveTrue(preferredSectors);
        wildCandidates = filterExcludedAssets(wildCandidates, excludeIds);

        List<Asset> picked = new ArrayList<>();

        // 1) Normal picks: sector/risk preference + diversity penalty
        for (int i = 0; i < NORMAL_COUNT && !preferredCandidates.isEmpty(); i++) {
            Asset next = weightedPickOne(preferredCandidates, riskProfile, new HashSet<>(preferredSectors), picked, true);
            picked.add(next);
            preferredCandidates.remove(next);
            wildCandidates.remove(next); // Also remove from wild pool
        }

        // 2) Wild pick: ignore sector preference, diversity only
        if (picked.size() < 3 && !wildCandidates.isEmpty()) {
            Asset wild = weightedPickOne(wildCandidates, riskProfile, new HashSet<>(preferredSectors), picked, false);
            picked.add(wild);
            wildCandidates.remove(wild);
        }

        // Fallback: if not enough picked, relax constraints
        if (picked.size() < 3) {
            log.warn("Not enough candidates. Relaxing shown constraint. SessionId: {}", session.getId());
            picked = rerollWithRelaxation(preferredSectors, deckAssetIds, riskProfile);
        }

        // 3) Diversity check with retry
        int retry = 0;
        while (retry < MAX_RETRY && !isDiverseEnough(picked)) {
            log.debug("Diversity check failed. Retrying... Attempt: {}/{}", retry + 1, MAX_RETRY);
            picked = rerollWithRelaxation(preferredSectors, deckAssetIds, riskProfile);
            retry++;
        }

        if (!isDiverseEnough(picked)) {
            log.warn("Could not achieve diversity after {} retries. SessionId: {}, RoundNo: {}",
                    MAX_RETRY, session.getId(), roundNo);
        }

        return picked;
    }

    /**
     * Filter out excluded asset IDs
     */
    private List<Asset> filterExcludedAssets(List<Asset> assets, Set<UUID> excludeIds) {
        if (excludeIds.isEmpty()) {
            return new ArrayList<>(assets);
        }
        return assets.stream()
                .filter(a -> !excludeIds.contains(a.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Weighted pick with diversity penalty
     */
    private Asset weightedPickOne(List<Asset> candidates, RiskProfile riskProfile,
                                   Set<Sector> preferredSectors, List<Asset> alreadyPicked,
                                   boolean useSectorPreference) {
        Map<UUID, Double> weights = new HashMap<>();

        for (Asset asset : candidates) {
            double w = 1.0;
            w *= riskWeight(riskProfile, asset.getCurrentRiskLevel());

            if (useSectorPreference) {
                w *= sectorWeight(preferredSectors, asset.getSector());
            }

            w *= typeWeight(asset.getType());
            w *= diversityPenalty(asset, alreadyPicked);

            // Safety: prevent zero weight
            w = Math.max(w, MIN_WEIGHT);

            weights.put(asset.getId(), w);
        }

        return sampleByWeight(candidates, weights);
    }

    /**
     * Risk weight based on profile
     */
    private double riskWeight(RiskProfile profile, Integer riskScore) {
        if (profile == null || riskScore == null) {
            return 1.0;
        }

        return switch (profile) {
            case SAFE -> {
                if (riskScore <= 2) yield 1.4;
                if (riskScore == 3) yield 1.0;
                yield 0.6; // 4~5
            }
            case BALANCED -> {
                if (riskScore >= 2 && riskScore <= 4) yield 1.2;
                yield 0.8; // 1 or 5
            }
            case AGGRESSIVE -> {
                if (riskScore >= 4) yield 1.4;
                if (riskScore == 3) yield 1.0;
                yield 0.7; // 1~2
            }
        };
    }

    /**
     * Sector weight based on preferences
     */
    private double sectorWeight(Set<Sector> preferredSectors, Sector sector) {
        if (preferredSectors == null || preferredSectors.isEmpty() || sector == null) {
            return 1.0;
        }
        return preferredSectors.contains(sector) ? 1.5 : 1.0;
    }

    /**
     * Type weight (slight preference for ETF)
     */
    private double typeWeight(Asset.AssetType assetType) {
        if (assetType == null) {
            return 1.0;
        }
        return assetType == Asset.AssetType.ETF ? 1.05 : 1.0;
    }

    /**
     * Diversity penalty: reduce probability if same sector or risk band
     */
    private double diversityPenalty(Asset candidate, List<Asset> alreadyPicked) {
        if (alreadyPicked == null || alreadyPicked.isEmpty()) {
            return 1.0;
        }

        double penalty = 1.0;

        // Same sector penalty
        Sector candidateSector = candidate.getSector();
        if (candidateSector != null && alreadyPicked.stream()
                .anyMatch(p -> candidateSector.equals(p.getSector()))) {
            penalty *= 0.35;
        }

        // Same risk band penalty
        String candidateBand = riskBand(candidate.getCurrentRiskLevel());
        if (alreadyPicked.stream()
                .anyMatch(p -> candidateBand.equals(riskBand(p.getCurrentRiskLevel())))) {
            penalty *= 0.70;
        }

        return penalty;
    }

    /**
     * Risk band categorization
     */
    private String riskBand(Integer riskScore) {
        if (riskScore == null) {
            return "UNKNOWN";
        }
        if (riskScore <= 2) return "LOW";
        if (riskScore == 3) return "MID";
        return "HIGH";
    }

    /**
     * Diversity condition: at least 2 sectors and 2 risk bands
     */
    private boolean isDiverseEnough(List<Asset> picked) {
        if (picked == null || picked.size() < 3) {
            return false;
        }

        long distinctSectors = picked.stream()
                .map(Asset::getSector)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        if (distinctSectors < 2) {
            return false;
        }

        long distinctBands = picked.stream()
                .map(Asset::getCurrentRiskLevel)
                .map(this::riskBand)
                .distinct()
                .count();

        return distinctBands >= 2;
    }

    /**
     * Weighted random sampling
     */
    private Asset sampleByWeight(List<Asset> candidates, Map<UUID, Double> weights) {
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        double r = Math.random() * total;
        double acc = 0;

        for (Asset asset : candidates) {
            acc += weights.get(asset.getId());
            if (acc >= r) {
                return asset;
            }
        }

        // Fallback to last candidate
        return candidates.get(candidates.size() - 1);
    }

    /**
     * Reroll with relaxed constraints (ignore shown constraint)
     */
    private List<Asset> rerollWithRelaxation(List<Sector> preferredSectors, Set<UUID> deckAssetIds,
                                              RiskProfile riskProfile) {
        // Relax shown constraint - only exclude deck assets
        List<Asset> preferredCandidates = assetRepository.findBySectorInAndActiveTrue(preferredSectors);
        preferredCandidates = filterExcludedAssets(preferredCandidates, deckAssetIds);

        List<Asset> wildCandidates = assetRepository.findBySectorNotInAndActiveTrue(preferredSectors);
        wildCandidates = filterExcludedAssets(wildCandidates, deckAssetIds);

        List<Asset> picked = new ArrayList<>();

        // Try to pick 2 normal + 1 wild
        while (picked.size() < NORMAL_COUNT && !preferredCandidates.isEmpty()) {
            Asset next = weightedPickOne(preferredCandidates, riskProfile, new HashSet<>(preferredSectors), picked, true);
            picked.add(next);
            preferredCandidates.remove(next);
            wildCandidates.remove(next);
        }

        if (picked.size() < 3 && !wildCandidates.isEmpty()) {
            Asset wild = weightedPickOne(wildCandidates, riskProfile, new HashSet<>(preferredSectors), picked, false);
            picked.add(wild);
        }

        return picked;
    }

    /**
     * Get already selected asset IDs (in deck)
     */
    private Set<UUID> getDeckAssetIds(UUID sessionId) {
        List<ArenaRound> assetRounds = roundRepository.findBySessionIdAndRoundType(sessionId, RoundType.ASSET);

        return assetRounds.stream()
                .map(ArenaRound::getSelectedAssetId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Get already shown asset IDs (presented in previous rounds)
     */
    private Set<UUID> getShownAssetIds(UUID sessionId) {
        List<ArenaRound> assetRounds = roundRepository.findBySessionIdAndRoundType(sessionId, RoundType.ASSET);

        return assetRounds.stream()
                .map(ArenaRound::getPresentedAssetIds)
                .flatMap(List::stream)
                .collect(Collectors.toSet());
    }

    /**
     * Validate that a sector has enough active assets for arena rounds
     */
    public boolean hasEnoughAssets(Sector sector, int minimumCount) {
        Integer count = assetRepository.countBySectorAndActiveTrue(sector);
        return count != null && count >= minimumCount;
    }

    /**
     * Get count of active assets in a sector
     */
    public Integer getAssetCount(Sector sector) {
        Integer count = assetRepository.countBySectorAndActiveTrue(sector);
        return count != null ? count : 0;
    }
}