package com.porcana.domain.arena.service;

import com.porcana.domain.arena.entity.ArenaRound;
import com.porcana.domain.arena.entity.ArenaSession;
import com.porcana.domain.arena.entity.RoundType;
import com.porcana.domain.arena.repository.ArenaRoundRepository;
import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.Sector;
import com.porcana.global.exception.InsufficientAssetsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetRecommendationService {

    private final AssetRepository assetRepository;
    private final ArenaRoundRepository roundRepository;

    /**
     * Recommend assets based on session's selected sectors
     *
     * @param session Arena session with selected sectors
     * @param count Number of assets to recommend
     * @return List of recommended assets
     * @throws IllegalStateException if not enough assets available
     */
    public List<Asset> recommendAssets(ArenaSession session, int count) {
        List<Sector> selectedSectors = session.getSelectedSectors();

        if (selectedSectors == null || selectedSectors.isEmpty()) {
            throw new InsufficientAssetsException("No sectors selected in session");
        }

        // Get already selected asset IDs to avoid duplicates
        List<UUID> alreadySelectedIds = getAlreadySelectedAssetIds(session.getId());

        // Filter assets by selected sectors and active status
        List<Asset> candidatePool = assetRepository.findBySectorInAndActiveTrue(selectedSectors);

        // Remove already selected assets
        candidatePool = candidatePool.stream()
                .filter(asset -> !alreadySelectedIds.contains(asset.getId()))
                .collect(Collectors.toList());

        // Ensure we have enough candidates
        if (candidatePool.size() < count) {
            throw new InsufficientAssetsException(
                    String.format("Not enough assets in selected sectors. Available: %d, Required: %d",
                            candidatePool.size(), count)
            );
        }

        // Randomly select 'count' assets
        Collections.shuffle(candidatePool);
        return new ArrayList<>(candidatePool.subList(0, count));
    }

    /**
     * Get list of asset IDs already selected in previous rounds
     */
    private List<UUID> getAlreadySelectedAssetIds(UUID sessionId) {
        List<ArenaRound> assetRounds = roundRepository.findBySessionIdAndRoundType(sessionId, RoundType.ASSET);

        return assetRounds.stream()
                .map(ArenaRound::getSelectedAssetId)
                .filter(id -> id != null)
                .collect(Collectors.toList());
    }

    /**
     * Validate that a sector has enough active assets for arena rounds
     *
     * @param sector Sector to validate
     * @param minimumCount Minimum number of assets required
     * @return true if sector has enough assets
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
