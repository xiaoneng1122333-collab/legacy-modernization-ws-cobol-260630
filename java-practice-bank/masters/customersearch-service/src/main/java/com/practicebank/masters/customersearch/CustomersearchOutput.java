package com.practicebank.masters.customersearch;

/** COBOL の CSRCH-OUTPUT に対応する検索結果。ステータスコードを含む。 */
public record CustomersearchOutput(
        int status,
        Long matchId,
        String matchKana,
        String matchKanji,
        String matchPhone,
        String matchAddr,
        Long lastId
) {
    /** 正常終了 (CSRCH-OK = 00)。 */
    public boolean isOk() {
        return status == 0;
    }

    /** EOF / 不一致 (CSRCH-EOF = 10)。 */
    public boolean isEof() {
        return status == 10;
    }

    /** FATAL (CSRCH-FATAL = 16)。 */
    public boolean isFatal() {
        return status == 16;
    }

    /** EOF (status=10) を返すファクトリ。 */
    public static CustomersearchOutput eof() {
        return new CustomersearchOutput(10, null, null, null, null, null, null);
    }

    /** 正常終了 (status=0) を返すファクトリ。 */
    public static CustomersearchOutput ok(Customer customer) {
        return new CustomersearchOutput(
                0,
                customer.id(),
                customer.kana(),
                customer.kanji(),
                customer.phone(),
                customer.address(),
                customer.id()
        );
    }
}
