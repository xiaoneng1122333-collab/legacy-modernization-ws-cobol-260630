package com.practicebank.batch.integrationout.domain;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * MQ publish 用の JSON エンベロープ — INTO-PUBLISH-EVENT の BUILD-ENVELOPE 相当.
 *
 * <p>version / eventId / eventType / businessDate / publishedAt / source / payload の 7 項目を持つ.</p>
 */
public record EventEnvelope(
    String version,
    String eventId,
    String eventType,
    String businessDate,
    String publishedAt,
    String source,
    String payload
) {
    public static final String VERSION = "1.0";
    public static final String SOURCE = "20-integrationout";

    /**
     * 与えられた入力で envelope を生成するファクトリ.
     */
    public static EventEnvelope of(String eventId,
                                    PublishEventInput input,
                                    String payload) {
        String bdate = input.businessDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String now = ZonedDateTime.now(java.time.ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
        return new EventEnvelope(
            VERSION,
            eventId,
            input.eventType().trim(),
            bdate,
            now,
            SOURCE,
            payload
        );
    }

    /** payload の簡易 JSON 構築 (マッピングライブラリなしで文字列組み立て). */
    public static String buildPayload(PublishEventInput in) {
        StringBuilder sb = new StringBuilder("{");
        switch (in.eventType().trim()) {
            case PublishEventInput.EVT_TXN_POSTED -> {
                sb.append("\"txnId\":\"").append(nullToEmpty(in.txnId())).append("\",");
                sb.append("\"account\":\"").append(nullToEmpty(in.account())).append("\",");
                sb.append("\"amountJpy\":").append(in.amountJpy() != null ? in.amountJpy() : 0);
            }
            case PublishEventInput.EVT_INTEREST_POSTED -> {
                sb.append("\"account\":\"").append(nullToEmpty(in.account())).append("\",");
                sb.append("\"amountJpy\":").append(in.amountJpy() != null ? in.amountJpy() : 0);
            }
            case PublishEventInput.EVT_AUTODEBIT_FAILED -> {
                sb.append("\"account\":\"").append(nullToEmpty(in.account())).append("\",");
                sb.append("\"amountJpy\":").append(in.amountJpy() != null ? in.amountJpy() : 0).append(",");
                sb.append("\"reason\":\"").append(nullToEmpty(in.reason())).append("\"");
            }
            case PublishEventInput.EVT_BATCH_COMPLETED -> {
                sb.append("\"batchId\":\"").append(nullToEmpty(in.batchId())).append("\",");
                sb.append("\"count\":").append(in.count());
            }
            case PublishEventInput.EVT_STATEMENT_GENERATED -> {
                sb.append("\"account\":\"").append(nullToEmpty(in.account())).append("\",");
                sb.append("\"batchId\":\"").append(nullToEmpty(in.batchId())).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
