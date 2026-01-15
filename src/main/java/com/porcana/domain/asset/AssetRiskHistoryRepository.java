package com.porcana.domain.asset;

import com.porcana.domain.asset.entity.AssetRiskHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetRiskHistoryRepository extends JpaRepository<AssetRiskHistory, UUID> {

    /**
     * 특정 자산의 특정 주차 위험도 이력 조회
     */
    Optional<AssetRiskHistory> findByAssetIdAndWeek(UUID assetId, String week);

    /**
     * 특정 자산의 특정 주차 위험도 이력 존재 여부 확인
     */
    boolean existsByAssetIdAndWeek(UUID assetId, String week);
}