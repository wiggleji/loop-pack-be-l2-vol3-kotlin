package com.loopers.job.ranking.bench

import com.loopers.batch.job.ranking.tasklet.WeeklyRankingTaskletJobConfig
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
 * Weekly Tasklet Job 의 wall-time 벤치마크 — Chunk 변형([WeeklyRankingChunkJobBenchmark]) 과 1:1 비교.
 *
 * Tasklet 변형은 단일 INSERT INTO ... SELECT ROW_NUMBER() ... LIMIT 100 으로 처리하므로
 * JVM 측 객체 생성/IO roundtrip 이 거의 없다. 일반적으로 chunk 변형보다 wall time 이 짧다.
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = ["spring.batch.job.name=${WeeklyRankingTaskletJobConfig.JOB_NAME}"])
class WeeklyRankingTaskletJobBenchmark @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @param:Qualifier(WeeklyRankingTaskletJobConfig.JOB_NAME) private val job: Job,
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

    @DisplayName("[BENCH] WeeklyRankingTaskletJob — 시드 수별 wall time")
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
        println("[BENCH] job=${WeeklyRankingTaskletJobConfig.JOB_NAME} seed=$seed elapsedMs=${elapsed.toMillis()} exit=${exec.exitStatus.exitCode}")
    }
}
