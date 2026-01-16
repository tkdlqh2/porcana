package com.porcana.domain.arena.command;

import com.porcana.domain.arena.dto.PickAssetRequest;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class PickAssetCommand {
    private final UUID pickedAssetId;

    public static PickAssetCommand from(PickAssetRequest request) {
        return PickAssetCommand.builder()
                .pickedAssetId(request.pickedAssetId())
                .build();
    }
}
