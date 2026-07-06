package com.practicebank.batch.txnpost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

/**
 * Job 終了時に TXPOST-RUN-OUTPUT + TXPOST-REPORT-OUTPUT 相当の集計をログ出力する.
 */
public class TxpostSummaryListener implements JobExecutionListener {

    private static final Logger LOG = LoggerFactory.getLogger(TxpostSummaryListener.class);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        LOG.info("TXPOST-RUN-BATCH start: {}", jobExecution.getJobInstance().getJobName());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        var ctx = jobExecution.getExecutionContext();

        // TXPOST-RUN-OUTPUT 相当 (from Step 1)
        int read = ctx.getInt("txpost.read", 0);
        int posted = ctx.getInt("txpost.posted", 0);
        int alreadySkipped = ctx.getInt("txpost.alreadySkipped", 0);
        int hardRejected = ctx.getInt("txpost.hardRejected", 0);
        int reconDeferred = ctx.getInt("txpost.reconDeferred", 0);
        int inDoubtResolved = ctx.getInt("txpost.inDoubtResolved", 0);
        int dormancyDeferred = ctx.getInt("txpost.dormancyDeferred", 0);

        // TXPOST-REPORT-OUTPUT 相当 (from Step 3)
        int pt = ctx.getInt("txpost.pt", 0);
        int se = ctx.getInt("txpost.se", 0);
        int rv = ctx.getInt("txpost.rv", 0);
        int grand = ctx.getInt("txpost.grand", 0);
        int pstTotal = ctx.getInt("txpost.pstTotal", 0);
        int linesWritten = ctx.getInt("txpost.linesWritten", 0);
        String conservationOk = (String) ctx.get("txpost.conservationOk");
        if (conservationOk == null) conservationOk = "N";

        // 終了コード決定 (COBOL 88 値相当: TXPR-STATUS)
        String exitCode;
        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            exitCode = "16"; // FATAL
        } else if (posted == 0 && read > 0) {
            exitCode = "04"; // PARTIAL-RECON
        } else {
            exitCode = "00"; // OK
        }

        LOG.info("TXPOST-RUN-OUTPUT: status={} read={} posted={} alreadySkipped={} hardRejected={} " +
                 "reconDeferred={} inDoubtResolved={} dormancyDeferred={}",
            exitCode, read, posted, alreadySkipped, hardRejected,
            reconDeferred, inDoubtResolved, dormancyDeferred);

        LOG.info("TXPOST-REVERSE-OUTPUT: status={}", ctx.get("txpost.reverseStatus"));

        LOG.info("TXPOST-REPORT-OUTPUT: status={} PT={} SE={} RV={} GRAND={} POSTINGS={} " +
                 "CONSERVATION_OK={} LINES_WRITTEN={}",
            exitCode, pt, se, rv, grand, pstTotal, conservationOk, linesWritten);
    }
}
