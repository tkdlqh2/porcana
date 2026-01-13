package com.porcana.batch.dto;

import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.Sector;
import com.porcana.domain.asset.entity.UniverseTag;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for asset data in batch processing
 * Decouples external API responses from domain entities
 */
@Getter
@Builder
public class AssetBatchDto {

    private final Asset.Market market;
    private final String symbol;
    private final String name;
    private final Asset.AssetType type;
    private final Sector sector;

    @Builder.Default
    private final List<UniverseTag> universeTags = new ArrayList<>();

    @Builder.Default
    private final Boolean active = false;

    private final LocalDate asOf;

    /**
     * Convert to Asset entity for persistence
     */
    public Asset toEntity() {
        return Asset.builder()
                .market(market)
                .symbol(symbol)
                .name(name)
                .type(type)
                .sector(sector)
                .universeTags(universeTags)
                .active(active)
                .asOf(asOf)
                .build();
    }

    /**
     * Update existing entity with this DTO's data
     */
    public void updateEntity(Asset entity) {
        entity.updateUniverseTags(universeTags);
        entity.updateAsOf(asOf);
        if (active) {
            entity.activate();
        } else {
            entity.deactivate();
        }
    }
}