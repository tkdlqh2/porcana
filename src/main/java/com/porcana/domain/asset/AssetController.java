package com.porcana.domain.asset;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Asset Controller (종목)
 * Base Path: /app/v1/assets
 */
@RestController
@RequestMapping("/assets")
public class AssetController {

    /**
     * GET /app/v1/assets/search
     * Query: query=string
     * Response: [{ assetId, ticker, name, exchange, country, sector, imageUrl }]
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchAssets(@RequestParam String query) {
        // TODO: Implement search assets logic
        return ResponseEntity.status(501).body("Not implemented");
    }

    /**
     * GET /app/v1/assets/{assetId}
     * Response: { assetId, ticker, name, exchange, country, sector, currency, imageUrl, description }
     */
    @GetMapping("/{assetId}")
    public ResponseEntity<?> getAsset(@PathVariable String assetId) {
        // TODO: Implement get asset detail logic
        return ResponseEntity.status(501).body("Not implemented");
    }

    /**
     * GET /app/v1/assets/{assetId}/chart
     * Query: range=1M|3M|1Y
     * Response: { assetId, range, points: [{ date, price }] }
     */
    @GetMapping("/{assetId}/chart")
    public ResponseEntity<?> getAssetChart(
            @PathVariable String assetId,
            @RequestParam String range
    ) {
        // TODO: Implement get asset chart logic
        return ResponseEntity.status(501).body("Not implemented");
    }

    /**
     * GET /app/v1/assets/{assetId}/in-my-main-portfolio
     * Response (not included): { included: false }
     * Response (included): { included: true, portfolioId, weightPct, returnPct }
     */
    @GetMapping("/{assetId}/in-my-main-portfolio")
    public ResponseEntity<?> isAssetInMainPortfolio(@PathVariable String assetId) {
        // TODO: Implement check asset in main portfolio logic
        return ResponseEntity.status(501).body("Not implemented");
    }
}