package com.porcana.domain.inquiry.dto;

import com.porcana.domain.inquiry.entity.InquiryCategory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateInquiryRequest(
        @NotBlank
        @Email
        String email,

        @NotNull
        InquiryCategory category,

        @NotBlank
        @Size(max = 120)
        String title,

        @NotBlank
        @Size(max = 5000)
        String content
) {
}
