package com.porcana.domain.portfolio;

import com.porcana.domain.portfolio.dto.CreatePortfolioRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Portfolio Controller
 * Base Path: /app/v1/portfolios
 */
@RestController
@RequestMapping("/app/v1/portfolios")
public class PortfolioController {

    /**
     * GET /app/v1/portfolios
     * Response: [{ portfolioId, name, status, isMain, totalReturnPct, createdAt }]
     */
    @GetMapping
    public ResponseEntity<?> getPortfolios() {
        // TODO: Implement get portfolios list logic
        return ResponseEntity.status(501).body("Not implemented");
    }

    /**
     * POST /app/v1/portfolios
     * Request: { name }
     * Response: { portfolioId, name, status, createdAt }
     */
    @PostMapping
    public ResponseEntity<?> createPortfolio(@RequestBody CreatePortfolioRequest request) {
        // TODO: Implement create portfolio logic
        return ResponseEntity.status(501).body("Not implemented");
    }

    /**
     * GET /app/v1/portfolios/{portfolioId}
     * Response: { portfolioId, name, status, isMain, startedAt, totalReturnPct, positions: [...] }
     */
    @GetMapping("/{portfolioId}")
    public ResponseEntity<?> getPortfolio(@PathVariable String portfolioId) {
        // TODO: Implement get portfolio detail logic
        return ResponseEntity.status(501).body("Not implemented");
    }

    /**
     * POST /app/v1/portfolios/{portfolioId}/start
     * Response: { portfolioId, status, startedAt }
     */
    @PostMapping("/{portfolioId}/start")
    public ResponseEntity<?> startPortfolio(@PathVariable String portfolioId) {
        // TODO: Implement start portfolio logic
        return ResponseEntity.status(501).body("Not implemented");
    }

    /**
     * GET /app/v1/portfolios/{portfolioId}/performance
     * Query: range=1M|3M|1Y
     * Response: { portfolioId, range, points: [{ date, value }] }
     */
    @GetMapping("/{portfolioId}/performance")
    public ResponseEntity<?> getPortfolioPerformance(
            @PathVariable String portfolioId,
            @RequestParam String range
    ) {
        // TODO: Implement get portfolio performance logic
        return ResponseEntity.status(501).body("Not implemented");
    }
}