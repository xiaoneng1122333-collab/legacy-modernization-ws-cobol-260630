package com.practicebank.batch.statement.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

/**
 * Job 終了時に STMT-OUTPUT をログ出力する.
 */
public class StmtJobListener implements JobExecutionListener {

    private static final Logger LOG = LoggerFactory.getLogger(StmtJobListener.class);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        LOG.info("STMT-GENERATE-BATCH start: {}", jobExecution.getJobInstance().getJobName());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        int processed = jobExecution.getExecutionContext().getInt("stmt.processed", 0);
        int empty = jobExecution.getExecutionContext().getInt("stmt.empty", 0);
        int skipped = jobExecution.getExecutionContext().getInt("stmt.skipped", 0);

        String status;
        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            status = "16"; // FATAL
        } else if (processed == 0) {
            status = "08"; // INVALID-INPUT (no processing)
        } else {
            status = "00"; // OK
        }

        LOG.info("STMT-JOB-OUTPUT: status={} processed={} empty={} skipped={}",
            status, processed, empty, skipped);
    }
}
