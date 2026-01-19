package com.porcana.domain.portfolio.command;

import com.porcana.domain.portfolio.dto.CreatePortfolioRequest;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class CreatePortfolioCommand {
    private final UUID userId;
    private final String name;

    public static CreatePortfolioCommand from(CreatePortfolioRequest request, UUID userId) {
        return CreatePortfolioCommand.builder()
                .userId(userId)
                .name(request.name())
                .build();
    }
}