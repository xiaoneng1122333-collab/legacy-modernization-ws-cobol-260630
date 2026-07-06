package com.practicebank.batch.txnsortmerge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

/** Job 終了時に 3 フェーズのバッチステータスと集計を出力 (TXSM-BATCH-OUTPUT 相当). */
public class TxsmSummaryListener implements JobExecutionListener {

    private static final Logger LOG = LoggerFactory.getLogger(TxsmSummaryListener.class);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        LOG.info("TXSM-SORT-MERGE-BATCH start: {}", jobExecution.getJobInstance().getJobName());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        var ctx = jobExecution.getExecutionContext();
        var sortOutput = (TxsmSortOutput) ctx.get(TxsmSortStepConfig.CTX_SORT_OUTPUT);
        var mergeOutput = (TxsmMergeOutput) ctx.get(TxsmMergeStepConfig.CTX_MERGE_OUTPUT);
        var reportOutput = (TxsmReportOutput) ctx.get(TxsmReportStepConfig.CTX_REPORT_OUTPUT);

        // 最悪のステータスを Job 全体の代表値とする.
        String overallStatus;
        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            overallStatus = TxsmSortOutput.STATUS_FATAL;
        } else if (hasStatus(sortOutput, TxsmMergeOutput.STATUS_INVALID)
            || hasStatus(mergeOutput, TxsmMergeOutput.STATUS_INVALID)
            || hasStatus(reportOutput, TxsmMergeOutput.STATUS_INVALID)) {
            overallStatus = TxsmSortOutput.STATUS_INVALID;
        } else if (hasStatus(sortOutput, TxsmMergeOutput.STATUS_PARTIAL)
            || hasStatus(mergeOutput, TxsmMergeOutput.STATUS_PARTIAL)
            || hasStatus(reportOutput, TxsmReportOutput.STATUS_PARTIAL)) {
            overallStatus = TxsmSortOutput.STATUS_PARTIAL;
        } else {
            overallStatus = TxsmSortOutput.STATUS_OK;
        }

        LOG.info("TXSM-BATCH-OUTPUT: sort.status={} merge.status={} report.status={} overall={}",
            sortOutput == null ? "-" : sortOutput.status(),
            mergeOutput == null ? "-" : mergeOutput.status(),
            reportOutput == null ? "-" : reportOutput.status(),
            overallStatus);
    }

    private boolean hasStatus(Object output, String statusCode) {
        if (output instanceof TxsmSortOutput o) return statusCode.equals(o.status());
        if (output instanceof TxsmMergeOutput o) return statusCode.equals(o.status());
        if (output instanceof TxsmReportOutput o) return statusCode.equals(o.status());
        return false;
    }
}
