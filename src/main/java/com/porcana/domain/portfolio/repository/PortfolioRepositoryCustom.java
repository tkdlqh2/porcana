package com.porcana.domain.portfolio.repository;

import com.porcana.domain.portfolio.entity.Portfolio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Portfolio Repository Custom Interface for QueryDSL
 */
public interface PortfolioRepositoryCustom {

    /**
     * Search portfolios by name (case-insensitive, excluding deleted)
     */
    Page<Portfolio> searchByName(String keyword, Pageable pageable);
}
