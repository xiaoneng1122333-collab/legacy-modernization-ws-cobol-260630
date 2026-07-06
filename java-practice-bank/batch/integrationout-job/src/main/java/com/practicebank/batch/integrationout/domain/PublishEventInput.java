package com.practicebank.batch.integrationout.domain;

import java.time.LocalDate;

/**
 * INTO-PUBLISH-EVENT の入力 — INTO-INPUT 相当.
 *
 * <p>イベント種別に対応するオプションフィールドを持つ.</p>
 */
public record PublishEventInput(
    String eventType,
    LocalDate businessDate,
    String batchId,
    String txnId,
    String account,
    Long amountJpy,
    String category,
    String reason,
    int count,
    String mode
) {
    public static final String EVT_TXN_POSTED = "txn.posted";
    public static final String EVT_INTEREST_POSTED = "interest.posted";
    public static final String EVT_AUTODEBIT_FAILED = "autodebit.failed";
    public static final String EVT_BATCH_COMPLETED = "batch.completed";
    public static final String EVT_STATEMENT_GENERATED = "statement.generated";

    public static final String MODE_REAL = "R";
    public static final String MODE_MOCK = "M";

    public boolean isMock() {
        return MODE_MOCK.equals(mode);
    }

    /** 入力妥当性検証: eventType 必須 + businessDate != null. */
    public boolean isValid() {
        if (eventType == null || eventType.isBlank()) return false;
        if (businessDate == null) return false;
        return switch (eventType.trim()) {
            case EVT_TXN_POSTED, EVT_INTEREST_POSTED, EVT_AUTODEBIT_FAILED,
                 EVT_BATCH_COMPLETED, EVT_STATEMENT_GENERATED -> true;
            default -> false;
        };
    }
}
