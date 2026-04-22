package com.porcana.domain.portfolio.repository;

import com.porcana.domain.portfolio.entity.Portfolio;
import com.porcana.domain.portfolio.entity.PortfolioStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Set;

/**
 * Portfolio Repository Custom Interface for QueryDSL
 */
public interface PortfolioRepositoryCustom {

    /**
     * Search portfolios by name (case-insensitive, excluding deleted)
     */
    Page<Portfolio> searchByName(String keyword, Pageable pageable);

    /**
     * Search portfolios by name and allowed statuses (excluding deleted)
     */
    Page<Portfolio> searchByNameAndStatuses(String keyword, Set<PortfolioStatus> statuses, Pageable pageable);

    /**
     * Find portfolios by allowed statuses (excluding deleted)
     */
    Page<Portfolio> findByStatuses(Set<PortfolioStatus> statuses, Pageable pageable);
}
