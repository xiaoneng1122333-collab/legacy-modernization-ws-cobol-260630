package com.practicebank.masters.account;

import java.time.LocalDate;

/**
 * 口座マスタの 1 行。COBOL の ACCT-REC に対応する。
 *
 * <p>DB テーブル {@code accounts} の行と 1:1 で対応し、COBOL プログラム群
 * (ACCT-EXISTS / ACCT-LOAD / ACCT-LOOKUP / ACCT-LOOKUP-BY-CUSTOMER /
 * ACCT-UPDATE-DORMANCY-DATE) が扱う口座情報を保持する。
 *
 * @param acctNumber   口座番号 (CHAR(13) 主キー)
 * @param acctName     口座名義 (漢字)
 * @param branchCode   支店コード (CHAR(3))
 * @param productCode  商品コード (CHAR(3))
 * @param acctStatus   口座ステータス (CHAR(1): P/A/D/S/C/R)
 * @param custId       顧客番号 (CHAR(10))
 * @param openedDate   開設日
 * @param dormancyDate 休眠日 (nullable)
 */
public record Account(
        String acctNumber,
        String acctName,
        String branchCode,
        String productCode,
        String acctStatus,
        String custId,
        LocalDate openedDate,
        LocalDate dormancyDate
) {
}
