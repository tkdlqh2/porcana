package com.porcana.domain.portfolio.service;

import com.porcana.domain.portfolio.entity.PortfolioAsset;
import com.porcana.domain.portfolio.entity.PortfolioSnapshot;
import com.porcana.domain.portfolio.entity.PortfolioSnapshotAsset;
import com.porcana.domain.portfolio.repository.PortfolioAssetRepository;
import com.porcana.domain.portfolio.repository.PortfolioSnapshotAssetRepository;
import com.porcana.domain.portfolio.repository.PortfolioSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 포트폴리오 스냅샷 관리 서비스
 * 자산 구성 변경 시점을 기록하고 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioSnapshotService {

    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioSnapshotAssetRepository snapshotAssetRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;

    /**
     * 포트폴리오의 현재 자산 구성으로 스냅샷 생성
     *
     * @param portfolioId  포트폴리오 ID
     * @param effectiveDate 스냅샷 유효 시작일
     * @param note         스냅샷 메모
     * @return 생성된 스냅샷
     */
    @Transactional
    public PortfolioSnapshot createSnapshot(UUID portfolioId, LocalDate effectiveDate, String note) {
        // 동일한 날짜에 이미 스냅샷이 있는지 확인
        boolean exists = snapshotRepository.existsByPortfolioIdAndEffectiveDate(portfolioId, effectiveDate);
        if (exists) {
            log.warn("Snapshot already exists for portfolio {} on {}", portfolioId, effectiveDate);
            throw new IllegalStateException(
                    String.format("Snapshot already exists for this date: %s", effectiveDate)
            );
        }

        // 현재 포트폴리오 자산 구성 조회
        List<PortfolioAsset> currentAssets = portfolioAssetRepository.findByPortfolioId(portfolioId);
        if (currentAssets.isEmpty()) {
            throw new IllegalStateException("Cannot create snapshot: portfolio has no assets");
        }

        // 비중 합계 검증
        BigDecimal totalWeight = currentAssets.stream()
                .map(PortfolioAsset::getWeightPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalWeight.compareTo(BigDecimal.valueOf(100.0)) != 0) {
            log.warn("Portfolio {} total weight is {} instead of 100%", portfolioId, totalWeight);
        }

        // 스냅샷 생성
        PortfolioSnapshot snapshot = PortfolioSnapshot.builder()
                .portfolioId(portfolioId)
                .effectiveDate(effectiveDate)
                .note(note)
                .build();

        PortfolioSnapshot savedSnapshot = snapshotRepository.save(snapshot);

        // 스냅샷 자산 구성 저장
        for (PortfolioAsset asset : currentAssets) {
            PortfolioSnapshotAsset snapshotAsset = PortfolioSnapshotAsset.builder()
                    .snapshotId(savedSnapshot.getId())
                    .assetId(asset.getAssetId())
                    .weight(asset.getWeightPct())
                    .build();

            snapshotAssetRepository.save(snapshotAsset);
        }

        log.info("Created snapshot {} for portfolio {} with {} assets on {}",
                savedSnapshot.getId(), portfolioId, currentAssets.size(), effectiveDate);

        return savedSnapshot;
    }

    /**
     * 특정 자산 구성으로 스냅샷 생성 (Arena 완료 시 사용)
     *
     * @param portfolioId  포트폴리오 ID
     * @param assetWeights 자산 ID -> 비중 맵
     * @param effectiveDate 스냅샷 유효 시작일
     * @param note         스냅샷 메모
     * @return 생성된 스냅샷
     */
    @Transactional
    public PortfolioSnapshot createSnapshotWithAssets(
            UUID portfolioId,
            Map<UUID, BigDecimal> assetWeights,
            LocalDate effectiveDate,
            String note) {

        if (assetWeights.isEmpty()) {
            throw new IllegalArgumentException("Cannot create snapshot: asset weights are empty");
        }

        // 비중 합계 검증
        BigDecimal totalWeight = assetWeights.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalWeight.compareTo(BigDecimal.valueOf(100.0)) != 0) {
            log.warn("Portfolio {} total weight is {} instead of 100%", portfolioId, totalWeight);
        }

        // 동일한 날짜에 이미 스냅샷이 있는지 확인
        Optional<PortfolioSnapshot> existingSnapshotOpt =
                snapshotRepository.findByPortfolioIdAndEffectiveDate(portfolioId, effectiveDate);

        PortfolioSnapshot savedSnapshot;

        if (existingSnapshotOpt.isPresent()) {
            // 기존 스냅샷이 있으면 업데이트
            savedSnapshot = existingSnapshotOpt.get();
            log.info("Updating existing snapshot {} for portfolio {} on {}",
                    savedSnapshot.getId(), portfolioId, effectiveDate);

            // 기존 스냅샷 자산 구성 삭제 (즉시 실행)
            List<PortfolioSnapshotAsset> existingAssets = snapshotAssetRepository.findBySnapshotId(savedSnapshot.getId());
            snapshotAssetRepository.deleteAllInBatch(existingAssets);
            log.debug("Deleted {} existing snapshot assets", existingAssets.size());

            // 노트 업데이트 (새로운 노트가 제공된 경우)
            if (note != null && !note.isBlank()) {
                // PortfolioSnapshot에 updateNote 메서드 추가 필요
                // savedSnapshot.updateNote(note);
            }
        } else {
            // 새 스냅샷 생성
            PortfolioSnapshot snapshot = PortfolioSnapshot.builder()
                    .portfolioId(portfolioId)
                    .effectiveDate(effectiveDate)
                    .note(note)
                    .build();

            savedSnapshot = snapshotRepository.save(snapshot);
            log.info("Created new snapshot {} for portfolio {} on {}",
                    savedSnapshot.getId(), portfolioId, effectiveDate);
        }

        // 스냅샷 자산 구성 저장 (새 비중으로)
        for (Map.Entry<UUID, BigDecimal> entry : assetWeights.entrySet()) {
            PortfolioSnapshotAsset snapshotAsset = PortfolioSnapshotAsset.builder()
                    .snapshotId(savedSnapshot.getId())
                    .assetId(entry.getKey())
                    .weight(entry.getValue())
                    .build();

            snapshotAssetRepository.save(snapshotAsset);
        }

        log.info("Saved snapshot {} for portfolio {} with {} assets on {}",
                savedSnapshot.getId(), portfolioId, assetWeights.size(), effectiveDate);

        return savedSnapshot;
    }

    /**
     * 포트폴리오의 모든 스냅샷 조회
     */
    @Transactional(readOnly = true)
    public List<PortfolioSnapshot> getSnapshotsByPortfolio(UUID portfolioId) {
        return snapshotRepository.findByPortfolioIdOrderByEffectiveDateAsc(portfolioId);
    }

    /**
     * 특정 날짜의 스냅샷 조회
     */
    @Transactional(readOnly = true)
    public PortfolioSnapshot getSnapshotByDate(UUID portfolioId, LocalDate effectiveDate) {
        return snapshotRepository.findByPortfolioIdAndEffectiveDate(portfolioId, effectiveDate)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Snapshot not found for portfolio %s on %s", portfolioId, effectiveDate)
                ));
    }

    /**
     * 특정 날짜 이전의 가장 최근 스냅샷 조회
     */
    @Transactional(readOnly = true)
    public PortfolioSnapshot getLatestSnapshotBeforeDate(UUID portfolioId, LocalDate date) {
        return snapshotRepository
                .findFirstByPortfolioIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(portfolioId, date)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("No snapshot found for portfolio %s before %s", portfolioId, date)
                ));
    }

    /**
     * 스냅샷의 자산 구성 조회
     */
    @Transactional(readOnly = true)
    public List<PortfolioSnapshotAsset> getSnapshotAssets(UUID snapshotId) {
        return snapshotAssetRepository.findBySnapshotId(snapshotId);
    }

    /**
     * 스냅샷의 자산 구성을 맵으로 조회
     */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> getSnapshotAssetWeights(UUID snapshotId) {
        List<PortfolioSnapshotAsset> assets = snapshotAssetRepository.findBySnapshotId(snapshotId);
        return assets.stream()
                .collect(Collectors.toMap(
                        PortfolioSnapshotAsset::getAssetId,
                        PortfolioSnapshotAsset::getWeight
                ));
    }
}