package com.practicebank.batch.fee.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

/**
 * Job 終了時に FEE-CHARGE-OUTPUT / FEE-REPORT-OUTPUT をログ出力する.
 */
public class FeeChargeJobListener implements JobExecutionListener {

    private static final Logger LOG = LoggerFactory.getLogger(FeeChargeJobListener.class);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        LOG.info("FEE-CHARGE start: {}", jobExecution.getJobInstance().getJobName());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        int scanned = jobExecution.getExecutionContext().getInt("fee.scanned", 0);
        int posted = jobExecution.getExecutionContext().getInt("fee.posted", 0);
        int skippedNoFee = jobExecution.getExecutionContext().getInt("fee.skippedNoFee", 0);
        int skippedClosed = jobExecution.getExecutionContext().getInt("fee.skippedClosed", 0);
        int skippedNsf = jobExecution.getExecutionContext().getInt("fee.skippedNsf", 0);
        int skippedAlready = jobExecution.getExecutionContext().getInt("fee.skippedAlready", 0);
        int skippedHelper = jobExecution.getExecutionContext().getInt("fee.skippedHelper", 0);
        long totalFeeJpyCents = jobExecution.getExecutionContext().getLong("fee.totalFeeJpyCents", 0L);

        String status;
        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            status = "16"; // FATAL
        } else if (posted == 0 && scanned > 0) {
            status = "08"; // INVALID-INPUT (no posting)
        } else if (skippedNoFee + skippedClosed + skippedNsf + skippedAlready + skippedHelper > 0) {
            status = "04"; // PARTIAL
        } else {
            status = "00"; // OK
        }
        LOG.info("FEE-JOB-OUTPUT: status={} scanned={} posted={} noFee={} closed={} nsf={} already={} helper={} totalFeeJpyCents={}",
            status, scanned, posted, skippedNoFee, skippedClosed, skippedNsf, skippedAlready, skippedHelper, totalFeeJpyCents);
    }
}
