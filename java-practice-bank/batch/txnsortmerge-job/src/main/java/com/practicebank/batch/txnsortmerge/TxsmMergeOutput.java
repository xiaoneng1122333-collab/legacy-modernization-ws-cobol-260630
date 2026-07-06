package com.practicebank.batch.txnsortmerge;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * TXSM-MERGE-OUTPUT 相当 — マージフェーズの出力.
 * COBOL copybook の PIC 定義 (TXSM-MO-*) に準拠.
 */
public record TxsmMergeOutput(
    String status,           // TXSM-MO-STATUS             PIC X(2)
    int recordsSortedIn,     // TXSM-MO-RECORDS-SORTED-IN  PIC 9(7)
    int recordsReconIn,      // TXSM-MO-RECORDS-RECON-IN   PIC 9(7)
    int recordsMergedOut,    // TXSM-MO-RECORDS-MERGED-OUT PIC 9(7)
    int duplicateRecords,    // TXSM-MO-DUPLICATE-RECORDS  PIC 9(5)
    int duplicatePairs,      // TXSM-MO-DUPLICATE-PAIRS    PIC 9(5)
    int sortViolations,      // TXSM-MO-SORT-VIOLATIONS    PIC 9(5)
    String reconPresentFlag, // TXSM-MO-RECON-PRESENT-FLAG PIC X(1)  "Y"/"N"
    BigDecimal amountSum       // TXSM-MO-AMOUNT-SUM         PIC 9(20)
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    public static final String STATUS_OK = "00";
    public static final String STATUS_PARTIAL = "04";
    public static final String STATUS_INVALID = "08";
    public static final String STATUS_IO_FAIL = "12";
    public static final String STATUS_FATAL = "16";
}
