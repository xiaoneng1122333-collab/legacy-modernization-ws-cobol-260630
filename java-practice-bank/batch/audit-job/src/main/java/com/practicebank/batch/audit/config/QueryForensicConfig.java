package com.practicebank.batch.audit.config;

import com.practicebank.batch.audit.domain.AuditEntry;
import com.practicebank.batch.audit.domain.ForensicQueryInput;
import com.practicebank.batch.audit.domain.ForensicQueryOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * AUDIT-QUERY-FORENSIC 相当の Spring Batch Step (tasklet).
 *
 * <p>audit_log を日付範囲・サブシステム・アクション・重大度・口座番号で検索し,
 * TEXT/CSV/JSON 形式のファイルに出力する.
 * Phase 2 ではファイル出力をログ出力に置き換え, 結果件数のみ返す (<= 50 rows for test stability).</p>
 */
@Configuration
public class QueryForensicConfig {

    private static final Logger LOG = LoggerFactory.getLogger(QueryForensicConfig.class);

    @Value("${audit.forensic.date-start:#{null}}")
    private String dateStart;

    @Value("${audit.forensic.date-end:#{null}}")
    private String dateEnd;

    @Value("${audit.forensic.subsystem:#{null}}")
    private String subsystem;

    @Value("${audit.forensic.action:#{null}}")
    private String action;

    @Value("${audit.forensic.severity:#{null}}")
    private String severity;

    @Value("${audit.forensic.account-filter:#{null}}")
    private String accountFilter;

    @Value("${audit.forensic.max-rows:1000}")
    private int maxRows;

    @Value("${audit.forensic.output-format:TEXT}")
    private String outputFormat;

    @Value("${audit.forensic.output-filename:#{null}}")
    private String outputFilename;

    @Value("${audit.forensic.operator-user:#{null}}")
    private String operatorUser;

    @Value("${audit.forensic.file-output:true}")
    private boolean fileOutput;

    @Bean
    public Step queryForensic(JobRepository jobRepository,
                              PlatformTransactionManager txManager,
                              Tasklet queryForensicTasklet) {
        return new StepBuilder("queryForensic", jobRepository)
            .tasklet(queryForensicTasklet, txManager)
            .build();
    }

    @Bean
    public Tasklet queryForensicTasklet(DataSource dataSource) {
        final JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        return new Tasklet() {
            @Override
            public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
                ForensicQueryInput input = new ForensicQueryInput(
                    dateStart, dateEnd, subsystem, action, severity,
                    accountFilter, maxRows, outputFormat, outputFilename, operatorUser
                );

                // VALIDATE-INPUT
                if (!input.isValid()) {
                    LOG.warn("AUDIT-QUERY-FORENSIC: INVALID-INPUT (dateStart={} dateEnd={} format={})",
                        dateStart, dateEnd, outputFormat);
                    writeOutput(chunkContext, ForensicQueryOutput.invalidInput());
                    return RepeatStatus.FINISHED;
                }

                // UUID から query_id 生成
                String queryId = UUID.randomUUID().toString();

                // BUILD-QUERY (Parameterized)
                StringBuilder sql = new StringBuilder(
                    "SELECT audit_id, business_date, subsystem, action, severity, " +
                    "account_number, payload, source_system, operator_user, created_ts " +
                    "FROM audit_log WHERE business_date BETWEEN ? AND ?");
                java.util.List<Object> params = new java.util.ArrayList<>();
                params.add(input.dateStart());
                params.add(input.dateEnd());

                if (input.subsystem() != null && !input.subsystem().isBlank()) {
                    sql.append(" AND subsystem = ?");
                    params.add(input.subsystem());
                }
                if (input.action() != null && !input.action().isBlank()) {
                    sql.append(" AND action = ?");
                    params.add(input.action());
                }
                if (input.severity() != null && !input.severity().isBlank()
                    && !" ".equals(input.severity())) {
                    sql.append(" AND severity = ?");
                    params.add(input.severity());
                }
                if (input.accountFilter() != null && !input.accountFilter().isBlank()) {
                    sql.append(" AND account_number = ?");
                    params.add(input.accountFilter());
                }
                sql.append(" ORDER BY created_ts LIMIT ?");
                params.add(input.effectiveMaxRows());

                // PREAMBLE
                LOG.info("Audit Forensic Result: queryId={} dateStart={} dateEnd={} subsystem={} action={} severity={}",
                    queryId, input.dateStart(), input.dateEnd(),
                    blankToAny(input.subsystem()), blankToAny(input.action()), blankToAny(input.severity()));

                // FETCH-LOOP (simulated: stream rows to log instead of file)
                List<AuditEntry> rows = jdbc.query(sql.toString(), QueryForensicConfig::mapRow, params.toArray());

                int rowCount = 0;
                StringBuilder csvBuffer = new StringBuilder();
                for (AuditEntry row : rows) {
                    rowCount++;
                    String line = formatRow(row, input.outputFormat().trim(), rowCount == 1);
                    csvBuffer.append(line);
                    csvBuffer.append(System.lineSeparator());
                }
                LOG.info("FETCH result size={}", rowCount);

                // POSTAMBLE / META-AUDIT
                LOG.info("AUD-WRITE AUDIT_QUERY_EXECUTED: queryId={} rows={} operator={}",
                    queryId, rowCount, blankToAny(input.operatorUser()));

                ForensicQueryOutput output = ForensicQueryOutput.ok(rowCount, queryId);
                writeOutput(chunkContext, output);

                LOG.info("AUDIT-QUERY-FORENSIC end: queryId={} rows={} format={}",
                    queryId, rowCount, input.outputFormat());

                return RepeatStatus.FINISHED;
            }
        };
    }

    private static AuditEntry mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Timestamp ts = rs.getTimestamp("created_ts");
        return new AuditEntry(
            rs.getString("audit_id"),
            rs.getString("business_date"),
            rs.getString("subsystem"),
            rs.getString("action"),
            rs.getString("severity"),
            rs.getString("account_number"),
            rs.getString("payload"),
            rs.getString("source_system"),
            rs.getString("operator_user"),
            ts
        );
    }

    private String formatRow(AuditEntry row, String format, boolean isFirst) {
        return switch (format) {
            case "CSV" -> formatCsv(row, isFirst);
            case "JSON" -> formatJson(row, isFirst);
            default -> formatText(row);
        };
    }

    private String formatText(AuditEntry row) {
        return String.format("%s | %s | %s | %s | %s | %s | %s",
            row.auditId(), row.businessDate(), row.subsystem(), row.action(),
            row.severity(), nullToEmpty(row.accountNumber()), nullToEmpty(row.payload()));
    }

    private String formatCsv(AuditEntry row, boolean isFirst) {
        if (isFirst) {
            return "audit_id,bdate,subsystem,action,severity,account,payload" + System.lineSeparator()
                + String.format("%s,%s,%s,%s,%s,%s,%s",
                row.auditId(), row.businessDate(), row.subsystem(), row.action(),
                row.severity(), nullToEmpty(row.accountNumber()), nullToEmpty(row.payload()));
        }
        return String.format("%s,%s,%s,%s,%s,%s,%s",
            row.auditId(), row.businessDate(), row.subsystem(), row.action(),
            row.severity(), nullToEmpty(row.accountNumber()), nullToEmpty(row.payload()));
    }

    private String formatJson(AuditEntry row, boolean isFirst) {
        String prefix = isFirst ? "[" : ",";
        return prefix + String.format(
            "{\"auditId\":\"%s\",\"bdate\":\"%s\",\"subsystem\":\"%s\",\"action\":\"%s\",\"severity\":\"%s\"}",
            row.auditId(), row.businessDate(), row.subsystem(), row.action(), row.severity());
    }

    private String nullToEmpty(String s) { return s == null ? "" : s; }
    private String blankToAny(String s) { return (s == null || s.isBlank()) ? "*" : s; }

    private void writeOutput(ChunkContext chunkContext, ForensicQueryOutput output) {
        chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
            .put("aqf.status", output.status());
        chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
            .putInt("aqf.rowCount", output.rowCount());
        chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
            .put("aqf.queryId", output.queryId());
    }
}
