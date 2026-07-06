package com.practicebank.batch.txnsortmerge;

import java.io.Serial;
import java.io.Serializable;

/**
 * TXSM-REPORT-OUTPUT 相当 — レポートフェーズの出力.
 * COBOL copybook の PIC 定義 (TXSM-RP-*) に準拠.
 */
public record TxsmReportOutput(
    String status,           // TXSM-RP-STATUS          PIC X(2)
    int linesWritten,        // TXSM-RP-LINES-WRITTEN   PIC 9(5)
    String conservationOk    // TXSM-RP-CONSERVATION-OK PIC X(1)  "Y"/"?"
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    public static final String STATUS_OK = "00";
    public static final String STATUS_PARTIAL = "04";
    public static final String STATUS_IO_FAIL = "12";
    public static final String STATUS_FATAL = "16";
}
