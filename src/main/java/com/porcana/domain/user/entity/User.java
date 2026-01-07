package com.porcana.domain.user.entity;

import com.porcana.domain.auth.command.SignupCommand;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider;

    @Setter
    @Column(name = "main_portfolio_id")
    private UUID mainPortfolioId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public User(String email, String password, String nickname, AuthProvider provider) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.provider = provider;
    }

    public static User from(SignupCommand command, String encodedPassword) {
        return User.builder()
                .email(command.getEmail())
                .password(encodedPassword)
                .nickname(command.getNickname())
                .provider(command.getProvider())
                .build();
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public enum AuthProvider {
        EMAIL, GOOGLE, KAKAO
    }
}