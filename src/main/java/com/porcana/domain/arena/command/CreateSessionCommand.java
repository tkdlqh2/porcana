package com.porcana.domain.arena.command;

import com.porcana.domain.arena.dto.CreateSessionRequest;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class CreateSessionCommand {
    private final UUID portfolioId;
    private final UUID userId;

    public static CreateSessionCommand from(CreateSessionRequest request, UUID userId) {
        return CreateSessionCommand.builder()
                .portfolioId(request.portfolioId())
                .userId(userId)
                .build();
    }
}
