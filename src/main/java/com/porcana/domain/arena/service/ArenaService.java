package com.porcana.domain.arena.service;

import com.porcana.domain.arena.command.CreateSessionCommand;
import com.porcana.domain.arena.command.PickAssetCommand;
import com.porcana.domain.arena.command.PickPreferencesCommand;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final com.porcana.domain.portfolio.service.PortfolioSnapshotService portfolioSnapshotService;

    /**
     * Create a new arena session for portfolio drafting (supports both user and guest)
     */
    @Transactional
    public CreateSessionResponse createSession(CreateSessionCommand command, UUID userId, UUID guestSessionId) {
        // Validate portfolio exists and ownership
        Portfolio portfolio = portfolioRepository.findById(command.getPortfolioId())
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));

        // Validate ownership
        if (userId != null) {
            if (!portfolio.isOwnedByUser(userId)) {
                throw new ForbiddenException("Not authorized to access this portfolio");
            }
        } else if (guestSessionId != null) {
            if (!portfolio.isOwnedByGuestSession(guestSessionId)) {
                throw new ForbiddenException("Not authorized to access this portfolio");
            }
        } else {
            throw new IllegalArgumentException("Either userId or guestSessionId must be provided");
        }

        // Check if session already exists for this portfolio
        Optional<ArenaSession> existing = sessionRepository
                .findByPortfolioIdAndStatus(command.getPortfolioId(), SessionStatus.IN_PROGRESS);

        if (existing.isPresent()) {
            // Return existing session instead of creating duplicate
            return mapToCreateSessionResponse(existing.get());
        }

        // Create new session (Round 0 = Pre Round, Rounds 1-10 = Asset selection)
        ArenaSession session;
        if (userId != null) {
            session = ArenaSession.createForUser(command.getPortfolioId(), userId);
        } else {
            session = ArenaSession.createForGuest(command.getPortfolioId(), guestSessionId);
        }

        ArenaSession saved = sessionRepository.save(session);

        return mapToCreateSessionResponse(saved);
    }

    /**
     * Legacy method for backward compatibility (authenticated users only)
     */
    @Transactional
    public CreateSessionResponse createSession(CreateSessionCommand command) {
        return createSession(command, command.getUserId(), null);
    }

    /**
     * Get session details (supports both user and guest)
     */
    public SessionResponse getSession(UUID sessionId, UUID userId, UUID guestSessionId) {
        ArenaSession session = getSessionAndValidateOwnership(sessionId, userId, guestSessionId);

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
     * Legacy method for backward compatibility (authenticated users only)
     */
    public SessionResponse getSession(UUID sessionId, UUID userId) {
        return getSession(sessionId, userId, null);
    }

    /**
     * Get current round data (supports both user and guest)
     */
    @Transactional
    public RoundResponse getCurrentRound(UUID sessionId, UUID userId, UUID guestSessionId) {
        ArenaSession session = getSessionAndValidateOwnership(sessionId, userId, guestSessionId);

        if (session.getStatus() == SessionStatus.COMPLETED) {
            throw new InvalidOperationException("Session already completed");
        }

        int currentRound = session.getCurrentRound();

        // Round 0: Pre Round (Risk Profile + Sector Selection)
        if (currentRound == 0) {
            return buildPreRound(session);
        }

        // Rounds 1-10: Asset Selection
        return buildAssetRound(session);
    }

    /**
     * Legacy method for backward compatibility (authenticated users only)
     */
    @Transactional
    public RoundResponse getCurrentRound(UUID sessionId, UUID userId) {
        return getCurrentRound(sessionId, userId, null);
    }

    /**
     * Pick preferences (Round 0: Risk Profile + Sectors, supports both user and guest)
     */
    @Transactional
    public PickResponse pickPreferences(UUID sessionId, UUID userId, UUID guestSessionId, PickPreferencesCommand command) {
        ArenaSession session = getSessionAndValidateOwnership(sessionId, userId, guestSessionId);

        if (session.getCurrentRound() != 0) {
            throw new InvalidOperationException("Preferences can only be selected in round 0 (Pre Round)");
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
        session.setRiskProfile(command.getRiskProfile());
        session.setSelectedSectors(command.getSectors());
        session.setCurrentRound(1);  // Move to Round 1 (first asset selection)
        sessionRepository.save(session);

        return PickResponse.builder()
                .sessionId(session.getId())
                .status(session.getStatus())
                .currentRound(1)
                .picked(java.util.Map.of(
                        "riskProfile", command.getRiskProfile(),
                        "sectors", command.getSectors()
                ))
                .build();
    }

    /**
     * Legacy method for backward compatibility (authenticated users only)
     */
    @Transactional
    public PickResponse pickPreferences(UUID sessionId, UUID userId, PickPreferencesCommand command) {
        return pickPreferences(sessionId, userId, null, command);
    }

    /**
     * Pick asset (Rounds 1-10, supports both user and guest)
     */
    @Transactional
    public PickResponse pickAsset(UUID sessionId, UUID userId, UUID guestSessionId, PickAssetCommand command) {
        ArenaSession session = getSessionAndValidateOwnership(sessionId, userId, guestSessionId);

        int currentRound = session.getCurrentRound();

        if (currentRound < 1 || currentRound > 10) {
            throw new InvalidOperationException("Assets can only be selected in rounds 1-10");
        }

        // Get the round entity (should already exist with presented choices)
        ArenaRound round = roundRepository
                .findBySessionIdAndRoundNumber(session.getId(), currentRound)
                .orElseThrow(() -> new IllegalArgumentException("Round not found"));

        // Validate picked asset was in the presented choices
        if (!round.getPresentedAssetIds().contains(command.getPickedAssetId())) {
            throw new IllegalArgumentException("Invalid asset selection - asset was not in presented choices");
        }

        // Save selection
        round.setSelectedAssetId(command.getPickedAssetId());
        round.setPickedAt(LocalDateTime.now());
        roundRepository.save(round);

        // Advance round
        boolean isLastRound = (currentRound == 10);

        if (isLastRound) {
            // Complete session and update portfolio
            completeSession(session);

            return PickResponse.builder()
                    .sessionId(session.getId())
                    .status(SessionStatus.COMPLETED)
                    .currentRound(10)
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
     * Build pre round response (Round 0: Risk Profile + Sector Selection)
     */
    private RoundResponse buildPreRound(ArenaSession session) {
        // Risk Profile options
        List<PreRoundResponse.RiskProfileOption> riskProfileOptions = new ArrayList<>();
        for (RiskProfile profile : RiskProfile.values()) {
            riskProfileOptions.add(PreRoundResponse.RiskProfileOption.builder()
                    .value(profile)
                    .displayName(profile.getDisplayName())
                    .description(profile.getDescription())
                    .build());
        }

        // Sector options
        List<PreRoundResponse.SectorOption> sectorOptions = new ArrayList<>();
        for (Sector sector : Sector.values()) {
            Integer assetCount = recommendationService.getAssetCount(sector);

            // Only include sectors with enough assets (at least 3 for one round)
            if (assetCount >= 3) {
                sectorOptions.add(PreRoundResponse.SectorOption.builder()
                        .value(sector)
                        .displayName(sector.getDescription())
                        .assetCount(assetCount)
                        .build());
            }
        }

        return PreRoundResponse.builder()
                .sessionId(session.getId())
                .round(0)
                .roundType(RoundType.PRE_ROUND)
                .riskProfileOptions(riskProfileOptions)
                .sectorOptions(sectorOptions)
                .minSectorSelection(0)
                .maxSectorSelection(3)
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
                        .sector(asset.getSector())
                        .market(asset.getMarket())
                        .assetClass(asset.getAssetClass())
                        .impactHint(generateImpactHint(asset))
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
     * Generate impact hint for asset selection
     */
    private String generateImpactHint(Asset asset) {
        String role;

        switch (asset.getType()) {
            case ETF -> {
                if (asset.getAssetClass() == null) {
                    role = "자산 보완";
                } else {
                    role = switch (asset.getAssetClass()) {
                        case EQUITY_INDEX -> "분산 효과";
                        case DIVIDEND -> "배당 기여";
                        case BOND -> "방어 역할";
                        default -> "자산 보완";
                    };
                }
            }
            case STOCK -> {
                if (asset.getSector() == null) {
                    role = "포트폴리오 보강";
                } else {
                    role = switch (asset.getSector()) {
                        case INFORMATION_TECHNOLOGY -> "성장 비중 ↑";
                        case FINANCIALS -> "경기 민감";
                        case UTILITIES -> "방어적";
                        default -> "포트폴리오 보강";
                    };
                }
            }
            default -> role = "포트폴리오 보강";
        }

        String risk;
        if (asset.getCurrentRiskLevel() == null) {
            risk = "균형";
        } else if (asset.getCurrentRiskLevel() >= 4) {
            risk = "변동성 ↑";
        } else if (asset.getCurrentRiskLevel() <= 2) {
            risk = "안정성 ↑";
        } else {
            risk = "균형";
        }

        return role + " · " + risk;
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
                .toList();

        // Create PortfolioAsset entries with equal weighting
        // 10 assets = 10% each
        if (!selectedAssetIds.isEmpty()) {
            BigDecimal equalWeight = new BigDecimal("100.00")
                    .divide(new BigDecimal(selectedAssetIds.size()), 2, RoundingMode.HALF_UP);

            java.util.Map<UUID, BigDecimal> assetWeights = new java.util.HashMap<>();

            for (UUID assetId : selectedAssetIds) {
                PortfolioAsset portfolioAsset = PortfolioAsset.builder()
                        .portfolioId(session.getPortfolioId())
                        .assetId(assetId)
                        .weightPct(equalWeight)
                        .build();

                portfolioAssetRepository.save(portfolioAsset);
                assetWeights.put(assetId, equalWeight);
            }

            // Create initial snapshot
            java.time.LocalDate today = java.time.LocalDate.now();
            portfolioSnapshotService.createSnapshotWithAssets(
                    session.getPortfolioId(),
                    assetWeights,
                    today,
                    "Initial portfolio creation via Arena"
            );
        }
    }

    /**
     * Get session and validate ownership
     */
    /**
     * Get session and validate ownership (supports both user and guest)
     */
    private ArenaSession getSessionAndValidateOwnership(UUID sessionId, UUID userId, UUID guestSessionId) {
        if (userId != null) {
            return sessionRepository.findByIdAndUserId(sessionId, userId)
                    .orElseThrow(() -> new ForbiddenException("Session not found or access denied"));
        } else if (guestSessionId != null) {
            return sessionRepository.findByIdAndGuestSessionId(sessionId, guestSessionId)
                    .orElseThrow(() -> new ForbiddenException("Session not found or access denied"));
        } else {
            throw new IllegalArgumentException("Either userId or guestSessionId must be provided");
        }
    }

    /**
     * Legacy method for backward compatibility (authenticated users only)
     */
    private ArenaSession getSessionAndValidateOwnership(UUID sessionId, UUID userId) {
        return getSessionAndValidateOwnership(sessionId, userId, null);
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
