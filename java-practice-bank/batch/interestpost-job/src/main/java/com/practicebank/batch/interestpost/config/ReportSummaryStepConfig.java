package com.practicebank.batch.interestpost.config;

import com.practicebank.batch.interestpost.domain.ReportSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * IPST-REPORT-SUMMARY 相当の Spring Batch Step.
 *
 * <p>transactions と interest_accruals をクロス照合し保存量 (conservation) を検証する.
 * 結果を JobExecutionContext に格納し、帳票ファイル出力は Phase 2 別タスクで接続する.</p>
 */
@Configuration
public class ReportSummaryStepConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ReportSummaryStepConfig.class);

    @Value("${interestpost.run.batch.id:#{null}}")
    private String configuredBatchId;

    @Bean
    public Step reportSummary(JobRepository jobRepository,
                              PlatformTransactionManager txManager,
                              DataSource dataSource) {
        final JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        return new StepBuilder("reportSummary", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                LocalDateTime start = LocalDateTime.now();
                String batchId = configuredBatchId != null ? configuredBatchId : "";

                // Q1: transactions PT 行数・金額合計 (source_batch_id 指定時は限定)
                BigDecimal txnSum;
                Integer ptCount;
                if (batchId.isBlank()) {
                    txnSum = jdbc.queryForObject(
                        "SELECT COALESCE(SUM(amount_jpy), 0) FROM transactions WHERE status = 'PT'",
                        BigDecimal.class);
                    ptCount = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM transactions WHERE status = 'PT'",
                        Integer.class);
                } else {
                    txnSum = jdbc.queryForObject(
                        "SELECT COALESCE(SUM(amount_jpy), 0) FROM transactions " +
                        "WHERE status = 'PT' AND source_batch_id = ?",
                        BigDecimal.class, batchId);
                    ptCount = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM transactions " +
                        "WHERE status = 'PT' AND source_batch_id = ?",
                        Integer.class, batchId);
                }

                // Q2: interest_accruals AC 残行数
                Integer acRemaining = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM interest_accruals WHERE status = 'AC'",
                    Integer.class);

                // Q3: interest_accruals PT 金額合計
                BigDecimal accruedSum = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(accrued_jpy), 0) FROM interest_accruals WHERE status = 'PT'",
                    BigDecimal.class);

                // conservation 判定
                boolean conservationPass = txnSum != null && accruedSum != null
                    && txnSum.compareTo(accruedSum) == 0;

                long durationSec = ChronoUnit.SECONDS.between(start, LocalDateTime.now());
                String status = conservationPass ? ReportSummary.OK : ReportSummary.CONSERVATION_WARN;

                ReportSummary summary = new ReportSummary(
                    status,
                    ptCount != null ? ptCount : 0,
                    txnSum != null ? txnSum : BigDecimal.ZERO,
                    ptCount != null ? ptCount : 0,
                    acRemaining != null ? acRemaining : 0,
                    accruedSum != null ? accruedSum : BigDecimal.ZERO,
                    conservationPass,
                    durationSec
                );

                // JobExecutionContext に保存
                chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                    .put("ipst.report.summary", summary);

                LOG.info("IPST-REPORT-OUTPUT: status={} ptCount={} txnSum={} acRemaining={} " +
                        "accruedSum={} conservation={} durationSec={}",
                    summary.status(), summary.totalPosted(), summary.totalPostedJpy(),
                    summary.acRemaining(), summary.accruedSum(), summary.conservationPass(),
                    summary.durationSec());

                return RepeatStatus.FINISHED;
            }, txManager)
            .build();
    }
}
