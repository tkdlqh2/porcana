package com.porcana.domain.asset.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AssetDetailResponse {
    private final String assetId;
    private final String ticker;
    private final String name;
    private final String exchange;
    private final String country;
    private final String sector;
    private final String currency;
    private final String imageUrl;
    private final String description;
}