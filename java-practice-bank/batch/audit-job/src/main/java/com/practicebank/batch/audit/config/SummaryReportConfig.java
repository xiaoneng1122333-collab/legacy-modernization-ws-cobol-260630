package com.practicebank.batch.audit.config;

import com.practicebank.batch.audit.domain.SummaryReportInput;
import com.practicebank.batch.audit.domain.SummaryReportOutput;
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
import java.util.List;
import java.util.Map;

/**
 * AUDIT-SUMMARY-REPORT 相当の Spring Batch Step (tasklet).
 *
 * <p>audit_log を日付範囲で集計し, BY-DAY または BY-SUBSYSTEM の集計レポートを出力する.
 * Phase 2 ではファイル出力をログ出力に置き換える.</p>
 */
@Configuration
public class SummaryReportConfig {

    private static final Logger LOG = LoggerFactory.getLogger(SummaryReportConfig.class);

    @Value("${audit.summary.date-start:#{null}}")
    private String dateStart;

    @Value("${audit.summary.date-end:#{null}}")
    private String dateEnd;

    @Value("${audit.summary.mode:D}")
    private String mode;

    @Value("${audit.summary.output-filename:#{null}}")
    private String outputFilename;

    @Bean
    public Step summaryReport(JobRepository jobRepository,
                              PlatformTransactionManager txManager,
                              Tasklet summaryReportTasklet) {
        return new StepBuilder("summaryReport", jobRepository)
            .tasklet(summaryReportTasklet, txManager)
            .build();
    }

    @Bean
    public Tasklet summaryReportTasklet(DataSource dataSource) {
        final JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        return new Tasklet() {
            @Override
            public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
                SummaryReportInput input = new SummaryReportInput(
                    dateStart, dateEnd, mode, outputFilename
                );

                // VALIDATE-INPUT
                if (!input.isValid()) {
                    LOG.warn("AUDIT-SUMMARY-REPORT: INVALID-INPUT (start={} end={} file={})",
                        dateStart, dateEnd, outputFilename);
                    writeOutput(chunkContext, SummaryReportOutput.invalidInput());
                    return RepeatStatus.FINISHED;
                }

                String effectiveMode = input.effectiveMode();
                LOG.info("Audit Summary Report: mode={} dateStart={} dateEnd={}",
                    effectiveMode, input.dateStart(), input.dateEnd());

                // HEADER
                LOG.info("HEADER: Audit Summary Report — mode={} range={}..{}",
                    effectiveMode, input.dateStart(), input.dateEnd());

                int groupCount = 0;
                long totalRows = 0;

                if ("D".equals(effectiveMode)) {
                    // BY-DAY: GROUP BY business_date, action
                    List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT business_date, action, COUNT(*) AS cnt " +
                        "FROM audit_log " +
                        "WHERE business_date BETWEEN ? AND ? " +
                        "GROUP BY business_date, action " +
                        "ORDER BY business_date, cnt DESC",
                        input.dateStart(), input.dateEnd()
                    );
                    for (Map<String, Object> row : rows) {
                        groupCount++;
                        long cnt = ((Number) row.get("cnt")).longValue();
                        totalRows += cnt;
                        LOG.info("BY-DAY: bdate={} action={} count={}",
                            row.get("business_date"), row.get("action"), cnt);
                    }
                } else {
                    // BY-SUBSYSTEM: GROUP BY subsystem, severity
                    List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT subsystem, severity, COUNT(*) AS cnt " +
                        "FROM audit_log " +
                        "WHERE business_date BETWEEN ? AND ? " +
                        "GROUP BY subsystem, severity " +
                        "ORDER BY subsystem, severity",
                        input.dateStart(), input.dateEnd()
                    );
                    for (Map<String, Object> row : rows) {
                        groupCount++;
                        long cnt = ((Number) row.get("cnt")).longValue();
                        totalRows += cnt;
                        LOG.info("BY-SUBSYSTEM: subsystem={} severity={} count={}",
                            row.get("subsystem"), row.get("severity"), cnt);
                    }
                }

                SummaryReportOutput output = SummaryReportOutput.ok(groupCount, totalRows);
                writeOutput(chunkContext, output);

                LOG.info("AUDIT-SUMMARY-REPORT end: groups={} total={}", groupCount, totalRows);

                return RepeatStatus.FINISHED;
            }
        };
    }

    private void writeOutput(ChunkContext chunkContext, SummaryReportOutput output) {
        chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
            .put("asr.status", output.status());
        chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
            .putInt("asr.groupCount", output.groupCount());
        chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
            .putLong("asr.totalRows", output.totalRows());
    }
}
