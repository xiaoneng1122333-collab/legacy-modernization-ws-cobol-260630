package com.practicebank.masters.accountlifecycle.program;

/**
 * COBOL プログラム群の返却コード (RETURN-CODE 相当)。
 *
 * <p>各 PIC X(2) ステータスに対応する enum。
 */
public enum ProgramStatus {

    /** 00 — 正常終了。 */
    OK("00"),
    /** 04 — 該当なし (NOT-FOUND / NO-CANDS)。 */
    NOT_FOUND("04"),
    /** 08 — 入力不正 / 遷移禁止 (INVALID)。 */
    INVALID("08"),
    /** 12 — I/O 失敗 (OPEN / WRITE / REWRITE 失敗)。 */
    IO_FAIL("12"),
    /** 16 — 予約 / 致命的エラー (FATAL)。 */
    FATAL("16");

    private final String code;

    ProgramStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public boolean isOk() {
        return this == OK;
    }
}
