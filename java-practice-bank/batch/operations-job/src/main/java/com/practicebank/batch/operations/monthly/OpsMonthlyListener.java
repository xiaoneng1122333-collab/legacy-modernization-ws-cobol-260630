package com.practicebank.batch.operations.monthly;

import com.practicebank.batch.operations.OpsAudit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * OPS-BATCH-MONTHLY 相当の Job リスナー — OPS_MONTHLY_START / OPS_MONTHLY_OK / FAIL 監査.
 */
@Component
public class OpsMonthlyListener implements JobExecutionListener {

    private static final Logger LOG = LoggerFactory.getLogger(OpsMonthlyListener.class);

    private final OpsAudit audit;
    private final String defaultBatchId;
    private final String defaultBusinessDate;

    public OpsMonthlyListener(OpsAudit audit,
                              @Value("${ops.batch-id:}") String defaultBatchId,
                              @Value("${ops.business-date:}") String defaultBusinessDate) {
        this.audit = audit;
        this.defaultBatchId = defaultBatchId;
        this.defaultBusinessDate = defaultBusinessDate;
    }

    @Override
    public void beforeJob(JobExecution je) {
        String bid = resolve(je, "batchId", defaultBatchId);
        String bdate = resolve(je, "businessDate", defaultBusinessDate);
        audit.writeJob(bid, bdate, "OPS_MONTHLY_START");
        LOG.info("OPS-BATCH-MONTHLY start batch={} businessDate={}", bid, bdate);
    }

    @Override
    public void afterJob(JobExecution je) {
        String bid = resolve(je, "batchId", defaultBatchId);
        String bdate = resolve(je, "businessDate", defaultBusinessDate);
        if (je.getStatus() == BatchStatus.COMPLETED) {
            audit.writeJob(bid, bdate, "OPS_MONTHLY_OK");
            LOG.info("OPS-BATCH-MONTHLY done=OK batch={}", bid);
        } else {
            audit.writeJob(bid, bdate, "OPS_MONTHLY_FAIL");
            LOG.warn("OPS-BATCH-MONTHLY done=FAIL batch={} status={}", bid, je.getStatus());
        }
    }

    private String resolve(JobExecution je, String key, String fallback) {
        String v = je.getJobParameters().getString("ops." + key);
        if (v != null && !v.isBlank()) return v;
        String fromEc = je.getExecutionContext().getString("ops." + key);
        if (fromEc != null && !fromEc.isBlank()) return fromEc;
        return fallback == null ? "" : fallback;
    }
}
