package com.porcana.domain.arena.dto;

import com.porcana.domain.arena.entity.RoundType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AssetRoundResponse implements RoundResponse {
    private UUID sessionId;
    private Integer round;
    private RoundType roundType;
    private List<AssetOption> assets;

    @Getter
    @Builder
    public static class AssetOption {
        private UUID assetId;
        private String ticker;
        private String name;
        private String sector;
        private List<String> tags;
    }
}
