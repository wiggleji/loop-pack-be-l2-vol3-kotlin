package com.loopers.job.ranking.bench

import com.loopers.batch.job.ranking.chunk.WeeklyRankingChunkJobConfig
import com.loopers.support.ProductMetricsSeeder
import com.loopers.utils.DatabaseCleanUp
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.time.LocalDate

/**
 * Weekly Chunk Job 의 wall-time 벤치마크.
 *
 * 시드 사이즈를 바꿔가며 동일 환경(테스트컨테이너 MySQL)에서 측정해
 * [WeeklyRankingTaskletJobBenchmark] 와 비교 가능한 숫자를 제공한다.
 *
 * 출력 형식 (parsing 친화):
 *   `[BENCH] job=weeklyRankingChunkJob seed=10000 elapsedMs=1234 exit=COMPLETED`
 *
 * **NB**: TestContainer cold start 비용을 분리하기 위해 첫 번째 measurement 는 warm-up 으로
 * 한 번 더 돌리지 않고, 결과 비교 시에만 첫 row 를 무시할 것 (혹은 반복 실행해 평균).
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = ["spring.batch.job.name=${WeeklyRankingChunkJobConfig.JOB_NAME}"])
class WeeklyRankingChunkJobBenchmark @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @param:Qualifier(WeeklyRankingChunkJobConfig.JOB_NAME) private val job: Job,
    private val seeder: ProductMetricsSeeder,
    private val cleanUp: DatabaseCleanUp,
) {

    @BeforeEach
    fun setUp() {
        jobLauncherTestUtils.job = job
        cleanUp.truncateAllTables()
    }

    @AfterEach
    fun tearDown() {
        cleanUp.truncateAllTables()
    }

    @DisplayName("[BENCH] WeeklyRankingChunkJob — 시드 수별 wall time")
    @ParameterizedTest(name = "seed={0}")
    @ValueSource(ints = [1_000, 5_000, 10_000, 100_000, 300_000])
    fun bench(seed: Int) {
        seeder.seedRandom(count = seed)

        val params = JobParametersBuilder()
            .addLocalDate("baseDate", LocalDate.of(2026, 4, 14))
            .addLong("run", System.nanoTime())
            .toJobParameters()

        val started = System.nanoTime()
        val exec = jobLauncherTestUtils.launchJob(params)
        val elapsed = Duration.ofNanos(System.nanoTime() - started)

        check(exec.exitStatus.exitCode == ExitStatus.COMPLETED.exitCode) { "Job failed: ${exec.exitStatus}" }
        // 의도적으로 println — gradle test output 에 그대로 남아 grep 으로 추출 가능
        println("[BENCH] job=${WeeklyRankingChunkJobConfig.JOB_NAME} seed=$seed elapsedMs=${elapsed.toMillis()} exit=${exec.exitStatus.exitCode}")
    }
}
