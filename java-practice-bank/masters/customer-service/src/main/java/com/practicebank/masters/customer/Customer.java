package com.practicebank.masters.customer;

/** 顧客マスタの 1 行。COBOL の CUST-REC に対応する。 */
public record Customer(
        String custId,
        String custName,
        String custNameKana,
        String custStatus,
        String tier,
        String phone,
        String address
) {
}
