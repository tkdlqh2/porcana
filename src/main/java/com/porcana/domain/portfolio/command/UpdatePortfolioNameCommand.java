package com.porcana.domain.portfolio.command;

import com.porcana.domain.portfolio.dto.UpdatePortfolioNameRequest;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class UpdatePortfolioNameCommand {
    private final UUID portfolioId;
    private final UUID userId;
    private final String name;

    public static UpdatePortfolioNameCommand from(UpdatePortfolioNameRequest request, UUID portfolioId, UUID userId) {
        return UpdatePortfolioNameCommand.builder()
                .portfolioId(portfolioId)
                .userId(userId)
                .name(request.name())
                .build();
    }
}