package com.practicebank.masters.interestrate;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 金利マスタの 1 行。COBOL の IR-REC に対応する。
 *
 * <p>DB テーブル {@code interest_rates} の行と 1:1 で対応し、COBOL プログラム群
 * (IRATE-LOOKUP / IRATE-LOAD) が扱う金利情報 (商品コード・適用日・年率・ティア閾値) を保持する。
 *
 * @param productCode     商品コード (CHAR(3) 主キーの一部)
 * @param effectiveDate   適用日 (DATE 主キーの一部)
 * @param annualRate      年率 (NUMERIC(7,6))。COBOL の IR-REC-RATE (PIC S9(3)V9(4) COMP-3) に対応
 * @param tierThresholdJpy ティア閾値 (JPY)。COBOL の IR-REC-TIER-MIN の概念に対応 (NULL 可)
 */
public record InterestRate(
        String productCode,
        LocalDate effectiveDate,
        BigDecimal annualRate,
        Long tierThresholdJpy
) {
}
