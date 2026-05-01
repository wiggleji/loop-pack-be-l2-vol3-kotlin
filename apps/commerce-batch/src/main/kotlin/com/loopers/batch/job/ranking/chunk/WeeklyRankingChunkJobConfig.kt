package com.loopers.batch.job.ranking.chunk

import com.loopers.batch.job.ranking.RankingScorePolicy
import com.loopers.batch.job.ranking.chunk.step.ProductMetricsScoreRow
import com.loopers.batch.job.ranking.chunk.step.ProductMetricsScoreRowMapper
import com.loopers.batch.listener.JobListener
import com.loopers.batch.listener.StepMonitorListener
import com.loopers.domain.ranking.PeriodPolicy
import com.loopers.infrastructure.persistence.ranking.MvProductRankWeeklyEntity
import com.loopers.infrastructure.persistence.ranking.MvProductRankWeeklyJpaRepository
import jakarta.persistence.EntityManagerFactory
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
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.database.JdbcCursorItemReader
import org.springframework.batch.item.database.JpaItemWriter
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDate
import javax.sql.DataSource

/**
 * 주간 랭킹 Materialized View 적재 (Chunk-Oriented 방식).
 *
 * 실행 예:
 *   `--job.name=weeklyRankingChunkJob -baseDate=2026-04-14`
 *
 * Job Parameters:
 *  - `baseDate` (yyyy-MM-dd) : 어느 날짜의 ISO 주차로 적재할지. 미지정 시 today.
 *
 * Steps:
 *  1. `clearWeeklyStep` (Tasklet) : 동일 `period_key` 의 기존 row 삭제 (idempotent 보장)
 *  2. `aggregateWeeklyStep` (Chunk) :
 *      - Reader   : `JdbcCursorItemReader` — `product_metrics` 에서 점수 계산 + ROW_NUMBER 부여 + LIMIT 100
 *      - Processor: row → `MvProductRankWeeklyEntity` (period_key 주입)
 *      - Writer   : `JpaItemWriter` (insert)
 *
 * Why chunk size 25 (not 100)?
 *  - TOP 100 이라는 작은 데이터셋이지만 청크가 4번 돌면서 chunk-level TX 가 정상 동작함을 검증할 수 있다.
 *  - 운영 시나리오로 확장하면(e.g. TOP 10,000) 자연스럽게 늘리면 된다.
 */
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = WeeklyRankingChunkJobConfig.JOB_NAME)
@Configuration
class WeeklyRankingChunkJobConfig(
    private val jobRepository: JobRepository,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val txManager: PlatformTransactionManager,
    private val dataSource: DataSource,
    private val entityManagerFactory: EntityManagerFactory,
) {

    companion object {
        const val JOB_NAME = "weeklyRankingChunkJob"
        const val STEP_CLEAR = "clearWeeklyStep"
        const val STEP_AGGREGATE = "aggregateWeeklyStep"
        const val BEAN_CLEAR_TASKLET = "weeklyClearTasklet"
        const val BEAN_READER = "weeklyRankingReader"
        const val BEAN_PROCESSOR = "weeklyRankingProcessor"
        const val BEAN_WRITER = "weeklyRankingWriter"
        const val CHUNK_SIZE = 25
        const val TOP_N = 100
        private val log = LoggerFactory.getLogger(WeeklyRankingChunkJobConfig::class.java)
    }

    @Bean(JOB_NAME)
    fun weeklyRankingChunkJob(
        @Qualifier(STEP_CLEAR) clearStep: Step,
        @Qualifier(STEP_AGGREGATE) aggregateStep: Step,
    ): Job =
        JobBuilder(JOB_NAME, jobRepository)
            .incrementer(RunIdIncrementer())
            .start(clearStep)
            .next(aggregateStep)
            .listener(jobListener)
            .build()

    // ─────────────────────────────────────────────────────────────
    // Step 1. 기존 row 삭제 (재실행 idempotency)
    // ─────────────────────────────────────────────────────────────

    @JobScope
    @Bean(STEP_CLEAR)
    fun clearWeeklyStep(@Qualifier(BEAN_CLEAR_TASKLET) clearTasklet: Tasklet): Step =
        StepBuilder(STEP_CLEAR, jobRepository)
            // JPA @Modifying 쿼리는 실제 트랜잭션이 필요하므로 ResourcelessTxManager 사용 불가
            .tasklet(clearTasklet, txManager)
            .listener(stepMonitorListener)
            .build()

    @StepScope
    @Bean(BEAN_CLEAR_TASKLET)
    fun weeklyClearTasklet(
        @Value("#{jobParameters['baseDate']}") baseDate: LocalDate?,
        weeklyRepository: MvProductRankWeeklyJpaRepository,
    ): Tasklet = Tasklet { _, _ ->
        val periodKey = PeriodPolicy.yearWeek(baseDate ?: LocalDate.now())
        val deleted = weeklyRepository.deleteAllByPeriodKey(periodKey)
        log.info("[$JOB_NAME] cleared $deleted rows for periodKey=$periodKey")
        RepeatStatus.FINISHED
    }

    // ─────────────────────────────────────────────────────────────
    // Step 2. 점수 집계 + TOP-N 적재 (Chunk)
    // ─────────────────────────────────────────────────────────────

    @JobScope
    @Bean(STEP_AGGREGATE)
    fun aggregateWeeklyStep(
        @Qualifier(BEAN_READER) reader: JdbcCursorItemReader<ProductMetricsScoreRow>,
        @Qualifier(BEAN_PROCESSOR) processor: ItemProcessor<ProductMetricsScoreRow, MvProductRankWeeklyEntity>,
        @Qualifier(BEAN_WRITER) writer: JpaItemWriter<MvProductRankWeeklyEntity>,
    ): Step =
        StepBuilder(STEP_AGGREGATE, jobRepository)
            .chunk<ProductMetricsScoreRow, MvProductRankWeeklyEntity>(CHUNK_SIZE, txManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .listener(stepMonitorListener)
            .build()

    @StepScope
    @Bean(BEAN_READER)
    fun weeklyRankingReader(): JdbcCursorItemReader<ProductMetricsScoreRow> {
        // SQL 에서 점수 계산 + ROW_NUMBER 로 1-based rank 부여 + LIMIT TOP_N
        // tie-break: product_id ASC — 동일 점수 row 도 결정적으로 순서 부여
        val sql = """
            SELECT product_id,
                   ${RankingScorePolicy.SCORE_EXPR} AS score,
                   ROW_NUMBER() OVER (ORDER BY ${RankingScorePolicy.SCORE_EXPR} DESC, product_id ASC) AS rank_no,
                   view_count,
                   like_count,
                   sales_count,
                   sales_amount
            FROM product_metrics
            ORDER BY ${RankingScorePolicy.SCORE_EXPR} DESC, product_id ASC
            LIMIT $TOP_N
        """.trimIndent()

        return JdbcCursorItemReaderBuilder<ProductMetricsScoreRow>()
            .name(BEAN_READER)
            .dataSource(dataSource)
            .sql(sql)
            .rowMapper(ProductMetricsScoreRowMapper())
            .fetchSize(CHUNK_SIZE)
            .build()
    }

    @StepScope
    @Bean(BEAN_PROCESSOR)
    fun weeklyRankingProcessor(
        @Value("#{jobParameters['baseDate']}") baseDate: LocalDate?,
    ): ItemProcessor<ProductMetricsScoreRow, MvProductRankWeeklyEntity> {
        val periodKey = PeriodPolicy.yearWeek(baseDate ?: LocalDate.now())
        return ItemProcessor { row ->
            MvProductRankWeeklyEntity(
                productId = row.productId,
                rank = row.rank,
                score = row.score,
                viewCount = row.viewCount,
                likeCount = row.likeCount,
                salesCount = row.salesCount,
                salesAmount = row.salesAmount,
                periodKey = periodKey,
            )
        }
    }

    @Bean(BEAN_WRITER)
    fun weeklyRankingWriter(): JpaItemWriter<MvProductRankWeeklyEntity> =
        JpaItemWriterBuilder<MvProductRankWeeklyEntity>()
            .entityManagerFactory(entityManagerFactory)
            .build()
}
