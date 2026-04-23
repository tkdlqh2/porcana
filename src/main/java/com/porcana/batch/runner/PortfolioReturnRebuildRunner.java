package com.porcana.batch.runner;

import com.porcana.batch.service.PortfolioPerformanceBackfillService;
import com.porcana.domain.portfolio.entity.Portfolio;
import com.porcana.domain.portfolio.entity.PortfolioStatus;
import com.porcana.domain.portfolio.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "batch.runner.portfolio-return-rebuild",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
@RequiredArgsConstructor
public class PortfolioReturnRebuildRunner implements ApplicationRunner {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioPerformanceBackfillService portfolioPerformanceBackfillService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("========================================");
        log.info("Starting Portfolio Return Rebuild Runner");
        log.info("========================================");

        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<Portfolio> portfolios = new ArrayList<>();
        portfolios.addAll(portfolioRepository.findByStatusAndDeletedAtIsNull(PortfolioStatus.ACTIVE));
        portfolios.addAll(portfolioRepository.findByStatusAndDeletedAtIsNull(PortfolioStatus.FINISHED));

        log.info("Found {} portfolios to rebuild", portfolios.size());

        int successPortfolios = 0;
        int failedPortfolios = 0;
        int totalDaysInserted = 0;

        for (int i = 0; i < portfolios.size(); i++) {
            Portfolio portfolio = portfolios.get(i);
            log.info("[{}/{}] Rebuilding portfolio {} (status: {}, started: {})",
                    i + 1, portfolios.size(), portfolio.getId(), portfolio.getStatus(), portfolio.getStartedAt());

            try {
                int[] result = portfolioPerformanceBackfillService.rebuildPortfolio(portfolio, yesterday);
                totalDaysInserted += result[0];
                successPortfolios++;
                log.info("  Rebuilt: {} days inserted after purge", result[0]);
            } catch (Exception e) {
                failedPortfolios++;
                log.error("  Failed to rebuild portfolio {}: {}", portfolio.getId(), e.getMessage(), e);
            }
        }

        log.info("========================================");
        log.info("Portfolio Return Rebuild completed");
        log.info("Portfolios: {} success, {} failed", successPortfolios, failedPortfolios);
        log.info("Days rebuilt: {}", totalDaysInserted);
        log.info("========================================");
    }
}
