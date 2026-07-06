package com.practicebank.batch.interestaccrual;

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
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * IACR-REPORT-SUMMARY 相当の Spring Batch Step.
 *
 * <p>interest_accruals テーブルから status='AC' / status='PT' / 全体の 3 回 COUNT(*) を取得し、
 * AC+PT == 全体 の保存チェックを通したうえで、日次サマリレポートファイルを出力する.
 */
@Configuration
public class IacrReportSummaryStepConfig {

    private static final Logger LOG = LoggerFactory.getLogger(IacrReportSummaryStepConfig.class);

    @Value("${iacr.business.date:#{null}}")
    private String businessDate;

    @Value("${iacr.report.filename:/tmp/iacr-summary-report.txt}")
    private String reportFilename;

    @Bean
    public Step iacrReportSummary(JobRepository jobRepository,
                                   PlatformTransactionManager txManager,
                                   DataSource dataSource) {
        return new StepBuilder("iacrReportSummary", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                JdbcTemplate jdbc = new JdbcTemplate(dataSource);

                String bdate = businessDate != null ? businessDate : "20260101";
                String formattedDate = formatBusinessDate(bdate);

                // java.sql.Date に変換 (business_date カラムは date 型)
                Date sqlDate = Date.valueOf(formattedDate);

                // COUNT(*) WHERE status = 'AC'
                Integer acCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM interest_accruals WHERE status = 'AC' AND business_date = ?",
                    Integer.class, sqlDate);
                acCount = (acCount == null) ? 0 : acCount;

                // COUNT(*) WHERE status = 'PT'
                Integer ptCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM interest_accruals WHERE status = 'PT' AND business_date = ?",
                    Integer.class, sqlDate);
                ptCount = (ptCount == null) ? 0 : ptCount;

                // COUNT(*) 全体
                Integer grandTotal = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM interest_accruals WHERE business_date = ?",
                    Integer.class, sqlDate);
                grandTotal = (grandTotal == null) ? 0 : grandTotal;

                // 保存チェック: AC + PT == 全体
                boolean conservationPass = (acCount + ptCount) == grandTotal;

                LOG.info("IACR-REPORT-SUMMARY: AC={} PT={} GRAND={} conservation={}",
                    acCount, ptCount, grandTotal, conservationPass ? "Y" : "N");

                // レポートファイル出力 (7行)
                writeReport(bdate, acCount, ptCount, grandTotal, conservationPass);

                // Job ExecutionContext に結果を設定
                ExecutionContext jobCtx = chunkContext.getStepContext()
                    .getStepExecution().getJobExecution().getExecutionContext();
                jobCtx.putInt("iacr.ac", acCount);
                jobCtx.putInt("iacr.pt", ptCount);
                jobCtx.putInt("iacr.grand", grandTotal);
                jobCtx.put("iacr.conservationPass", conservationPass ? "Y" : "N");

                return RepeatStatus.FINISHED;
            }, txManager)
            .build();
    }

    private void writeReport(String bdate, int ac, int pt, int grand, boolean conservationPass) {
        Path path = Paths.get(reportFilename);
        try (BufferedWriter w = Files.newBufferedWriter(path)) {
            w.write("IACR-DAILY-INTEREST-SUMMARY");
            w.newLine();
            w.write("BUSINESS_DATE=" + bdate);
            w.newLine();
            w.write("AC-COUNT=" + ac);
            w.newLine();
            w.write("PT-COUNT=" + pt);
            w.newLine();
            w.write("GRAND-TOTAL=" + grand);
            w.newLine();
            w.write("CONSERVATION-PASS=" + (conservationPass ? "Y" : "N"));
            w.newLine();
            w.write("---END---");
            w.newLine();
            LOG.info("IACR-REPORT-SUMMARY report written to {}", reportFilename);
        } catch (IOException e) {
            LOG.error("Failed to write IACR report to {}: {}", reportFilename, e.getMessage(), e);
            throw new RuntimeException("IACR report file write failed: " + reportFilename, e);
        }
    }

    private static String formatBusinessDate(String bdate) {
        if (bdate != null && bdate.length() == 8) {
            // YYYYMMDD → YYYY-MM-DD for SQL DATE comparison
            return bdate.substring(0, 4) + "-" + bdate.substring(4, 6) + "-" + bdate.substring(6, 8);
        }
        return bdate;
    }
}
