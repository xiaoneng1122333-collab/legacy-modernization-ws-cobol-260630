package com.practicebank.batch.integrationout.domain;

/**
 * autodebit キューファイルの 1 レコード — INTO-DRAIN-QUEUE の READ-ONE 相当.
 */
public record FailedAutodebitRecord(
    String txnId,
    String account,
    long amountJpy,
    String reason
) {
    /** 固定長 200 バイト Record と互換の文字列表現. */
    @Override
    public String toString() {
        return String.format("FailedAutodebitRecord{txnId='%s', account='%s', amountJpy=%d, reason='%s'}",
            txnId, account, amountJpy, reason);
    }
}
