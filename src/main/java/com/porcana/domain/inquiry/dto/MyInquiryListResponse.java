package com.porcana.domain.inquiry.dto;

import com.porcana.domain.inquiry.entity.Inquiry;
import lombok.Builder;
import org.springframework.data.domain.Page;

import java.util.List;

@Builder
public record MyInquiryListResponse(
        List<InquiryItemResponse> inquiries,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static MyInquiryListResponse from(Page<Inquiry> page) {
        return MyInquiryListResponse.builder()
                .inquiries(page.getContent().stream().map(InquiryItemResponse::from).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
