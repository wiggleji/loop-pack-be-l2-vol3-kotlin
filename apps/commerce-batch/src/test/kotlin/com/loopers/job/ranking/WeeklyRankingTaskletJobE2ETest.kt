package com.loopers.job.ranking

import com.loopers.batch.job.ranking.tasklet.WeeklyRankingTaskletJobConfig
import com.loopers.domain.ranking.PeriodPolicy
import com.loopers.infrastructure.persistence.ranking.MvProductRankWeeklyJpaRepository
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
 * Weekly Tasklet Job 의 E2E 검증.
 *
 * Chunk 변형과 동일한 결과(rank 1..N, score 단조 감소, idempotent) 를 만들어야 한다.
 * 알고리즘 정확성은 [WeeklyRankingChunkJobE2ETest] 와 동일 — 여기서는 *Tasklet 으로도 동일 결과* 임을 보증한다.
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = ["spring.batch.job.name=${WeeklyRankingTaskletJobConfig.JOB_NAME}"])
class WeeklyRankingTaskletJobE2ETest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @param:Qualifier(WeeklyRankingTaskletJobConfig.JOB_NAME) private val job: Job,
    private val seeder: ProductMetricsSeeder,
    private val weeklyRepository: MvProductRankWeeklyJpaRepository,
    private val cleanUp: DatabaseCleanUp,
) {

    private val baseDate: LocalDate = LocalDate.of(2026, 4, 14)
    private val periodKey: String = PeriodPolicy.yearWeek(baseDate)

    @BeforeEach
    fun setUp() {
        jobLauncherTestUtils.job = job
        cleanUp.truncateAllTables()
    }

    @AfterEach
    fun tearDown() {
        cleanUp.truncateAllTables()
    }

    @DisplayName("tasklet weekly: seed 200개 → TOP 100 만 적재 (single-shot INSERT...SELECT)")
    @Test
    fun loads_top_100_in_order_via_tasklet() {
        val expectedScores = seeder.seedRandom(count = 200)
        val expectedTop100Ids = expectedScores
            .sortedWith(compareByDescending<Pair<Long, Double>> { it.second }.thenBy { it.first })
            .take(WeeklyRankingTaskletJobConfig.TOP_N)
            .map { it.first }

        val execution = jobLauncherTestUtils.launchJob(uniqueParams())

        val rows = weeklyRepository.findAll().filter { it.periodKey == periodKey }.sortedBy { it.rank }
        assertAll(
            { assertThat(execution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
            { assertThat(rows).hasSize(WeeklyRankingTaskletJobConfig.TOP_N) },
            { assertThat(rows.map { it.rank }).isEqualTo((1..WeeklyRankingTaskletJobConfig.TOP_N).toList()) },
            { assertThat(rows.map { it.productId }).isEqualTo(expectedTop100Ids) },
            {
                rows.windowed(2).forEach { (a, b) ->
                    assertThat(a.score).isGreaterThanOrEqualTo(b.score)
                }
            },
        )
    }

    @DisplayName("tasklet weekly: 재실행 시 동일 결과 (idempotent — DELETE + INSERT 가 같은 TX)")
    @Test
    fun idempotent_on_rerun() {
        seeder.seedRandom(count = 120)

        jobLauncherTestUtils.launchJob(uniqueParams())
        val firstSnapshot = snapshot()

        jobLauncherTestUtils.launchJob(uniqueParams())
        val secondSnapshot = snapshot()

        assertAll(
            { assertThat(secondSnapshot).isEqualTo(firstSnapshot) },
            { assertThat(weeklyRepository.countByPeriodKey(periodKey)).isEqualTo(WeeklyRankingTaskletJobConfig.TOP_N.toLong()) },
        )
    }

    private fun uniqueParams() =
        JobParametersBuilder()
            .addLocalDate("baseDate", baseDate)
            .addLong("run", System.nanoTime())
            .toJobParameters()

    private fun snapshot(): List<Triple<Long, Int, Double>> =
        weeklyRepository.findAll()
            .filter { it.periodKey == periodKey }
            .sortedBy { it.rank }
            .map { Triple(it.productId, it.rank, it.score) }
}
