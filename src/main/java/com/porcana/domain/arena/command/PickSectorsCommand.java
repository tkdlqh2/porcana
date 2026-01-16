package com.porcana.domain.arena.command;

import com.porcana.domain.arena.dto.PickSectorsRequest;
import com.porcana.domain.asset.entity.Sector;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
public class PickSectorsCommand {
    private final List<Sector> sectors;

    public static PickSectorsCommand from(PickSectorsRequest request) {
        return PickSectorsCommand.builder()
                .sectors(new ArrayList<>(request.sectors()))
                .build();
    }
}
