package com.porcana.domain.admin.dto.response;

import com.porcana.domain.inquiry.entity.Inquiry;
import com.porcana.domain.inquiry.entity.InquiryCategory;
import com.porcana.domain.inquiry.entity.InquiryStatus;
import com.porcana.domain.user.entity.User;
import lombok.Builder;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record AdminInquiryListResponse(
        List<InquiryItem> inquiries,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    @Builder
    public record InquiryItem(
            UUID inquiryId,
            UUID userId,
            UUID guestSessionId,
            String email,
            String nickname,
            InquiryCategory category,
            String title,
            InquiryStatus status,
            LocalDateTime createdAt,
            LocalDateTime respondedAt
    ) {
        public static InquiryItem from(Inquiry inquiry) {
            User user = inquiry.getUser();
            return InquiryItem.builder()
                    .inquiryId(inquiry.getId())
                    .userId(user != null ? user.getId() : null)
                    .guestSessionId(inquiry.getGuestSessionId())
                    .email(inquiry.getEmail())
                    .nickname(user != null ? user.getNickname() : null)
                    .category(inquiry.getCategory())
                    .title(inquiry.getTitle())
                    .status(inquiry.getStatus())
                    .createdAt(inquiry.getCreatedAt())
                    .respondedAt(inquiry.getRespondedAt())
                    .build();
        }
    }

    public static AdminInquiryListResponse from(Page<Inquiry> page) {
        return AdminInquiryListResponse.builder()
                .inquiries(page.getContent().stream().map(InquiryItem::from).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
