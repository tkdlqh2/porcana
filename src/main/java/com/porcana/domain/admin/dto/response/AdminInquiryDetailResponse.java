package com.porcana.domain.admin.dto.response;

import com.porcana.domain.inquiry.entity.Inquiry;
import com.porcana.domain.inquiry.entity.InquiryCategory;
import com.porcana.domain.inquiry.entity.InquiryResponse;
import com.porcana.domain.inquiry.entity.InquiryStatus;
import com.porcana.domain.user.entity.User;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record AdminInquiryDetailResponse(
        UUID inquiryId,
        UUID userId,
        UUID guestSessionId,
        String email,
        String nickname,
        InquiryCategory category,
        String title,
        String content,
        InquiryStatus status,
        LocalDateTime createdAt,
        LocalDateTime respondedAt,
        List<ResponseItem> responses
) {
    @Builder
    public record ResponseItem(
            UUID responseId,
            UUID responderId,
            String responderNickname,
            String content,
            LocalDateTime sentAt
    ) {
        public static ResponseItem from(InquiryResponse response) {
            return ResponseItem.builder()
                    .responseId(response.getId())
                    .responderId(response.getResponder().getId())
                    .responderNickname(response.getResponder().getNickname())
                    .content(response.getContent())
                    .sentAt(response.getSentAt())
                    .build();
        }
    }

    public static AdminInquiryDetailResponse from(Inquiry inquiry, List<InquiryResponse> responses) {
        User user = inquiry.getUser();
        return AdminInquiryDetailResponse.builder()
                .inquiryId(inquiry.getId())
                .userId(user != null ? user.getId() : null)
                .guestSessionId(inquiry.getGuestSessionId())
                .email(inquiry.getEmail())
                .nickname(user != null ? user.getNickname() : null)
                .category(inquiry.getCategory())
                .title(inquiry.getTitle())
                .content(inquiry.getContent())
                .status(inquiry.getStatus())
                .createdAt(inquiry.getCreatedAt())
                .respondedAt(inquiry.getRespondedAt())
                .responses(responses.stream().map(ResponseItem::from).toList())
                .build();
    }
}
