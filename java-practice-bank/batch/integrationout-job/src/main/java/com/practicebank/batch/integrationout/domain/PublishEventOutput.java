package com.practicebank.batch.integrationout.domain;

/**
 * INTO-PUBLISH-EVENT の出力 — INTO-OUTPUT 相当.
 *
 * <p>COBOL 88 レベル値を保持 (00=OK, 04=RETRY-EXHAUSTED, 08=INVALID-INPUT, 12=BROKER-FAIL, 16=FATAL).</p>
 */
public record PublishEventOutput(
    String status,
    String eventId,
    long durationMs,
    int retryCount
) {
    public static final String OK = "00";
    public static final String RETRY_EXHAUSTED = "04";
    public static final String INVALID_INPUT = "08";
    public static final String BROKER_FAIL = "12";
    public static final String FATAL = "16";

    public boolean isOk() { return OK.equals(status); }

    public static PublishEventOutput ok(String eventId, int retries) {
        return new PublishEventOutput(OK, eventId, 0, retries);
    }

    public static PublishEventOutput retryExhausted(int retries) {
        return new PublishEventOutput(RETRY_EXHAUSTED, null, 0, retries);
    }

    public static PublishEventOutput invalidInput() {
        return new PublishEventOutput(INVALID_INPUT, null, 0, 0);
    }
}
