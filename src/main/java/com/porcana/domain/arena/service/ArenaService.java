package com.porcana.domain.arena.service;

import com.porcana.domain.arena.command.CreateSessionCommand;
import com.porcana.domain.arena.command.PickAssetCommand;
import com.porcana.domain.arena.command.PickRiskProfileCommand;
import com.porcana.domain.arena.command.PickSectorsCommand;
import com.porcana.domain.arena.dto.*;
import com.porcana.domain.arena.entity.*;
import com.porcana.domain.arena.repository.ArenaRoundRepository;
import com.porcana.domain.arena.repository.ArenaSessionRepository;
import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.Sector;
import com.porcana.domain.portfolio.entity.Portfolio;
import com.porcana.domain.portfolio.entity.PortfolioAsset;
import com.porcana.domain.portfolio.repository.PortfolioAssetRepository;
import com.porcana.domain.portfolio.repository.PortfolioRepository;
import com.porcana.global.exception.ForbiddenException;
import com.porcana.global.exception.InvalidOperationException;
import com.porcana.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArenaService {

    private final ArenaSessionRepository sessionRepository;
    private final ArenaRoundRepository roundRepository;
    private final AssetRepository assetRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final PortfolioRepository portfolioRepository;
    private final AssetRecommendationService recommendationService;

    /**
     * Create a new arena session for portfolio drafting
     */
    @Transactional
    public CreateSessionResponse createSession(CreateSessionCommand command) {
        // Validate portfolio exists and user owns it
        Portfolio portfolio = portfolioRepository.findById(command.getPortfolioId())
                .orElseThrow(() -> new NotFoundException("Portfolio not found"));

        if (!portfolio.getUserId().equals(command.getUserId())) {
            throw new ForbiddenException("Not authorized to access this portfolio");
        }

        // Check if session already exists for this portfolio
        Optional<ArenaSession> existing = sessionRepository
                .findByPortfolioIdAndStatus(command.getPortfolioId(), SessionStatus.IN_PROGRESS);

        if (existing.isPresent()) {
            // Return existing session instead of creating duplicate
            return mapToCreateSessionResponse(existing.get());
        }

        // Create new session
        ArenaSession session = ArenaSession.builder()
                .portfolioId(command.getPortfolioId())
                .userId(command.getUserId())
                .status(SessionStatus.IN_PROGRESS)
                .currentRound(1)
                .totalRounds(12)
                .build();

        ArenaSession saved = sessionRepository.save(session);

        return mapToCreateSessionResponse(saved);
    }

    /**
     * Get session details
     */
    public SessionResponse getSession(UUID sessionId, UUID userId) {
        ArenaSession session = getSessionAndValidateOwnership(sessionId, userId);

        // Get selected asset IDs from completed asset rounds
        List<ArenaRound> assetRounds = roundRepository.findBySessionIdAndRoundType(sessionId, RoundType.ASSET);
        List<UUID> selectedAssetIds = assetRounds.stream()
                .map(ArenaRound::getSelectedAssetId)
                .filter(id -> id != null)
                .collect(Collectors.toList());

        return SessionResponse.builder()
                .sessionId(session.getId())
                .portfolioId(session.getPortfolioId())
                .status(session.getStatus())
                .currentRound(session.getCurrentRound())
                .totalRounds(session.getTotalRounds())
                .riskProfile(session.getRiskProfile())
                .selectedSectors(session.getSelectedSectors())
                .selectedAssetIds(selectedAssetIds)
                .build();
    }

    /**
     * Get current round data
     */
    @Transactional
    public RoundResponse getCurrentRound(UUID sessionId, UUID userId) {
        ArenaSession session = getSessionAndValidateOwnership(sessionId, userId);

        if (session.getStatus() == SessionStatus.COMPLETED) {
            throw new InvalidOperationException("Session already completed");
        }

        int currentRound = session.getCurrentRound();

        // Round 1: Risk Profile Selection
        if (currentRound == 1) {
            return buildRiskProfileRound(session);
        }

        // Round 2: Sector Selection
        if (currentRound == 2) {
            return buildSectorRound(session);
        }

        // Rounds 3-12: Asset Selection
        return buildAssetRound(session);
    }

    /**
     * Pick risk profile (Round 1)
     */
    @Transactional
    public PickResponse pickRiskProfile(UUID sessionId, UUID userId, PickRiskProfileCommand command) {
        ArenaSession session = getSessionAndValidateOwnership(sessionId, userId);

        if (session.getCurrentRound() != 1) {
            throw new InvalidOperationException("Risk profile can only be selected in round 1");
        }

        // Save selection to session
        session.setRiskProfile(command.getRiskProfile());
        session.setCurrentRound(2);
        sessionRepository.save(session);

        return PickResponse.builder()
                .sessionId(session.getId())
                .status(session.getStatus())
                .currentRound(2)
                .picked(command.getRiskProfile())
                .build();
    }

    /**
     * Pick sectors (Round 2)
     */
    @Transactional
    public PickResponse pickSectors(UUID sessionId, UUID userId, PickSectorsCommand command) {
        ArenaSession session = getSessionAndValidateOwnership(sessionId, userId);

        if (session.getCurrentRound() != 2) {
            throw new InvalidOperationException("Sectors can only be selected in round 2");
        }

        // Validate 0-3 sectors
        if (command.getSectors().size() > 3) {
            throw new IllegalArgumentException("Must select 0-3 sectors");
        }

        // Validate no duplicate sectors
        long distinctCount = command.getSectors().stream().distinct().count();
        if (distinctCount != command.getSectors().size()) {
            throw new IllegalArgumentException("Duplicate sectors are not allowed");
        }

        // Validate each sector has enough assets (at least 3 for one round)
        for (Sector sector : command.getSectors()) {
            if (!recommendationService.hasEnoughAssets(sector, 3)) {
                throw new IllegalArgumentException(
                        String.format("Sector %s has insufficient assets", sector.getDescription())
                );
            }
        }

        // Save selections
        session.setSelectedSectors(command.getSectors());
        session.setCurrentRound(3);
        sessionRepository.save(session);

        return PickResponse.builder()
                .sessionId(session.getId())
                .status(session.getStatus())
                .currentRound(3)
                .picked(command.getSectors())
                .build();
    }

    /**
     * Pick asset (Rounds 3-12)
     */
    @Transactional
    public PickResponse pickAsset(UUID sessionId, UUID userId, PickAssetCommand command) {
        ArenaSession session = getSessionAndValidateOwnership(sessionId, userId);

        int currentRound = session.getCurrentRound();

        if (currentRound < 3 || currentRound > 12) {
            throw new InvalidOperationException("Assets can only be selected in rounds 3-12");
        }

        // Get the round entity (should already exist with presented choices)
        ArenaRound round = roundRepository
                .findBySessionIdAndRoundNumber(session.getId(), currentRound)
                .orElseThrow(() -> new NotFoundException("Round not found"));

        // Validate picked asset was in the presented choices
        if (!round.getPresentedAssetIds().contains(command.getPickedAssetId())) {
            throw new IllegalArgumentException("Invalid asset selection - asset was not in presented choices");
        }

        // Save selection
        round.setSelectedAssetId(command.getPickedAssetId());
        round.setPickedAt(LocalDateTime.now());
        roundRepository.save(round);

        // Advance round
        boolean isLastRound = (currentRound == 12);

        if (isLastRound) {
            // Complete session and update portfolio
            completeSession(session);

            return PickResponse.builder()
                    .sessionId(session.getId())
                    .status(SessionStatus.COMPLETED)
                    .currentRound(12)
                    .picked(command.getPickedAssetId())
                    .build();
        } else {
            session.setCurrentRound(currentRound + 1);
            sessionRepository.save(session);

            return PickResponse.builder()
                    .sessionId(session.getId())
                    .status(SessionStatus.IN_PROGRESS)
                    .currentRound(currentRound + 1)
                    .picked(command.getPickedAssetId())
                    .build();
        }
    }

    /**
     * Build risk profile round response (Round 1)
     */
    private RoundResponse buildRiskProfileRound(ArenaSession session) {
        List<RiskProfileRoundResponse.RiskProfileOption> options = new ArrayList<>();

        for (RiskProfile profile : RiskProfile.values()) {
            options.add(RiskProfileRoundResponse.RiskProfileOption.builder()
                    .value(profile)
                    .displayName(profile.getDisplayName())
                    .description(profile.getDescription())
                    .build());
        }

        return RiskProfileRoundResponse.builder()
                .sessionId(session.getId())
                .round(1)
                .roundType(RoundType.RISK_PROFILE)
                .options(options)
                .build();
    }

    /**
     * Build sector round response (Round 2)
     */
    private RoundResponse buildSectorRound(ArenaSession session) {
        List<SectorRoundResponse.SectorOption> sectorOptions = new ArrayList<>();

        for (Sector sector : Sector.values()) {
            Integer assetCount = recommendationService.getAssetCount(sector);

            // Only include sectors with enough assets (at least 3 for one round)
            if (assetCount >= 3) {
                sectorOptions.add(SectorRoundResponse.SectorOption.builder()
                        .value(sector)
                        .displayName(sector.getDescription())
                        .assetCount(assetCount)
                        .build());
            }
        }

        return SectorRoundResponse.builder()
                .sessionId(session.getId())
                .round(2)
                .roundType(RoundType.SECTOR)
                .sectors(sectorOptions)
                .minSelection(0)
                .maxSelection(3)
                .build();
    }

    /**
     * Build asset round response (Rounds 3-12)
     */
    private RoundResponse buildAssetRound(ArenaSession session) {
        int currentRound = session.getCurrentRound();

        // Check if round already exists (user refreshing page - idempotent)
        Optional<ArenaRound> existingRound = roundRepository
                .findBySessionIdAndRoundNumber(session.getId(), currentRound);

        List<Asset> assets;

        if (existingRound.isPresent()) {
            // Return same assets as before
            List<UUID> assetIds = existingRound.get().getPresentedAssetIds();
            assets = assetRepository.findAllById(assetIds);
        } else {
            // Generate new recommendations using weighted selection logic
            assets = recommendationService.generateRoundOptions(session, currentRound);

            // Save the round with presented choices
            ArenaRound newRound = ArenaRound.builder()
                    .sessionId(session.getId())
                    .roundNumber(currentRound)
                    .roundType(RoundType.ASSET)
                    .presentedAssetIds(assets.stream().map(Asset::getId).collect(Collectors.toList()))
                    .build();

            roundRepository.save(newRound);
        }

        // Map to response
        List<AssetRoundResponse.AssetOption> options = assets.stream()
                .map(asset -> AssetRoundResponse.AssetOption.builder()
                        .assetId(asset.getId())
                        .ticker(asset.getSymbol())
                        .name(asset.getName())
                        .sector(asset.getSector() != null ? asset.getSector() : null)
                        .tags(asset.getUniverseTags().stream()
                                .map(Enum::name)
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());

        return AssetRoundResponse.builder()
                .sessionId(session.getId())
                .round(currentRound)
                .roundType(RoundType.ASSET)
                .assets(options)
                .build();
    }

    /**
     * Complete session and create portfolio assets
     */
    @Transactional
    private void completeSession(ArenaSession session) {
        // Mark session as completed
        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        sessionRepository.save(session);

        // Get all selected assets from asset rounds (Rounds 3-12)
        List<ArenaRound> assetRounds = roundRepository.findBySessionIdAndRoundType(
                session.getId(), RoundType.ASSET);

        List<UUID> selectedAssetIds = assetRounds.stream()
                .map(ArenaRound::getSelectedAssetId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Create PortfolioAsset entries with equal weighting
        // 10 assets = 10% each
        if (!selectedAssetIds.isEmpty()) {
            BigDecimal equalWeight = new BigDecimal("100.00")
                    .divide(new BigDecimal(selectedAssetIds.size()), 2, BigDecimal.ROUND_HALF_UP);

            for (UUID assetId : selectedAssetIds) {
                PortfolioAsset portfolioAsset = PortfolioAsset.builder()
                        .portfolioId(session.getPortfolioId())
                        .assetId(assetId)
                        .weightPct(equalWeight)
                        .build();

                portfolioAssetRepository.save(portfolioAsset);
            }
        }
    }

    /**
     * Get session and validate ownership
     */
    private ArenaSession getSessionAndValidateOwnership(UUID sessionId, UUID userId) {
        ArenaSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ForbiddenException("Session not found or access denied"));

        return session;
    }

    /**
     * Map entity to create session response
     */
    private CreateSessionResponse mapToCreateSessionResponse(ArenaSession session) {
        return CreateSessionResponse.builder()
                .sessionId(session.getId())
                .portfolioId(session.getPortfolioId())
                .status(session.getStatus())
                .currentRound(session.getCurrentRound())
                .build();
    }
}
