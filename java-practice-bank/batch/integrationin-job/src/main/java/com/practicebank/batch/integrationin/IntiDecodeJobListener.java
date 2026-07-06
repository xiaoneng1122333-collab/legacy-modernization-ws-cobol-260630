package com.practicebank.batch.integrationin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

/**
 * INTI-EMIT-AUDIT-START / END 相当。Job 終了時点で INTI-OUTPUT 集計をログ出力する。
 *
 * <p>実際の AUD-Write (監査ログ書き込み → DB) は Phase 2 後半で行う。
 */
public class IntiDecodeJobListener implements JobExecutionListener {

    private static final Logger LOG = LoggerFactory.getLogger(IntiDecodeJobListener.class);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        LOG.info("INTI-DECODE-BATCH start: {}", jobExecution.getJobInstance().getJobName());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void afterJob(JobExecution jobExecution) {
        IntiOutput output = (IntiOutput) jobExecution.getExecutionContext().get("inti.output");
        if (output == null) {
            output = IntiOutput.init();
        }
        String severity = (jobExecution.getStatus() == BatchStatus.FAILED) ? "W" : "I";
        LOG.info("INTI-OUTPUT status={} read={} decoded={} rejected={} rejectPct={} checksumMatch={} severity={}",
                output.status(), output.recordsRead(), output.detailsDecoded(),
                output.detailsRejected(), output.rejectPct(), output.checksumMatch(), severity);
    }
}
