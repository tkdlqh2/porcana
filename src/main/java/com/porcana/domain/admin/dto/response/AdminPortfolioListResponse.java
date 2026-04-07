package com.porcana.domain.admin.dto.response;

import com.porcana.domain.portfolio.entity.Portfolio;
import com.porcana.domain.portfolio.entity.PortfolioStatus;
import lombok.Builder;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Paginated portfolio list response for admin
 */
@Builder
public record AdminPortfolioListResponse(
        List<PortfolioItem> portfolios,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    @Builder
    public record PortfolioItem(
            UUID portfolioId,
            String name,
            PortfolioStatus status,
            UUID userId,
            UUID guestSessionId,
            LocalDate startedAt,
            LocalDateTime createdAt,
            LocalDateTime deletedAt
    ) {
        public static PortfolioItem from(Portfolio portfolio) {
            return PortfolioItem.builder()
                    .portfolioId(portfolio.getId())
                    .name(portfolio.getName())
                    .status(portfolio.getStatus())
                    .userId(portfolio.getUserId())
                    .guestSessionId(portfolio.getGuestSessionId())
                    .startedAt(portfolio.getStartedAt())
                    .createdAt(portfolio.getCreatedAt())
                    .deletedAt(portfolio.getDeletedAt())
                    .build();
        }
    }

    public static AdminPortfolioListResponse from(Page<Portfolio> page) {
        return AdminPortfolioListResponse.builder()
                .portfolios(page.getContent().stream().map(PortfolioItem::from).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
