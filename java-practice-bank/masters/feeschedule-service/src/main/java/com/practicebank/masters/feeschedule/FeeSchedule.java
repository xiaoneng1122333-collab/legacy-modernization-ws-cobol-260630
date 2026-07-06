package com.practicebank.masters.feeschedule;

import java.time.LocalDate;

/**
 * 手数料マスタの 1 行。COBOL の FS-REC に対応する。
 *
 * <p>DB テーブル {@code fee_schedules} の行と 1:1 で対応し、COBOL プログラム群
 * (FEE-LOAD / FEE-LOOKUP-BY-TIER) が扱う手数料情報
 * (カテゴリ・ティヤ・有効開始日・手数料（JPY）) を保持する。
 *
 * @param category      手数料カテゴリコード（10=入金, 20=出金, 30=振込, 40=海外送金）
 * @param tier          ティヤ（顧客層ランク, 1..3）
 * @param effectiveDate 有効開始日 (YYYYMMDD)
 * @param feeJpy        手数料（日本円, 銭未満）
 */
public record FeeSchedule(
        String category,
        String tier,
        LocalDate effectiveDate,
        Long feeJpy
) {
}
