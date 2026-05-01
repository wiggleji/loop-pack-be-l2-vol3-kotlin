package com.loopers.batch.job.ranking.tasklet

import com.loopers.batch.job.ranking.RankingScorePolicy
import com.loopers.batch.listener.JobListener
import com.loopers.batch.listener.StepMonitorListener
import com.loopers.domain.ranking.PeriodPolicy
import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.JobScope
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDate

/**
 * 주간 랭킹 적재 — **Tasklet 비교군**.
 *
 * Chunk 변형 ([com.loopers.batch.job.ranking.chunk.WeeklyRankingChunkJobConfig]) 와 동일한 결과를 만들지만,
 * 단일 SQL `INSERT INTO mv_product_rank_weekly SELECT ... ORDER BY score DESC LIMIT 100` 로 처리한다.
 *
 * 비교 포인트:
 *  - **Throughput**: DB roundtrip 1 회 → 일반적으로 chunk 보다 빠르다 (chunk 는 fetch + insert 가 분리)
 *  - **Memory**: JVM 측 객체 생성이 거의 없음 (DB 가 sort/insert 를 모두 수행)
 *  - **Restartability**: 실패 시 step 전체 재실행 (chunk 의 chunk-level 회복은 불가)
 *  - **Throttling**: 한 트랜잭션이 너무 길어지면 lock 보유 시간이 늘어 운영 위험
 */
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = WeeklyRankingTaskletJobConfig.JOB_NAME)
@Configuration
class WeeklyRankingTaskletJobConfig(
    private val jobRepository: JobRepository,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val txManager: PlatformTransactionManager,
    private val jdbcTemplate: JdbcTemplate,
) {

    companion object {
        const val JOB_NAME = "weeklyRankingTaskletJob"
        const val STEP_AGGREGATE = "aggregateWeeklyTaskletStep"
        const val BEAN_TASKLET = "weeklyAggregateTasklet"
        const val TOP_N = 100
        private val log = LoggerFactory.getLogger(WeeklyRankingTaskletJobConfig::class.java)
    }

    @Bean(JOB_NAME)
    fun weeklyRankingTaskletJob(@Qualifier(STEP_AGGREGATE) step: Step): Job =
        JobBuilder(JOB_NAME, jobRepository)
            .incrementer(RunIdIncrementer())
            .start(step)
            .listener(jobListener)
            .build()

    @JobScope
    @Bean(STEP_AGGREGATE)
    fun aggregateWeeklyTaskletStep(@Qualifier(BEAN_TASKLET) tasklet: Tasklet): Step =
        StepBuilder(STEP_AGGREGATE, jobRepository)
            .tasklet(tasklet, txManager) // 실제 DB 변경이 일어나므로 ResourcelessTxManager 가 아닌 main txManager 사용
            .listener(stepMonitorListener)
            .build()

    @StepScope
    @Bean(BEAN_TASKLET)
    fun weeklyAggregateTasklet(
        @Value("#{jobParameters['baseDate']}") baseDate: LocalDate?,
    ): Tasklet = Tasklet { _, _ ->
        val periodKey = PeriodPolicy.yearWeek(baseDate ?: LocalDate.now())

        // 1) idempotency: 같은 periodKey 의 기존 row 삭제
        val deleted = jdbcTemplate.update(
            "DELETE FROM mv_product_rank_weekly WHERE period_key = ?",
            periodKey,
        )

        // 2) 점수 계산 + ROW_NUMBER + INSERT (DB-side single shot)
        val sql = """
            INSERT INTO mv_product_rank_weekly
                (product_id, rank_no, score,
                 view_count, like_count, sales_count, sales_amount,
                 period_key, created_at, updated_at)
            SELECT t.product_id,
                   t.rank_no,
                   t.score,
                   t.view_count,
                   t.like_count,
                   t.sales_count,
                   t.sales_amount,
                   ?, NOW(6), NOW(6)
            FROM (
                SELECT product_id,
                       ${RankingScorePolicy.SCORE_EXPR} AS score,
                       ROW_NUMBER() OVER (ORDER BY ${RankingScorePolicy.SCORE_EXPR} DESC, product_id ASC) AS rank_no,
                       view_count,
                       like_count,
                       sales_count,
                       sales_amount
                FROM product_metrics
            ) t
            WHERE t.rank_no <= $TOP_N
        """.trimIndent()
        val inserted = jdbcTemplate.update(sql, periodKey)

        log.info("[$JOB_NAME] periodKey=$periodKey deleted=$deleted inserted=$inserted")
        RepeatStatus.FINISHED
    }
}
