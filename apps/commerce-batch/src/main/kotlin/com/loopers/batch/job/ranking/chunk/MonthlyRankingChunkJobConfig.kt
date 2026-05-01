package com.loopers.batch.job.ranking.chunk

import com.loopers.batch.job.ranking.RankingScorePolicy
import com.loopers.batch.job.ranking.chunk.step.ProductMetricsScoreRow
import com.loopers.batch.job.ranking.chunk.step.ProductMetricsScoreRowMapper
import com.loopers.batch.listener.JobListener
import com.loopers.batch.listener.StepMonitorListener
import com.loopers.domain.ranking.PeriodPolicy
import com.loopers.infrastructure.persistence.ranking.MvProductRankMonthlyEntity
import com.loopers.infrastructure.persistence.ranking.MvProductRankMonthlyJpaRepository
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
 * 월간 랭킹 Materialized View 적재 (Chunk-Oriented 방식).
 *
 * 구조는 [WeeklyRankingChunkJobConfig] 와 동일하며, target 테이블/엔티티/period_key 포맷만 다르다.
 *
 * 의도적으로 별도 Config 로 분리한 이유:
 *  - Job 단위 격리 — 주간/월간이 같은 잡 안에 두 step 으로 묶이면 한쪽 실패가 다른 쪽 차단
 *  - 스케줄링 분리 — 주간/월간이 서로 다른 cron schedule 에 묶임
 *  - 가독성 — Reader/Processor 가 다른 entity 를 다루므로 generic 추출보다 복제가 단순
 */
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = MonthlyRankingChunkJobConfig.JOB_NAME)
@Configuration
class MonthlyRankingChunkJobConfig(
    private val jobRepository: JobRepository,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val txManager: PlatformTransactionManager,
    private val dataSource: DataSource,
    private val entityManagerFactory: EntityManagerFactory,
) {

    companion object {
        const val JOB_NAME = "monthlyRankingChunkJob"
        const val STEP_CLEAR = "clearMonthlyStep"
        const val STEP_AGGREGATE = "aggregateMonthlyStep"
        const val BEAN_CLEAR_TASKLET = "monthlyClearTasklet"
        const val BEAN_READER = "monthlyRankingReader"
        const val BEAN_PROCESSOR = "monthlyRankingProcessor"
        const val BEAN_WRITER = "monthlyRankingWriter"
        const val CHUNK_SIZE = 25
        const val TOP_N = 100
        private val log = LoggerFactory.getLogger(MonthlyRankingChunkJobConfig::class.java)
    }

    @Bean(JOB_NAME)
    fun monthlyRankingChunkJob(
        @Qualifier(STEP_CLEAR) clearStep: Step,
        @Qualifier(STEP_AGGREGATE) aggregateStep: Step,
    ): Job =
        JobBuilder(JOB_NAME, jobRepository)
            .incrementer(RunIdIncrementer())
            .start(clearStep)
            .next(aggregateStep)
            .listener(jobListener)
            .build()

    @JobScope
    @Bean(STEP_CLEAR)
    fun clearMonthlyStep(@Qualifier(BEAN_CLEAR_TASKLET) clearTasklet: Tasklet): Step =
        StepBuilder(STEP_CLEAR, jobRepository)
            // JPA @Modifying 쿼리는 실제 트랜잭션이 필요하므로 ResourcelessTxManager 사용 불가
            .tasklet(clearTasklet, txManager)
            .listener(stepMonitorListener)
            .build()

    @StepScope
    @Bean(BEAN_CLEAR_TASKLET)
    fun monthlyClearTasklet(
        @Value("#{jobParameters['baseDate']}") baseDate: LocalDate?,
        monthlyRepository: MvProductRankMonthlyJpaRepository,
    ): Tasklet = Tasklet { _, _ ->
        val periodKey = PeriodPolicy.yearMonth(baseDate ?: LocalDate.now())
        val deleted = monthlyRepository.deleteAllByPeriodKey(periodKey)
        log.info("[$JOB_NAME] cleared $deleted rows for periodKey=$periodKey")
        RepeatStatus.FINISHED
    }

    @JobScope
    @Bean(STEP_AGGREGATE)
    fun aggregateMonthlyStep(
        @Qualifier(BEAN_READER) reader: JdbcCursorItemReader<ProductMetricsScoreRow>,
        @Qualifier(BEAN_PROCESSOR) processor: ItemProcessor<ProductMetricsScoreRow, MvProductRankMonthlyEntity>,
        @Qualifier(BEAN_WRITER) writer: JpaItemWriter<MvProductRankMonthlyEntity>,
    ): Step =
        StepBuilder(STEP_AGGREGATE, jobRepository)
            .chunk<ProductMetricsScoreRow, MvProductRankMonthlyEntity>(CHUNK_SIZE, txManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .listener(stepMonitorListener)
            .build()

    @StepScope
    @Bean(BEAN_READER)
    fun monthlyRankingReader(): JdbcCursorItemReader<ProductMetricsScoreRow> {
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
    fun monthlyRankingProcessor(
        @Value("#{jobParameters['baseDate']}") baseDate: LocalDate?,
    ): ItemProcessor<ProductMetricsScoreRow, MvProductRankMonthlyEntity> {
        val periodKey = PeriodPolicy.yearMonth(baseDate ?: LocalDate.now())
        return ItemProcessor { row ->
            MvProductRankMonthlyEntity(
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
    fun monthlyRankingWriter(): JpaItemWriter<MvProductRankMonthlyEntity> =
        JpaItemWriterBuilder<MvProductRankMonthlyEntity>()
            .entityManagerFactory(entityManagerFactory)
            .build()
}
