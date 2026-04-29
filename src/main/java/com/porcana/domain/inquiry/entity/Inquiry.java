package com.porcana.domain.inquiry.entity;

import com.porcana.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "inquiries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "guest_session_id")
    private UUID guestSessionId;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InquiryCategory category;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InquiryStatus status = InquiryStatus.RECEIVED;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Inquiry(User user, UUID guestSessionId, String email, InquiryCategory category, String title, String content) {
        this.user = user;
        this.guestSessionId = guestSessionId;
        this.email = email;
        this.category = category;
        this.title = title;
        this.content = content;
        this.status = InquiryStatus.RECEIVED;
    }

    public void updateStatus(InquiryStatus status) {
        this.status = status;
        if (status == InquiryStatus.RESOLVED) {
            this.respondedAt = LocalDateTime.now();
        }
    }

    public void markResponded(LocalDateTime respondedAt) {
        this.respondedAt = respondedAt;
        if (this.status == InquiryStatus.RECEIVED) {
            this.status = InquiryStatus.IN_PROGRESS;
        }
    }
}
