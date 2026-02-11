package com.porcana.batch.job;

import com.porcana.batch.listener.BatchNotificationListener;
import com.porcana.domain.arena.repository.ArenaRoundRepository;
import com.porcana.domain.arena.repository.ArenaSessionRepository;
import com.porcana.domain.portfolio.entity.Portfolio;
import com.porcana.domain.portfolio.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Cleanup batch job for deleted portfolios
 * Hard-deletes portfolios that have been soft-deleted for more than 30 days
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DeletedPortfolioCleanupBatchJob {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final PortfolioDailyReturnRepository portfolioDailyReturnRepository;
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final PortfolioSnapshotAssetRepository portfolioSnapshotAssetRepository;
    private final SnapshotAssetDailyReturnRepository snapshotAssetDailyReturnRepository;
    private final ArenaSessionRepository arenaSessionRepository;
    private final ArenaRoundRepository arenaRoundRepository;
    private final BatchNotificationListener batchNotificationListener;

    private static final int RETENTION_DAYS = 30;

    // Self-injection for @Transactional to work on self-calls
    private DeletedPortfolioCleanupBatchJob self;

    @Autowired
    public void setSelf(@Lazy DeletedPortfolioCleanupBatchJob self) {
        this.self = self;
    }

    @Bean
    public Job deletedPortfolioCleanupJob() {
        return new JobBuilder("deletedPortfolioCleanupJob", jobRepository)
                .listener(batchNotificationListener)
                .start(cleanupDeletedPortfoliosStep())
                .build();
    }

    @Bean
    public Step cleanupDeletedPortfoliosStep() {
        return new StepBuilder("cleanupDeletedPortfoliosStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Starting deleted portfolio cleanup (retention: {} days)", RETENTION_DAYS);

                    LocalDateTime cutoffDate = LocalDateTime.now().minusDays(RETENTION_DAYS);
                    log.info("Cutoff date for cleanup: {}", cutoffDate);

                    List<Portfolio> portfoliosToDelete = portfolioRepository.findDeletedPortfoliosOlderThan(cutoffDate);
                    log.info("Found {} portfolios to hard-delete", portfoliosToDelete.size());

                    int deletedCount = 0;
                    int failedCount = 0;
                    for (Portfolio portfolio : portfoliosToDelete) {
                        try {
                            self.hardDeletePortfolio(portfolio.getId());
                            deletedCount++;
                            log.info("Hard-deleted portfolio: {} (deleted at: {})",
                                    portfolio.getId(), portfolio.getDeletedAt());
                        } catch (Exception e) {
                            failedCount++;
                            log.error("Failed to hard-delete portfolio: {} - transaction rolled back",
                                    portfolio.getId(), e);
                        }
                    }

                    if (failedCount > 0) {
                        log.warn("Portfolio cleanup completed with errors: {} deleted, {} failed",
                                deletedCount, failedCount);
                    }

                    log.info("Deleted portfolio cleanup completed: {} portfolios deleted", deletedCount);

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /**
     * Hard-delete portfolio and all related data in a separate transaction.
     * If any deletion fails, the entire portfolio deletion is rolled back.
     * Deletion order is critical to avoid foreign key constraint violations.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void hardDeletePortfolio(UUID portfolioId) {
        log.debug("Hard-deleting portfolio: {}", portfolioId);

        // 1. Delete ArenaRound records (must be before ArenaSession)
        arenaSessionRepository.findByPortfolioId(portfolioId).forEach(session -> {
            int roundsDeleted = arenaRoundRepository.deleteBySessionId(session.getId());
            log.debug("Deleted {} arena rounds for session: {}", roundsDeleted, session.getId());
        });

        // 2. Delete ArenaSession records
        int arenaSessionsDeleted = arenaSessionRepository.deleteByPortfolioId(portfolioId);
        log.debug("Deleted {} arena sessions for portfolio: {}", arenaSessionsDeleted, portfolioId);

        // 3. Delete SnapshotAssetDailyReturn records
        int snapshotAssetDailyReturnsDeleted = snapshotAssetDailyReturnRepository.deleteByPortfolioId(portfolioId);
        log.debug("Deleted {} snapshot asset daily returns for portfolio: {}", snapshotAssetDailyReturnsDeleted, portfolioId);

        // 4. Delete PortfolioDailyReturn records (must be before PortfolioSnapshot due to FK)
        int dailyReturnsDeleted = portfolioDailyReturnRepository.deleteByPortfolioId(portfolioId);
        log.debug("Deleted {} daily returns for portfolio: {}", dailyReturnsDeleted, portfolioId);

        // 5. Delete PortfolioSnapshotAsset records (must be before PortfolioSnapshot)
        portfolioSnapshotRepository.findByPortfolioId(portfolioId).forEach(snapshot -> {
            int snapshotAssetsDeleted = portfolioSnapshotAssetRepository.deleteBySnapshotId(snapshot.getId());
            log.debug("Deleted {} snapshot assets for snapshot: {}", snapshotAssetsDeleted, snapshot.getId());
        });

        // 6. Delete PortfolioSnapshot records
        int snapshotsDeleted = portfolioSnapshotRepository.deleteByPortfolioId(portfolioId);
        log.debug("Deleted {} snapshots for portfolio: {}", snapshotsDeleted, portfolioId);

        // 7. Delete PortfolioAsset records
        int portfolioAssetsDeleted = portfolioAssetRepository.deleteByPortfolioId(portfolioId);
        log.debug("Deleted {} portfolio assets for portfolio: {}", portfolioAssetsDeleted, portfolioId);

        // 8. Finally, delete Portfolio itself
        portfolioRepository.deleteById(portfolioId);
        log.debug("Hard-deleted portfolio: {}", portfolioId);
    }
}