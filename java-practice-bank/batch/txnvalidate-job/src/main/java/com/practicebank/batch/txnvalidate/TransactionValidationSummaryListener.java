package com.practicebank.batch.txnvalidate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

/** Job 終了時にバッチステータスと集計を出力 (TXVAL-REPORT-SUMMARY 続き). */
public class TransactionValidationSummaryListener implements JobExecutionListener {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionValidationSummaryListener.class);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        LOG.info("TXVAL-VALIDATE-BATCH start: {}", jobExecution.getJobInstance().getJobName());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        int processed = jobExecution.getExecutionContext().getInt("txval.processed", 0);
        int validated = jobExecution.getExecutionContext().getInt("txval.validated", 0);
        int rejected = jobExecution.getExecutionContext().getInt("txval.rejected", 0);

        String batchStatus;
        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            batchStatus = "16"; // FATAL
        } else if (rejected == 0) {
            batchStatus = "00"; // OK
        } else if (validated > 0) {
            batchStatus = "04"; // PARTIAL-REJECT
        } else {
            batchStatus = "08"; // INVALID-INPUT (全拒否)
        }

        LOG.info("TXVAL-BATCH-OUTPUT: status={} processed={} validated={} rejected={}",
            batchStatus, processed, validated, rejected);
    }
}
