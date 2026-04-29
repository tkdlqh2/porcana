package com.porcana.domain.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateInquiryResponseRequest(
        @NotBlank
        @Size(max = 5000)
        String content
) {
}
