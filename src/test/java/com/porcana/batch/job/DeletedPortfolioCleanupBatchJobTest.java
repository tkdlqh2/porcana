package com.porcana.batch.job;

import com.porcana.domain.arena.repository.ArenaRoundRepository;
import com.porcana.domain.arena.repository.ArenaSessionRepository;
import com.porcana.domain.portfolio.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Sql(scripts = "/sql/batch-cleanup-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DeletedPortfolioCleanupBatchJobTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("porcana_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job deletedPortfolioCleanupJob;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private PortfolioAssetRepository portfolioAssetRepository;

    @Autowired
    private PortfolioDailyReturnRepository portfolioDailyReturnRepository;

    @Autowired
    private PortfolioSnapshotRepository portfolioSnapshotRepository;

    @Autowired
    private PortfolioSnapshotAssetRepository portfolioSnapshotAssetRepository;

    @Autowired
    private SnapshotAssetDailyReturnRepository snapshotAssetDailyReturnRepository;

    @Autowired
    private ArenaSessionRepository arenaSessionRepository;

    @Autowired
    private ArenaRoundRepository arenaRoundRepository;

    // Test IDs from SQL file
    private static final UUID OLD_DELETED_PORTFOLIO_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
    private static final UUID RECENT_DELETED_PORTFOLIO_ID = UUID.fromString("77777777-0000-0000-0000-000000000001");
    private static final UUID ACTIVE_PORTFOLIO_ID = UUID.fromString("88888888-0000-0000-0000-000000000001");
    private static final UUID OLD_SNAPSHOT_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
    private static final UUID OLD_ARENA_SESSION_ID = UUID.fromString("55555555-0000-0000-0000-000000000001");

    @Test
    @DisplayName("30일 이상 경과한 삭제 포트폴리오 하드 삭제 성공")
    void deleteOldPortfolios_success() throws Exception {
        // Given - data loaded from SQL file

        // When
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution jobExecution = jobLauncher.run(deletedPortfolioCleanupJob, jobParameters);

        // Then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // Old deleted portfolio and all related data should be hard-deleted
        assertThat(portfolioRepository.findById(OLD_DELETED_PORTFOLIO_ID)).isEmpty();
        assertThat(portfolioAssetRepository.findByPortfolioId(OLD_DELETED_PORTFOLIO_ID)).isEmpty();
        assertThat(portfolioSnapshotRepository.findById(OLD_SNAPSHOT_ID)).isEmpty();
        assertThat(portfolioSnapshotAssetRepository.findBySnapshotId(OLD_SNAPSHOT_ID)).isEmpty();
        assertThat(portfolioDailyReturnRepository.findByPortfolioIdOrderByReturnDateAsc(OLD_DELETED_PORTFOLIO_ID)).isEmpty();
        assertThat(snapshotAssetDailyReturnRepository.findByPortfolioIdOrderByReturnDateAsc(OLD_DELETED_PORTFOLIO_ID)).isEmpty();
        assertThat(arenaSessionRepository.findById(OLD_ARENA_SESSION_ID)).isEmpty();
        assertThat(arenaRoundRepository.findBySessionIdOrderByRoundNumberAsc(OLD_ARENA_SESSION_ID)).isEmpty();

        // Recent deleted portfolio should still exist (soft-deleted)
        assertThat(portfolioRepository.findById(RECENT_DELETED_PORTFOLIO_ID)).isPresent();
        assertThat(portfolioRepository.findById(RECENT_DELETED_PORTFOLIO_ID).get().isDeleted()).isTrue();
    }

    @Test
    @DisplayName("삭제되지 않은 포트폴리오는 영향 없음")
    void doNotDeleteActivePortfolios() throws Exception {
        // Given - active portfolio loaded from SQL file

        // When
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution jobExecution = jobLauncher.run(deletedPortfolioCleanupJob, jobParameters);

        // Then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // Active portfolio should still exist
        assertThat(portfolioRepository.findById(ACTIVE_PORTFOLIO_ID)).isPresent();
        assertThat(portfolioRepository.findById(ACTIVE_PORTFOLIO_ID).get().isDeleted()).isFalse();
    }

    @Test
    @DisplayName("배치 실행 - 모든 포트폴리오가 정상 처리됨")
    void batchCompletesSuccessfully() throws Exception {
        // Given - portfolios from SQL file (old deleted, recent deleted, active)

        // When
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution jobExecution = jobLauncher.run(deletedPortfolioCleanupJob, jobParameters);

        // Then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // Verify expected state after batch
        assertThat(portfolioRepository.findById(OLD_DELETED_PORTFOLIO_ID)).isEmpty(); // Hard deleted
        assertThat(portfolioRepository.findById(RECENT_DELETED_PORTFOLIO_ID)).isPresent(); // Kept (soft deleted)
        assertThat(portfolioRepository.findById(ACTIVE_PORTFOLIO_ID)).isPresent(); // Kept (active)
    }
}