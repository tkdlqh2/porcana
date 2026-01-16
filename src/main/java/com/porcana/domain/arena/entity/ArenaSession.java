package com.porcana.domain.arena.entity;

import com.porcana.domain.asset.entity.Sector;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "arena_sessions", indexes = {
        @Index(name = "idx_arena_sessions_portfolio_id", columnList = "portfolio_id"),
        @Index(name = "idx_arena_sessions_user_id", columnList = "user_id"),
        @Index(name = "idx_arena_sessions_status", columnList = "status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArenaSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "current_round", nullable = false)
    private Integer currentRound;

    @Column(name = "total_rounds", nullable = false)
    private Integer totalRounds;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_profile", length = 20)
    private RiskProfile riskProfile;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "arena_session_sectors",
            joinColumns = @JoinColumn(name = "session_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "sector", length = 50)
    private List<Sector> selectedSectors = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Builder
    public ArenaSession(UUID portfolioId, UUID userId, SessionStatus status,
                        Integer currentRound, Integer totalRounds, RiskProfile riskProfile,
                        List<Sector> selectedSectors) {
        this.portfolioId = portfolioId;
        this.userId = userId;
        this.status = status != null ? status : SessionStatus.IN_PROGRESS;
        this.currentRound = currentRound != null ? currentRound : 1;
        this.totalRounds = totalRounds != null ? totalRounds : 12;
        this.riskProfile = riskProfile;
        this.selectedSectors = selectedSectors != null ? selectedSectors : new ArrayList<>();
    }

    public void setRiskProfile(RiskProfile riskProfile) {
        this.riskProfile = riskProfile;
    }

    public void setSelectedSectors(List<Sector> selectedSectors) {
        this.selectedSectors = selectedSectors != null ? selectedSectors : new ArrayList<>();
    }

    public void setCurrentRound(Integer currentRound) {
        this.currentRound = currentRound;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public void complete() {
        this.status = SessionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void abandon() {
        this.status = SessionStatus.ABANDONED;
    }
}
