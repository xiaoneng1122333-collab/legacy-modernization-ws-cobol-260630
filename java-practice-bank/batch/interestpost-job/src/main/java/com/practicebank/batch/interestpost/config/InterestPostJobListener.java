package com.practicebank.batch.interestpost.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

/**
 * Job 終了時に IPST-RUN-OUTPUT / IPST-REPORT-OUTPUT をログ出力する.
 */
public class InterestPostJobListener implements JobExecutionListener {

    private static final Logger LOG = LoggerFactory.getLogger(InterestPostJobListener.class);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        LOG.info("IPST-RUN-MONTHEND start: {}", jobExecution.getJobInstance().getJobName());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        int aggregated = jobExecution.getExecutionContext().getInt("ipst.aggregated", 0);
        int posted = jobExecution.getExecutionContext().getInt("ipst.posted", 0);
        long totalJpyCents = jobExecution.getExecutionContext().getLong("ipst.totalJpyCents", 0L);

        String status;
        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            status = "16"; // FATAL
        } else if (posted == 0) {
            status = "08"; // INVALID-INPUT (no posting)
        } else {
            status = "00"; // OK
        }
        LOG.info("IPST-JOB-OUTPUT: status={} aggregated={} posted={} totalJpyCents={}",
            status, aggregated, posted, totalJpyCents);
    }
}
