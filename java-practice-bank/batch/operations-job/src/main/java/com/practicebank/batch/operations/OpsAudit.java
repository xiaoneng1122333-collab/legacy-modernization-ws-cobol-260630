package com.practicebank.batch.operations;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 共有監査出力モジュール — COBOL の AUD-WRITE 相当.
 * 各ステップ・バッチイベントを DB audit_log テーブルへ記録する.
 * Phase 2 ではシンプルな単一テーブル書き込み. 将来は本監査サブシステム 21-audit へ委譲.
 */
@Component
public class OpsAudit {

    public static final String EVT_BATCH_START = "OPS_BATCH_START";
    public static final String EVT_STEP_START = "OPS_STEP_START";
    public static final String EVT_STEP_OK = "OPS_STEP_OK";
    public static final String EVT_STEP_FAIL = "OPS_STEP_FAIL";
    public static final String EVT_BATCH_OK = "OPS_BATCH_OK";
    public static final String EVT_BATCH_FAIL = "OPS_BATCH_FAIL";

    private final JdbcTemplate jdbc;

    public OpsAudit(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** ジョブレベルイベント. */
    public void writeJob(String batchId, String businessDate, String event) {
        insert(batchId, event, null, businessDate);
    }

    /** ステップレベルイベント. */
    public void writeStep(String batchId, String businessDate, String event, String stepName) {
        insert(batchId, event, stepName, businessDate);
    }

    public void writeStepFromChunk(String batchId, String businessDate, String event, ChunkContext chunk) {
        String stepName = chunk.getStepContext().getStepName();
        writeStep(batchId, businessDate, event, stepName);
    }

    private void insert(String batchId, String eventType, String stepName, String businessDate) {
        jdbc.update(
            "INSERT INTO audit_log (batch_id, event_type, step_name, business_date, created_ts) VALUES (?, ?, ?, ?, ?)",
            batchId, eventType, stepName, businessDate, Timestamp.valueOf(LocalDateTime.now()));
    }

    /** StepExecution から step_name を抽出するユーティリティ. */
    public static String stepNameOf(StepExecution se) {
        return se == null ? "" : se.getStepName();
    }

    /** JobExecution の exit description に rc=00 相当ステータス文字列を構築. */
    public static String statusOf(JobExecution je) {
        return switch (je.getStatus()) {
            case COMPLETED -> "00";
            case FAILED -> "04";
            case STOPPED -> "02";
            default -> "16";
        };
    }
}
