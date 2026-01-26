package com.porcana.domain.portfolio.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "portfolios", indexes = {
        @Index(name = "idx_portfolios_user_id", columnList = "user_id"),
        @Index(name = "idx_portfolios_status", columnList = "status"),
        @Index(name = "idx_portfolios_guest_session_id", columnList = "guest_session_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * 소유 사용자 ID (회원)
     * nullable - 게스트 포트폴리오의 경우 NULL
     */
    @Column(name = "user_id")
    private UUID userId;

    /**
     * 소유 게스트 세션 ID (비회원)
     * nullable - 회원 포트폴리오의 경우 NULL
     */
    @Column(name = "guest_session_id")
    private UUID guestSessionId;

    @Column(nullable = false, length = 100)
    private String name;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PortfolioStatus status;

    @Setter
    @Column(name = "started_at")
    private LocalDate startedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Portfolio(UUID userId, UUID guestSessionId, String name, PortfolioStatus status, LocalDate startedAt) {
        // Validate XOR: exactly one of userId or guestSessionId must be set
        if ((userId == null && guestSessionId == null) || (userId != null && guestSessionId != null)) {
            throw new IllegalArgumentException("Exactly one of userId or guestSessionId must be set");
        }

        this.userId = userId;
        this.guestSessionId = guestSessionId;
        this.name = name;
        this.status = status != null ? status : PortfolioStatus.DRAFT;
        this.startedAt = startedAt;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Create a portfolio owned by a user
     */
    public static Portfolio createForUser(UUID userId, String name) {
        return Portfolio.builder()
                .userId(userId)
                .name(name)
                .status(PortfolioStatus.DRAFT)
                .build();
    }

    /**
     * Create a portfolio owned by a guest session
     */
    public static Portfolio createForGuest(UUID guestSessionId, String name) {
        return Portfolio.builder()
                .guestSessionId(guestSessionId)
                .name(name)
                .status(PortfolioStatus.DRAFT)
                .build();
    }

    /**
     * Transfer ownership from guest session to user (claim)
     * @param userId User ID to claim the portfolio
     */
    public void claimToUser(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }

        if (this.userId != null) {
            throw new IllegalStateException("Portfolio is already owned by a user");
        }
        if (this.guestSessionId == null) {
            throw new IllegalStateException("Portfolio is not owned by a guest session");
        }

        this.userId = userId;
        this.guestSessionId = null;
    }

    /**
     * Check if portfolio is owned by a specific user
     */
    public boolean isOwnedByUser(UUID userId) {
        return this.userId != null && this.userId.equals(userId);
    }

    /**
     * Check if portfolio is owned by a specific guest session
     */
    public boolean isOwnedByGuestSession(UUID guestSessionId) {
        return this.guestSessionId != null && this.guestSessionId.equals(guestSessionId);
    }

    public void start() {
        if (this.status == PortfolioStatus.DRAFT) {
            this.status = PortfolioStatus.ACTIVE;
            this.startedAt = LocalDate.now();
        }
        // If already ACTIVE or FINISHED, do nothing (idempotent)
    }

    public void finish() {
        this.status = PortfolioStatus.FINISHED;
    }

    public void updateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("name must be <= 100 characters");
        }
        this.name = name;
    }
}
