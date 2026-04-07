package com.porcana.domain.admin.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating asset image URL
 */
public record UpdateAssetImageRequest(
        @Size(max = 500, message = "Image URL must be less than 500 characters")
        String imageUrl
) {}
