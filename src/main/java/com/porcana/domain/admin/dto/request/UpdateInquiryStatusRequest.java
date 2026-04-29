package com.porcana.domain.admin.dto.request;

import com.porcana.domain.inquiry.entity.InquiryStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateInquiryStatusRequest(
        @NotNull
        InquiryStatus status
) {
}
