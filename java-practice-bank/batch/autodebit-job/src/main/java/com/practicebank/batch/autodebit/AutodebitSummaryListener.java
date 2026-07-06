package com.practicebank.batch.autodebit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

/**
 * Job 終了時にバッチステータスと集計を出力 (AD-REPORT-SUMMARY 続き).
 */
public class AutodebitSummaryListener implements JobExecutionListener {

    private static final Logger LOG = LoggerFactory.getLogger(AutodebitSummaryListener.class);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        LOG.info("AD-RUN-DAILY start: {}", jobExecution.getJobInstance().getJobName());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        int processed = jobExecution.getExecutionContext().getInt("ad.processed", 0);
        int posted = jobExecution.getExecutionContext().getInt("ad.posted", 0);
        int failedNf = jobExecution.getExecutionContext().getInt("ad.failedNf", 0);
        int failedCl = jobExecution.getExecutionContext().getInt("ad.failedCl", 0);
        int failedSu = jobExecution.getExecutionContext().getInt("ad.failedSu", 0);
        int pgCount = jobExecution.getExecutionContext().getInt("ad.pgCount", 0);
        long totalJpy = jobExecution.getExecutionContext().getLong("ad.totalJpy", 0L);
        String conservationPass = jobExecution.getExecutionContext().getString("ad.conservationPass", "Y");

        // 返却コード (AD-RUN-STATUS 相当)
        String batchStatus;
        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            batchStatus = "12"; // FATAL
        } else if (failedNf + failedCl + failedSu == 0) {
            batchStatus = "00"; // OK
        } else if (posted > 0) {
            batchStatus = "04"; // PARTIAL
        } else {
            batchStatus = "08"; // INVALID-INPUT (全失敗)
        }

        LOG.info("AD-BATCH-OUTPUT: status={} processed={} posted={} failedNF={} failedCL={} failedSU={} " +
                "pgCount={} totalJpy={} conservationPass={}",
            batchStatus, processed, posted, failedNf, failedCl, failedSu,
            pgCount, totalJpy, conservationPass);
    }
}
