package com.practicebank.masters.product;

import java.time.LocalDateTime;

/**
 * 製品マスタの 1 行。COBOL の PRD-REC / PROD-OUTPUT に対応する。
 *
 * <p>product_type は COBOL の PRD-OUT-TYPE に相当:
 * <ul>
 *   <li>{@code S} — 普通預金 (PRD-TYPE-SAVINGS)</li>
 *   <li>{@code C} — 当座預金 (PRD-TYPE-CHECKING)</li>
 *   <li>{@code T} — 定期預金 (PRD-TYPE-TIME-DEPOSIT)</li>
 * </ul>
 */
public record Product(
        String productCode,
        String productName,
        String productType,
        String interestEligible,
        String feeEligible,
        Long minBalanceJpy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
