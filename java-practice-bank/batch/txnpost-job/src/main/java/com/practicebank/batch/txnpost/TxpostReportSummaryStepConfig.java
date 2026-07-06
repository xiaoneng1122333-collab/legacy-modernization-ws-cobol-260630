package com.practicebank.batch.txnpost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * TXPOST-REPORT-SUMMARY 相当の Spring Batch Step.
 *
 * <p>transactions / postings / balances から status 別件数 (PT / SE / RV / 全体) を取得し、
 * 保存性不変量 (PT + SE + RV == 全体) を検証したうえでレポートファイルを出力する.
 */
@Configuration
public class TxpostReportSummaryStepConfig {

    private static final Logger LOG = LoggerFactory.getLogger(TxpostReportSummaryStepConfig.class);

    @Value("${txpost.business.date:#{null}}")
    private String businessDate;

    @Value("${txpost.report.filename:/tmp/txpost-summary-report.txt}")
    private String reportFilename;

    @Bean
    public Step txpostReportSummary(JobRepository jobRepository,
                                     PlatformTransactionManager txManager,
                                     DataSource dataSource) {
        return new StepBuilder("txpostReportSummary", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                JdbcTemplate jdbc = new JdbcTemplate(dataSource);

                // PT / SE / RV / 全体 の 4 回 COUNT(*)
                Integer ptCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM transactions WHERE status = 'PT'", Integer.class);
                ptCount = (ptCount == null) ? 0 : ptCount;

                Integer seCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM transactions WHERE status = 'SE'", Integer.class);
                seCount = (seCount == null) ? 0 : seCount;

                Integer rvCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM transactions WHERE status = 'RV'", Integer.class);
                rvCount = (rvCount == null) ? 0 : rvCount;

                Integer grandTotal = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM transactions", Integer.class);
                grandTotal = (grandTotal == null) ? 0 : grandTotal;

                // 保存性不変量: PT + SE + RV == 全体
                boolean conservationPass = (ptCount + seCount + rvCount) == grandTotal;

                // postings 件数 (dual-entry なので transactions * 2 相当)
                Integer pstTotal = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM postings", Integer.class);
                pstTotal = (pstTotal == null) ? 0 : pstTotal;

                LOG.info("TXPOST-REPORT-SUMMARY: PT={} SE={} RV={} GRAND={} PST_TOTAL={} conservation={}",
                    ptCount, seCount, rvCount, grandTotal, pstTotal, conservationPass ? "Y" : "N");

                // レポート出力
                int linesWritten = writeReport(ptCount, seCount, rvCount, grandTotal, pstTotal, conservationPass);

                // Job ExecutionContext に結果を設定
                ExecutionContext jobCtx = chunkContext.getStepContext()
                    .getStepExecution().getJobExecution().getExecutionContext();
                jobCtx.putInt("txpost.pt", ptCount);
                jobCtx.putInt("txpost.se", seCount);
                jobCtx.putInt("txpost.rv", rvCount);
                jobCtx.putInt("txpost.grand", grandTotal);
                jobCtx.putInt("txpost.pstTotal", pstTotal);
                jobCtx.putInt("txpost.linesWritten", linesWritten);
                jobCtx.put("txpost.conservationOk", conservationPass ? "Y" : "N");

                return RepeatStatus.FINISHED;
            }, txManager)
            .build();
    }

    private int writeReport(int pt, int se, int rv, int grand, int pstTotal, boolean conservationPass) {
        Path path = Paths.get(reportFilename);
        try (BufferedWriter w = Files.newBufferedWriter(path)) {
            w.write("TXPOST-BATCH-SUMMARY-REPORT");
            w.newLine();
            w.write("=========================");
            w.newLine();
            w.write("TRANSACTIONS_PT=" + pt);
            w.newLine();
            w.write("TRANSACTIONS_SE=" + se);
            w.newLine();
            w.write("TRANSACTIONS_RV=" + rv);
            w.newLine();
            w.write("TRANSACTIONS_GRAND=" + grand);
            w.newLine();
            w.write("POSTINGS_TOTAL=" + pstTotal);
            w.newLine();
            w.write("CONSERVATION_OK=" + (conservationPass ? "Y" : "N"));
            w.newLine();
            w.write("---END---");
            w.newLine();
            LOG.info("TXPOST-REPORT-SUMMARY report written to {}", reportFilename);
            return 8; // 8 行固定
        } catch (IOException e) {
            LOG.error("Failed to write TXPOST report to {}: {}", reportFilename, e.getMessage(), e);
            throw new RuntimeException("TXPOST report file write failed: " + reportFilename, e);
        }
    }
}
