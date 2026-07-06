package com.practicebank.batch.interestaccrual;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

/**
 * Job 終了時に IACR-REPORT-OUTPUT 相当の集計を出力する.
 * IACR-RUN-OUTPUT + IACR-REPORT-OUTput をまとめてログ出力.
 */
public class IacrSummaryListener implements JobExecutionListener {

    private static final Logger LOG = LoggerFactory.getLogger(IacrSummaryListener.class);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        LOG.info("IACR-RUN-DAILY start: {}", jobExecution.getJobInstance().getJobName());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        var ctx = jobExecution.getExecutionContext();

        // IACR-RUN-OUTPUT 相当 (from Step 1)
        int scanned = ctx.getInt("iacr.scanned", 0);
        int inserted = ctx.getInt("iacr.inserted", 0);
        int iState = ctx.getInt("iacr.ineligibleState", 0);
        int iProd = ctx.getInt("iacr.ineligibleProd", 0);
        int iBal = ctx.getInt("iacr.ineligibleBalance", 0);
        int iRate = ctx.getInt("iacr.ineligibleRate", 0);
        int already = ctx.getInt("iacr.alreadyAccrued", 0);
        int sysSkipped = ctx.getInt("iacr.systemSkipped", 0);

        // IACR-REPORT-OUTPUT 相当 (from Step 2)
        int ac = ctx.getInt("iacr.ac", 0);
        int pt = ctx.getInt("iacr.pt", 0);
        int grand = ctx.getInt("iacr.grand", 0);
        String conservationPass = (String) ctx.get("iacr.conservationPass");
        if (conservationPass == null) conservationPass = "N";

        // 終了コード決定 (COBOL 88 値相当)
        String exitCode;
        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            exitCode = "16"; // FATAL
        } else if (inserted == 0 && scanned > 0) {
            exitCode = "04"; // PARTIAL (no records inserted despite scanning)
        } else {
            exitCode = "00"; // OK
        }

        LOG.info("IACR-RUN-OUTPUT: status={} scanned={} inserted={} ineligibleState={} " +
                 "ineligibleProd={} ineligibleBalance={} ineligibleRate={} " +
                 "alreadyAccrued={} systemSkipped={}",
            exitCode, scanned, inserted, iState, iProd, iBal, iRate, already, sysSkipped);

        LOG.info("IACR-REPORT-OUTPUT: status={} AC={} PT={} GRAND={} conservationPass={}",
            exitCode, ac, pt, grand, conservationPass);
    }
}
