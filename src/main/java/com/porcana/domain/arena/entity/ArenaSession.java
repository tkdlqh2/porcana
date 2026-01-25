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
        @Index(name = "idx_arena_sessions_status", columnList = "status"),
        @Index(name = "idx_arena_sessions_guest_session_id", columnList = "guest_session_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArenaSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    /**
     * 소유 사용자 ID (회원)
     * nullable - 게스트 세션의 경우 NULL
     */
    @Column(name = "user_id")
    private UUID userId;

    /**
     * 소유 게스트 세션 ID (비회원)
     * nullable - 회원 세션의 경우 NULL
     */
    @Column(name = "guest_session_id")
    private UUID guestSessionId;

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
    public ArenaSession(UUID portfolioId, UUID userId, UUID guestSessionId, SessionStatus status,
                        Integer currentRound, Integer totalRounds, RiskProfile riskProfile,
                        List<Sector> selectedSectors) {
        // Validate XOR: exactly one of userId or guestSessionId must be set
        if ((userId == null && guestSessionId == null) || (userId != null && guestSessionId != null)) {
            throw new IllegalArgumentException("Exactly one of userId or guestSessionId must be set");
        }

        this.portfolioId = portfolioId;
        this.userId = userId;
        this.guestSessionId = guestSessionId;
        this.status = status != null ? status : SessionStatus.IN_PROGRESS;
        this.currentRound = currentRound != null ? currentRound : 0;  // 0부터 시작 (0=Pre Round)
        this.totalRounds = totalRounds != null ? totalRounds : 11;  // Pre Round(0) + Asset Rounds(1-10)
        this.riskProfile = riskProfile;
        this.selectedSectors = selectedSectors != null ? selectedSectors : new ArrayList<>();
    }

    /**
     * Create an arena session owned by a user
     */
    public static ArenaSession createForUser(UUID portfolioId, UUID userId) {
        return ArenaSession.builder()
                .portfolioId(portfolioId)
                .userId(userId)
                .build();
    }

    /**
     * Create an arena session owned by a guest
     */
    public static ArenaSession createForGuest(UUID portfolioId, UUID guestSessionId) {
        return ArenaSession.builder()
                .portfolioId(portfolioId)
                .guestSessionId(guestSessionId)
                .build();
    }

    /**
     * Transfer ownership from guest session to user (claim)
     */
    public void claimToUser(UUID userId) {
        if (this.userId != null) {
            throw new IllegalStateException("Arena session is already owned by a user");
        }
        if (this.guestSessionId == null) {
            throw new IllegalStateException("Arena session is not owned by a guest");
        }

        this.userId = userId;
        this.guestSessionId = null;
    }

    /**
     * Check if session is owned by a specific user
     */
    public boolean isOwnedByUser(UUID userId) {
        return this.userId != null && this.userId.equals(userId);
    }

    /**
     * Check if session is owned by a specific guest session
     */
    public boolean isOwnedByGuestSession(UUID guestSessionId) {
        return this.guestSessionId != null && this.guestSessionId.equals(guestSessionId);
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
