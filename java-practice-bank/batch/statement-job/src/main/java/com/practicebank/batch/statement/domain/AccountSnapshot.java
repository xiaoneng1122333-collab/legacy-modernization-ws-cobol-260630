package com.practicebank.batch.statement.domain;

/**
 * 1 口座の帳票生成用 snapshot — STMT-GENERATE-BATCH の ACCTCUR カーソル行相当.
 *
 * <p>accounts テーブル + balances テーブル JOIN 結果.</p>
 */
public record AccountSnapshot(
    String accountNumber,
    String accountName,
    String branchCode,
    String productCode,
    String accountStatus,
    String customerId,
    long balanceJpy
) {
    /** 有効な口座 (status = 'A') かどうか. */
    public boolean isActive() {
        return "A".equals(accountStatus);
    }
}
