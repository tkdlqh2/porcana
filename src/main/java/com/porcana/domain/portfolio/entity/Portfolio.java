package com.porcana.domain.portfolio.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "portfolios", indexes = {
        @Index(name = "idx_portfolios_user_id", columnList = "user_id"),
        @Index(name = "idx_portfolios_status", columnList = "status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PortfolioStatus status;

    @Column(name = "started_at")
    private LocalDate startedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Portfolio(UUID userId, String name, PortfolioStatus status, LocalDate startedAt) {
        this.userId = userId;
        this.name = name;
        this.status = status != null ? status : PortfolioStatus.DRAFT;
        this.startedAt = startedAt;
    }

    public void setStatus(PortfolioStatus status) {
        this.status = status;
    }

    public void setStartedAt(LocalDate startedAt) {
        this.startedAt = startedAt;
    }

    public void start() {
        this.status = PortfolioStatus.ACTIVE;
        this.startedAt = LocalDate.now();
    }

    public void finish() {
        this.status = PortfolioStatus.FINISHED;
    }

    public void updateName(String name) {
        this.name = name;
    }
}
