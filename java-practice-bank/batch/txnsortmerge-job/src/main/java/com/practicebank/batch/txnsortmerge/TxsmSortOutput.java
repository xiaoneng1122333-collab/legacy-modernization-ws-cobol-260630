package com.practicebank.batch.txnsortmerge;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * TXSM-SORT-OUTPUT 相当 — ソートフェーズの出力.
 * COBOL copybook の PIC 定義 (TXSM-SO-*) に準拠.
 */
public record TxsmSortOutput(
    String status,           // TXSM-SO-STATUS            PIC X(2)   "00"/"04"/"08"/"12"/"16"
    int recordsProcessed,    // TXSM-SO-RECORDS-PROCESSED PIC 9(7)
    int recordsSorted,       // TXSM-SO-RECORDS-SORTED    PIC 9(7)
    String ctrlTotalMatch,   // TXSM-SO-CTRL-TOTAL-MATCH  PIC X(1)  "Y"/"N"
    BigDecimal amountSum      // TXSM-SO-AMOUNT-SUM        PIC 9(20)
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    public static final String STATUS_OK = "00";
    public static final String STATUS_PARTIAL = "04";
    public static final String STATUS_INVALID = "08";
    public static final String STATUS_IO_FAIL = "12";
    public static final String STATUS_FATAL = "16";
}
