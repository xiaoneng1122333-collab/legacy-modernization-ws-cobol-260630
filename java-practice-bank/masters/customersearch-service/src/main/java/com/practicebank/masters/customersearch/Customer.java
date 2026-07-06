package com.practicebank.masters.customersearch;

/** 顧客検索結果の 1 行。COBOL の CUST-OUTPUT に対応する。 */
public record Customer(
        Long id,
        String kana,
        String kanji,
        String phone,
        String address
) {
}
