package com.porcana.domain.arena.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "arena_rounds", indexes = {
        @Index(name = "idx_arena_round_session_round", columnList = "session_id, round_number", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArenaRound {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "round_number", nullable = false)
    private Integer roundNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "round_type", nullable = false, length = 20)
    private RoundType roundType;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "arena_round_choices",
            joinColumns = @JoinColumn(name = "round_id")
    )
    @Column(name = "asset_id")
    private List<UUID> presentedAssetIds = new ArrayList<>();

    @Column(name = "selected_asset_id")
    private UUID selectedAssetId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "picked_at")
    private LocalDateTime pickedAt;

    @Builder
    public ArenaRound(UUID sessionId, Integer roundNumber, RoundType roundType,
                      List<UUID> presentedAssetIds, UUID selectedAssetId) {
        this.sessionId = sessionId;
        this.roundNumber = roundNumber;
        this.roundType = roundType;
        this.presentedAssetIds = presentedAssetIds != null ? presentedAssetIds : new ArrayList<>();
        this.selectedAssetId = selectedAssetId;
    }

    public void setSelectedAssetId(UUID selectedAssetId) {
        this.selectedAssetId = selectedAssetId;
    }

    public void setPickedAt(LocalDateTime pickedAt) {
        this.pickedAt = pickedAt;
    }

    public void pick(UUID assetId) {
        this.selectedAssetId = assetId;
        this.pickedAt = LocalDateTime.now();
    }
}
