package com.porcana.domain.inquiry.dto;

import com.porcana.domain.inquiry.entity.Inquiry;
import com.porcana.domain.inquiry.entity.InquiryCategory;
import com.porcana.domain.inquiry.entity.InquiryStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record InquiryItemResponse(
        UUID inquiryId,
        String email,
        InquiryCategory category,
        String title,
        String content,
        InquiryStatus status,
        LocalDateTime createdAt,
        LocalDateTime respondedAt
) {
    public static InquiryItemResponse from(Inquiry inquiry) {
        return InquiryItemResponse.builder()
                .inquiryId(inquiry.getId())
                .email(inquiry.getEmail())
                .category(inquiry.getCategory())
                .title(inquiry.getTitle())
                .content(inquiry.getContent())
                .status(inquiry.getStatus())
                .createdAt(inquiry.getCreatedAt())
                .respondedAt(inquiry.getRespondedAt())
                .build();
    }
}
