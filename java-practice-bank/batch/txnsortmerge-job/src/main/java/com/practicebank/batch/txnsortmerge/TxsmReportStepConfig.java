package com.practicebank.batch.txnsortmerge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * TXSM-REPORT-SUMMARY 相当の Step.
 *
 * <p>SORT + MERGE フェーズのサマリから可読レポートを生成し、audit_log に保存.</p>
 * <p>保存量不変条件の検証結果 (VERIFIED / MERGE phase data missing / summary empty) をセクションに差し込む.</p>
 */
@Configuration
public class TxsmReportStepConfig {

    private static final Logger LOG = LoggerFactory.getLogger(TxsmReportStepConfig.class);
    public static final String CTX_REPORT_OUTPUT = "txsm.report.output";

    @Bean
    public Step reportSummary(JobRepository jobRepository,
                              PlatformTransactionManager txManager,
                              DataSource dataSource,
                              @Value("${txnsortmerge.report.batch.id:-}") String batchId) {
        return new StepBuilder("reportSummary", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                var jobCtx = chunkContext.getStepContext().getStepExecution()
                    .getJobExecution().getExecutionContext();

                var sortOutput = (TxsmSortOutput) jobCtx.get(TxsmSortStepConfig.CTX_SORT_OUTPUT);
                var mergeOutput = (TxsmMergeOutput) jobCtx.get(TxsmMergeStepConfig.CTX_MERGE_OUTPUT);

                // save report rows.
                int linesWritten = 0;
                StringBuilder body = new StringBuilder();

                body.append("===============================================\n");
                body.append(" TXSM  SORT / MERGE / REPORT SUMMARY\n");
                body.append(" batchId=").append(batchId == null ? "" : batchId).append('\n');
                body.append("===============================================\n");
                linesWritten += 4;

                String conservationDetail;
                String conservationFlag;
                if (sortOutput != null && mergeOutput != null) {
                    // both phases present — verify conservation invariant.
                    int sin = mergeOutput.recordsSortedIn();
                    int rin = mergeOutput.recordsReconIn();
                    int out = mergeOutput.recordsMergedOut();
                    int dup = mergeOutput.duplicateRecords();
                    boolean conservationOk = (sin + rin == out + dup);
                    String conservationStatus = conservationOk ? "VERIFIED" : "FAILED";

                    body.append(String.format(" SORT-PHASE : status=%s in=%d sorted=%d ctrl=%s sum=%s%n",
                        sortOutput.status(), sortOutput.recordsProcessed(), sortOutput.recordsSorted(),
                        sortOutput.ctrlTotalMatch(), sortOutput.amountSum()));
                    body.append(String.format(" MERGE-PHASE: status=%s sin=%d rin=%d out=%d dup-pairs=%d dup-records=%d viol=%s recon=%s sum=%s%n",
                        mergeOutput.status(), sin, rin, out, mergeOutput.duplicatePairs(),
                        dup, mergeOutput.sortViolations(), mergeOutput.reconPresentFlag(),
                        mergeOutput.amountSum()));
                    body.append(String.format(" ## Conservation invariant: %s (sin+rin=%d, out+dup=%d)%n",
                        conservationStatus, sin + rin, out + dup));
                    linesWritten += 3;
                    conservationDetail = conservationStatus;
                    conservationFlag = conservationOk ? "Y" : "?";
                } else if (sortOutput != null && mergeOutput == null) {
                    body.append(String.format(" SORT-PHASE : status=%s in=%d sorted=%d ctrl=%s sum=%s%n",
                        sortOutput.status(), sortOutput.recordsProcessed(), sortOutput.recordsSorted(),
                        sortOutput.ctrlTotalMatch(), sortOutput.amountSum()));
                    body.append(" ## NOTE: MERGE phase data missing\n");
                    linesWritten += 2;
                    conservationDetail = "MERGE phase data missing";
                    conservationFlag = "?";
                } else {
                    body.append(" ## NOTE: summary empty\n");
                    linesWritten += 1;
                    conservationDetail = "summary empty";
                    conservationFlag = "?";
                }

                body.append("===============================================\n");
                linesWritten += 1;

                // persist to audit_log.
                try (var conn = dataSource.getConnection();
                     var ps = conn.prepareStatement(
                         "INSERT INTO audit_log (business_date, subsystem, action, actor, target_type, target_id, payload_json, severity, schema_version) " +
                         "VALUES (CURRENT_DATE, 'TXNSORTMERGE', 'REPORT-SUMMARY', 'txsm-job', 'BATCH', ?, ?::jsonb, 'I', '1.0')")) {
                    ps.setString(1, batchId == null ? "" : batchId);
                    String payload = String.format(
                        "{\"sort\":%s,\"merge\":%s,\"conservation\":\"%s\",\"lines\":%d}",
                        sortOutput == null ? "null" : "\"" + sortOutput.status() + "\"",
                        mergeOutput == null ? "null" : "\"" + mergeOutput.status() + "\"",
                        conservationDetail,
                        linesWritten
                    );
                    ps.setString(2, payload);
                    ps.executeUpdate();
                } catch (Exception e) {
                    // audit_log テーブルがない場合はログだけ残す (NOT FATAL).
                    LOG.warn("audit_log insert failed (non-fatal): {}", e.getMessage());
                }

                // status 判定.
                String status;
                if (sortOutput != null && mergeOutput != null && "Y".equals(conservationFlag)) {
                    status = TxsmReportOutput.STATUS_OK;
                } else {
                    status = TxsmReportOutput.STATUS_PARTIAL;
                }

                TxsmReportOutput reportOutput = new TxsmReportOutput(status, linesWritten, conservationFlag);
                jobCtx.put(CTX_REPORT_OUTPUT, reportOutput);

                LOG.info("TXSM-REPORT-OUTPUT: status={} linesWritten={} conservation={}",
                    reportOutput.status(), reportOutput.linesWritten(), reportOutput.conservationOk());
                LOG.info("TXSM-REPORT-BODY:\n{}", body);
                return org.springframework.batch.repeat.RepeatStatus.FINISHED;
            }, txManager)
            .build();
    }
}
