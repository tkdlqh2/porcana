package com.porcana.domain.home;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Home Controller (Main Portfolio Widget)
 * Base Path: /api/v1
 */
@RestController
@RequestMapping("/api/v1")
public class HomeController {

    /**
     * GET /app/v1/home
     * Response (no main): { hasMainPortfolio: false }
     * Response (has main): { hasMainPortfolio: true, mainPortfolio: {...}, chart: [...], positions: [...] }
     */
    @GetMapping("/home")
    public ResponseEntity<?> getHome() {
        // TODO: Implement get home logic
        return ResponseEntity.status(501).body("Not implemented");
    }

    /**
     * PUT /app/v1/portfolios/{portfolioId}/main
     * Response: { mainPortfolioId }
     */
    @PutMapping("/portfolios/{portfolioId}/main")
    public ResponseEntity<?> setMainPortfolio(@PathVariable String portfolioId) {
        // TODO: Implement set main portfolio logic
        return ResponseEntity.status(501).body("Not implemented");
    }

    /**
     * DELETE /app/v1/portfolios/main
     * Response: { mainPortfolioId: null }
     */
    @DeleteMapping("/portfolios/main")
    public ResponseEntity<?> removeMainPortfolio() {
        // TODO: Implement remove main portfolio logic
        return ResponseEntity.status(501).body("Not implemented");
    }
}