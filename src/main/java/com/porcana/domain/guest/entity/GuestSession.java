package com.porcana.domain.guest.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 게스트 세션 엔티티
 * 비회원 사용자의 임시 세션 정보를 저장
 */
@Entity
@Table(name = "guest_sessions", indexes = {
        @Index(name = "idx_guest_sessions_last_seen_at", columnList = "last_seen_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuestSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * 세션 생성 시각
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 마지막 활동 시각 (만료 판단용)
     */
    @Column(nullable = false)
    private LocalDateTime lastSeenAt;

    /**
     * 게스트 세션 생성 팩토리 메서드
     */
    public static GuestSession create() {
        GuestSession session = new GuestSession();
        session.lastSeenAt = LocalDateTime.now();
        return session;
    }

    /**
     * 마지막 활동 시각 업데이트
     */
    public void updateLastSeenAt() {
        this.lastSeenAt = LocalDateTime.now();
    }

    /**
     * 세션이 만료되었는지 확인
     * @param expirationDays 만료 기준 일수
     * @return 만료 여부
     */
    public boolean isExpired(int expirationDays) {
        LocalDateTime expirationThreshold = LocalDateTime.now().minusDays(expirationDays);
        return this.lastSeenAt.isBefore(expirationThreshold);
    }
}
