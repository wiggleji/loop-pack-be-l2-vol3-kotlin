package com.loopers.job.ranking

import com.loopers.batch.job.ranking.chunk.MonthlyRankingChunkJobConfig
import com.loopers.domain.ranking.PeriodPolicy
import com.loopers.infrastructure.persistence.ranking.MvProductRankMonthlyJpaRepository
import com.loopers.support.ProductMetricsSeeder
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import java.time.LocalDate

/**
 * Monthly Chunk Job 의 E2E 검증.
 *
 * Weekly 와 알고리즘은 동일하므로 monthly-specific 한 부분만 검증한다:
 *  1. periodKey 가 yyyy-MM 포맷이어야 한다 (PeriodPolicy.yearMonth)
 *  2. mv_product_rank_monthly 테이블에 적재된다
 *  3. rank/score 정합성은 weekly 와 동일한 로직이므로 1개 happy-path 만 본다
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = ["spring.batch.job.name=${MonthlyRankingChunkJobConfig.JOB_NAME}"])
class MonthlyRankingChunkJobE2ETest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @param:Qualifier(MonthlyRankingChunkJobConfig.JOB_NAME) private val job: Job,
    private val seeder: ProductMetricsSeeder,
    private val monthlyRepository: MvProductRankMonthlyJpaRepository,
    private val cleanUp: DatabaseCleanUp,
) {

    private val baseDate: LocalDate = LocalDate.of(2026, 4, 14)
    private val periodKey: String = PeriodPolicy.yearMonth(baseDate)

    @BeforeEach
    fun setUp() {
        jobLauncherTestUtils.job = job
        cleanUp.truncateAllTables()
    }

    @AfterEach
    fun tearDown() {
        cleanUp.truncateAllTables()
    }

    @DisplayName("monthly job: seed 150개 → TOP 100 만 적재 (yyyy-MM periodKey)")
    @Test
    fun loads_top_100_for_monthly_period() {
        seeder.seedRandom(count = 150)

        val execution = jobLauncherTestUtils.launchJob(
            JobParametersBuilder()
                .addLocalDate("baseDate", baseDate)
                .addLong("run", System.nanoTime())
                .toJobParameters(),
        )

        val rows = monthlyRepository.findAll().filter { it.periodKey == periodKey }.sortedBy { it.rank }
        assertAll(
            { assertThat(execution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
            { assertThat(periodKey).matches(Regex("\\d{4}-\\d{2}").toPattern()) },
            { assertThat(rows).hasSize(MonthlyRankingChunkJobConfig.TOP_N) },
            { assertThat(rows.map { it.rank }).isEqualTo((1..MonthlyRankingChunkJobConfig.TOP_N).toList()) },
            // 점수 단조 감소
            {
                rows.windowed(2).forEach { (a, b) ->
                    assertThat(a.score).isGreaterThanOrEqualTo(b.score)
                }
            },
        )
    }
}
