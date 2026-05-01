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
 * 월간 랭킹 적재 — Tasklet 비교군. [WeeklyRankingTaskletJobConfig] 와 동일한 패턴.
 */
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = MonthlyRankingTaskletJobConfig.JOB_NAME)
@Configuration
class MonthlyRankingTaskletJobConfig(
    private val jobRepository: JobRepository,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val txManager: PlatformTransactionManager,
    private val jdbcTemplate: JdbcTemplate,
) {

    companion object {
        const val JOB_NAME = "monthlyRankingTaskletJob"
        const val STEP_AGGREGATE = "aggregateMonthlyTaskletStep"
        const val BEAN_TASKLET = "monthlyAggregateTasklet"
        const val TOP_N = 100
        private val log = LoggerFactory.getLogger(MonthlyRankingTaskletJobConfig::class.java)
    }

    @Bean(JOB_NAME)
    fun monthlyRankingTaskletJob(@Qualifier(STEP_AGGREGATE) step: Step): Job =
        JobBuilder(JOB_NAME, jobRepository)
            .incrementer(RunIdIncrementer())
            .start(step)
            .listener(jobListener)
            .build()

    @JobScope
    @Bean(STEP_AGGREGATE)
    fun aggregateMonthlyTaskletStep(@Qualifier(BEAN_TASKLET) tasklet: Tasklet): Step =
        StepBuilder(STEP_AGGREGATE, jobRepository)
            .tasklet(tasklet, txManager)
            .listener(stepMonitorListener)
            .build()

    @StepScope
    @Bean(BEAN_TASKLET)
    fun monthlyAggregateTasklet(
        @Value("#{jobParameters['baseDate']}") baseDate: LocalDate?,
    ): Tasklet = Tasklet { _, _ ->
        val periodKey = PeriodPolicy.yearMonth(baseDate ?: LocalDate.now())

        val deleted = jdbcTemplate.update(
            "DELETE FROM mv_product_rank_monthly WHERE period_key = ?",
            periodKey,
        )

        val sql = """
            INSERT INTO mv_product_rank_monthly
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
