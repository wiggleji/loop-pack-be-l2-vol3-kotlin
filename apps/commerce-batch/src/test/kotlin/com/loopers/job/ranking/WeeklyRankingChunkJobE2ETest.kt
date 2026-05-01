package com.loopers.job.ranking

import com.loopers.batch.job.ranking.RankingScorePolicy
import com.loopers.batch.job.ranking.chunk.WeeklyRankingChunkJobConfig
import com.loopers.domain.ranking.PeriodPolicy
import com.loopers.infrastructure.persistence.metrics.ProductMetricsJpaRepository
import com.loopers.infrastructure.persistence.ranking.MvProductRankWeeklyEntity
import com.loopers.infrastructure.persistence.ranking.MvProductRankWeeklyJpaRepository
import com.loopers.support.ProductMetricsSeeder
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
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
 * Chunk-Oriented 주간 랭킹 Job 의 End-to-End 검증.
 *
 * 검증 포인트:
 *  1. Job 이 정상 종료한다 (ExitStatus = COMPLETED)
 *  2. 적재된 row 수 ≤ TOP_N (= 100)
 *  3. rank_no 가 1, 2, 3 ... 으로 연속이다 (gap 없음)
 *  4. score 는 rank 와 단조 감소 — 정렬 정확성
 *  5. 같은 baseDate 로 재실행해도 결과가 동일 (멱등성, clearStep 검증)
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = ["spring.batch.job.name=${WeeklyRankingChunkJobConfig.JOB_NAME}"])
class WeeklyRankingChunkJobE2ETest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @param:Qualifier(WeeklyRankingChunkJobConfig.JOB_NAME) private val job: Job,
    private val seeder: ProductMetricsSeeder,
    private val metricsRepository: ProductMetricsJpaRepository,
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

    @DisplayName("seed 200개 → TOP 100 만 적재 (정렬 + rank 연속성 보장)")
    @Test
    fun loads_top_100_in_order() {
        // arrange: 200 개 seed → 100 개만 추려져야 한다
        val expectedScores = seeder.seedRandom(count = 200)
        val expectedTop100Ids = expectedScores
            .sortedWith(compareByDescending<Pair<Long, Double>> { it.second }.thenBy { it.first })
            .take(WeeklyRankingChunkJobConfig.TOP_N)
            .map { it.first }

        // act
        val execution = jobLauncherTestUtils.launchJob(uniqueParams())

        // assert
        val rows = weeklyRepository.findAll().filter { it.periodKey == periodKey }.sortedBy { it.rank }
        assertAll(
            { assertThat(execution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
            { assertThat(rows).hasSize(WeeklyRankingChunkJobConfig.TOP_N) },
            { assertThat(rows.map { it.rank }).isEqualTo((1..WeeklyRankingChunkJobConfig.TOP_N).toList()) },
            { assertThat(rows.map { it.productId }).isEqualTo(expectedTop100Ids) },
            // 점수 단조 감소
            {
                rows.windowed(2).forEach { (a, b) ->
                    assertThat(a.score).isGreaterThanOrEqualTo(b.score)
                }
            },
        )
    }

    @DisplayName("재실행 시 기존 row 가 삭제되고 동일 결과로 재적재된다 (idempotent)")
    @Test
    fun idempotent_on_rerun() {
        seeder.seedRandom(count = 150)

        // 1차 실행
        val firstParams = JobParametersBuilder()
            .addLocalDate("baseDate", baseDate)
            .addLong("run", 1L)
            .toJobParameters()
        val first = jobLauncherTestUtils.launchJob(firstParams)
        val firstSnapshot = weeklyRepository.findAll().filter { it.periodKey == periodKey }
            .sortedBy { it.rank }.snapshot()

        // 2차 실행 (run 파라미터만 다르게)
        val secondParams = JobParametersBuilder()
            .addLocalDate("baseDate", baseDate)
            .addLong("run", 2L)
            .toJobParameters()
        val second = jobLauncherTestUtils.launchJob(secondParams)
        val secondSnapshot = weeklyRepository.findAll().filter { it.periodKey == periodKey }
            .sortedBy { it.rank }.snapshot()

        assertAll(
            { assertThat(first.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
            { assertThat(second.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
            { assertThat(secondSnapshot).isEqualTo(firstSnapshot) },
            { assertThat(weeklyRepository.countByPeriodKey(periodKey)).isEqualTo(WeeklyRankingChunkJobConfig.TOP_N.toLong()) },
        )
    }

    @DisplayName("seed 50개 → 50개 모두 적재 (TOP_N 미만)")
    @Test
    fun loads_all_when_below_top_n() {
        val expected = seeder.seedRandom(count = 50)

        jobLauncherTestUtils.launchJob(uniqueParams())

        val rows = weeklyRepository.findAll().filter { it.periodKey == periodKey }
        assertThat(rows).hasSize(50)
        assertThat(rows.map { it.productId }.toSet()).isEqualTo(expected.map { it.first }.toSet())
    }

    @DisplayName("score 가 RankingScorePolicy 와 일치한다 (회귀 보호)")
    @Test
    fun score_matches_policy() {
        seeder.seedRandom(count = 30)

        jobLauncherTestUtils.launchJob(uniqueParams())

        val rows = weeklyRepository.findAll().filter { it.periodKey == periodKey }
        val sourceById = metricsRepository.findAll().associateBy { it.productId }

        rows.forEach { row ->
            val src = sourceById[row.productId]!!
            val expected = RankingScorePolicy.score(src.viewCount, src.likeCount, src.salesCount)
            // JVM Double 연산과 MySQL Double 연산의 reduction order 차이로 ULP 단위 오차가 날 수 있음.
            // 정책 일치 여부 검증이 목적이므로 충분히 작은 tolerance 로 비교.
            assertThat(row.score).isCloseTo(expected, Offset.offset(1e-9))
        }
    }

    /**
     * BATCH_JOB_INSTANCE 는 DatabaseCleanUp(JPA 메타데이터 기반)이 truncate 하지 않으므로,
     * 동일 (jobName, jobParameters) 로 두 번 실행하면 `JobInstanceAlreadyCompleteException` 발생.
     * → run = nanoTime() 으로 매 실행 unique JobInstance 보장.
     */
    private fun uniqueParams() =
        JobParametersBuilder()
            .addLocalDate("baseDate", baseDate)
            .addLong("run", System.nanoTime())
            .toJobParameters()

    /** snapshot 비교 전용 dataclass — version/createdAt 제외. */
    private data class Snapshot(
        val productId: Long,
        val rank: Int,
        val score: Double,
        val periodKey: String,
    )

    private fun List<MvProductRankWeeklyEntity>.snapshot(): List<Snapshot> =
        map { Snapshot(it.productId, it.rank, it.score, it.periodKey) }
}
