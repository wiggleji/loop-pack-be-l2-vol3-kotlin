package com.loopers.job.ranking

import com.loopers.batch.job.ranking.tasklet.MonthlyRankingTaskletJobConfig
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
 * Monthly Tasklet Job 의 E2E — periodKey 가 yyyy-MM 으로 적재되는지만 빠르게 본다.
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = ["spring.batch.job.name=${MonthlyRankingTaskletJobConfig.JOB_NAME}"])
class MonthlyRankingTaskletJobE2ETest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @param:Qualifier(MonthlyRankingTaskletJobConfig.JOB_NAME) private val job: Job,
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

    @DisplayName("tasklet monthly: seed 150개 → TOP 100 만 yyyy-MM periodKey 로 적재")
    @Test
    fun loads_top_100_for_monthly_via_tasklet() {
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
            { assertThat(rows).hasSize(MonthlyRankingTaskletJobConfig.TOP_N) },
            { assertThat(rows.map { it.rank }).isEqualTo((1..MonthlyRankingTaskletJobConfig.TOP_N).toList()) },
            {
                rows.windowed(2).forEach { (a, b) ->
                    assertThat(a.score).isGreaterThanOrEqualTo(b.score)
                }
            },
        )
    }
}
