package com.practicebank.batch.operations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * OPS-BATCH-DAILY 相当の Job リスナー.
 * 開始時に OPS_BATCH_START 監査を、終了時に成否に応じて OPS_BATCH_OK / FAIL を記録する.
 */
@Component
public class OpsJobListener implements JobExecutionListener {

    private static final Logger LOG = LoggerFactory.getLogger(OpsJobListener.class);

    private final OpsAudit audit;
    private final String defaultBatchId;
    private final String defaultBusinessDate;

    public OpsJobListener(OpsAudit audit,
                          @Value("${ops.batch-id:}") String defaultBatchId,
                          @Value("${ops.business-date:}") String defaultBusinessDate) {
        this.audit = audit;
        this.defaultBatchId = defaultBatchId;
        this.defaultBusinessDate = defaultBusinessDate;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        String batchId = resolveBatchId(jobExecution);
        String bdate = resolveBusinessDate(jobExecution);
        audit.writeJob(batchId, bdate, OpsAudit.EVT_BATCH_START);
        LOG.info("OPS-BATCH-DAILY start batch={} businessDate={} dryRun={}",
            batchId, bdate, jobExecution.getJobParameters().getString("dryRun", "N"));
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String batchId = resolveBatchId(jobExecution);
        String bdate = resolveBusinessDate(jobExecution);
        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            audit.writeJob(batchId, bdate, OpsAudit.EVT_BATCH_OK);
            LOG.info("OPS-BATCH-DAILY done status=00 batch={}", batchId);
        } else {
            audit.writeJob(batchId, bdate, OpsAudit.EVT_BATCH_FAIL);
            LOG.warn("OPS-BATCH-DAILY done status={} batch={}", OpsAudit.statusOf(jobExecution), batchId);
        }
    }

    private String resolveBatchId(JobExecution je) {
        String fromParam = je.getJobParameters().getString("ops.batchId");
        if (fromParam != null && !fromParam.isBlank()) return fromParam;
        if (defaultBatchId != null && !defaultBatchId.isBlank()) return defaultBatchId;
        return "BATCH" + je.getJobInstance().getInstanceId();
    }

    private String resolveBusinessDate(JobExecution je) {
        String fromParam = je.getJobParameters().getString("ops.businessDate");
        if (fromParam != null && !fromParam.isBlank()) return fromParam;
        return defaultBusinessDate == null ? "" : defaultBusinessDate;
    }
}
