package com.practicebank.batch.integrationout.domain;

/**
 * INTO-DRAIN-QUEUE の出力 — INTD-OUTPUT 相当.
 *
 * <p>COBOL 88 レベル値を保持 (00=OK, 04=PARTIAL, 08=INVALID-INPUT, 12=IO-FAIL, 16=FATAL).</p>
 */
public record DrainQueueOutput(
    String status,
    int drained,
    int failed,
    long durationMs
) {
    public static final String OK = "00";
    public static final String PARTIAL = "04";
    public static final String INVALID_INPUT = "08";
    public static final String IO_FAIL = "12";
    public static final String FATAL = "16";

    public boolean isOk() { return OK.equals(status); }

    public static DrainQueueOutput ok(int drained) {
        return new DrainQueueOutput(OK, drained, 0, 0);
    }

    public static DrainQueueOutput partial(int drained, int failed) {
        return new DrainQueueOutput(PARTIAL, drained, failed, 0);
    }

    public static DrainQueueOutput invalidInput() {
        return new DrainQueueOutput(INVALID_INPUT, 0, 0, 0);
    }

    public static DrainQueueOutput ioFail() {
        return new DrainQueueOutput(IO_FAIL, 0, 0, 0);
    }
}
