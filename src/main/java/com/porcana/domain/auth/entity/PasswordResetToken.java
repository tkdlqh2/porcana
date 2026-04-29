package com.porcana.domain.auth.entity;

import com.porcana.domain.user.entity.User;
import com.porcana.global.auth.TokenGenerator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 16)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private static final int TOKEN_LENGTH = 16;

    public static PasswordResetToken create(User user) {
        PasswordResetToken prt = new PasswordResetToken();
        prt.user = user;
        prt.token = TokenGenerator.generate(TOKEN_LENGTH);
        prt.expiresAt = LocalDateTime.now().plusHours(1);
        return prt;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }

    public boolean isUsed() {
        return this.usedAt != null;
    }

    public void markUsed() {
        if (this.usedAt != null) {
            throw new IllegalStateException("이미 사용된 토큰입니다");
        }
        this.usedAt = LocalDateTime.now();
    }
}
