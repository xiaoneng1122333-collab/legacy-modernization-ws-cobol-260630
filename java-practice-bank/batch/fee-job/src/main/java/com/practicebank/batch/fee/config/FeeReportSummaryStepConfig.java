package com.practicebank.batch.fee.config;

import com.practicebank.batch.fee.domain.FeeReportSummary;
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
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * FEE-REPORT-SUMMARY 相当の Spring Batch Step.
 *
 * <p>transactions と balances (費用水上口座) をクロス照合し保存量 (conservation) を検証する.
 * 結果を JobExecutionContext に格納し、レポートファイル (LINE SEQUENTIAL 120 桁) を出力する.</p>
 */
@Configuration
public class FeeReportSummaryStepConfig {

    private static final Logger LOG = LoggerFactory.getLogger(FeeReportSummaryStepConfig.class);

    @Value("${fee.charge.batch.id:#{null}}")
    private String configuredBatchId;

    @Value("${fee.charge.fee-rev-account:0010010000004}")
    private String feeRevAccount;

    @Value("${fee.report.filename:#{null}}")
    private String reportFilename;

    @Bean
    public Step feeReportSummary(JobRepository jobRepository,
                                 PlatformTransactionManager txManager,
                                 DataSource dataSource) {
        final JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        return new StepBuilder("feeReportSummary", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                LocalDateTime start = LocalDateTime.now();
                String batchId = configuredBatchId != null ? configuredBatchId : "";

                // Q1: transactions から FEE 系 PT 件数・金額合計
                BigDecimal txnSum;
                Integer ptCount;
                if (batchId.isBlank()) {
                    txnSum = jdbc.queryForObject(
                        "SELECT COALESCE(SUM(amount_jpy), 0) FROM transactions " +
                        "WHERE source_system = 'FEE' AND status = 'PT'",
                        BigDecimal.class);
                    ptCount = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM transactions " +
                        "WHERE source_system = 'FEE' AND status = 'PT'",
                        Integer.class);
                } else {
                    txnSum = jdbc.queryForObject(
                        "SELECT COALESCE(SUM(amount_jpy), 0) FROM transactions " +
                        "WHERE source_system = 'FEE' AND status = 'PT' AND source_batch_id = ?",
                        BigDecimal.class, batchId);
                    ptCount = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM transactions " +
                        "WHERE source_system = 'FEE' AND status = 'PT' AND source_batch_id = ?",
                        Integer.class, batchId);
                }

                // Q2: balances から費用水上口座の現在高
                BigDecimal feeRevBalance;
                try {
                    feeRevBalance = jdbc.queryForObject(
                        "SELECT balance_jpy FROM balances WHERE account_number = ?",
                        BigDecimal.class, feeRevAccount);
                } catch (org.springframework.dao.EmptyResultDataAccessException e) {
                    feeRevBalance = BigDecimal.ZERO;
                }

                // conservation 判定
                BigDecimal safeTxnSum = txnSum != null ? txnSum : BigDecimal.ZERO;
                BigDecimal safeFeeRevBal = feeRevBalance != null ? feeRevBalance : BigDecimal.ZERO;
                boolean conservationPass = safeTxnSum.compareTo(safeFeeRevBal) == 0;
                String conservationFlag = conservationPass ? "Y" : "N";

                long durationSec = ChronoUnit.SECONDS.between(start, LocalDateTime.now());
                String status = conservationPass ? FeeReportSummary.OK : FeeReportSummary.CONSERVATION_WARN;

                FeeReportSummary summary = new FeeReportSummary(
                    status,
                    ptCount != null ? ptCount : 0,
                    safeTxnSum,
                    safeFeeRevBal,
                    conservationFlag,
                    durationSec
                );

                // JobExecutionContext に保存
                chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                    .put("fee.report.summary", summary);

                // レポートファイル出力 (LINE SEQUENTIAL 120 桁)
                writeReportFile(summary, batchId);

                LOG.info("FEE-REPORT-OUTPUT: status={} charges={} totalFeeJpy={} feeRevBalance={} " +
                        "conservation={} durationSec={}",
                    summary.status(), summary.totalCharges(), summary.totalFeeJpy(),
                    summary.feeRevBalance(), summary.conservationPass(), summary.durationSec());

                return RepeatStatus.FINISHED;
            }, txManager)
            .build();
    }

    /**
     * 7 行の FEE Daily Charge Report を出力.
     * 行長 120 桁固定 (LINE SEQUENTIAL).
     */
    private void writeReportFile(FeeReportSummary summary, String batchId) {
        if (reportFilename == null || reportFilename.isBlank()) {
            LOG.info("FEE-REPORT: report_filename not configured, skip file write");
            return;
        }
        try {
            Path path = Paths.get(reportFilename);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (BufferedWriter w = Files.newBufferedWriter(path)) {
                w.write("=== FEE Daily Charge Report ===");
                w.newLine();
                w.write(String.format("Batch ID:        %s", batchId));
                w.newLine();
                w.write(String.format("PT count:        %d", summary.totalCharges()));
                w.newLine();
                w.write(String.format("Txn JPY sum:     %s", summary.totalFeeJpy().toPlainString()));
                w.newLine();
                w.write(String.format("Fee rev balance: %s", summary.feeRevBalance().toPlainString()));
                w.newLine();
                w.write(String.format("Conservation:    %s", summary.conservationPass()));
                w.newLine();
                w.write(String.format("Status:          %s", summary.status()));
                w.newLine();
            }
            LOG.info("FEE-REPORT: report written to {}", reportFilename);
        } catch (IOException e) {
            LOG.error("FEE-REPORT: failed to write report file {}: {}", reportFilename, e.getMessage(), e);
        }
    }
}
