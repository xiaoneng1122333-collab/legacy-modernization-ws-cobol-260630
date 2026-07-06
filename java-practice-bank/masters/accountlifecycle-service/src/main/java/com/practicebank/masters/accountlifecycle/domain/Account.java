package com.practicebank.masters.accountlifecycle.domain;

import java.time.LocalDate;

/**
 * 口座マスタの 1 行。COBOL の ACCT-REC に対応する。
 *
 * <p>DB テーブル {@code accounts} の行と 1:1 で対応し、COBOL ライフサイクル
 * プログラム群 (ALC-OPEN / ALC-CHANGE-STATE / ALC-DORMANCY-SCAN /
 * ALC-REACTIVATION-SCAN) が扱う口座情報を保持する。
 *
 * <p>口座ステータス ({@code acctStatus}) は以下の 7 値を取る (状態機械 P→A→S→L→C→F→D):
 * <ul>
 *   <li>P — Pending (開設直後)</li>
 *   <li>A — Active (稼働中)</li>
 *   <li>S — Suspended (休・凍結)</li>
 *   <li>L — Lifted suspend 経由で復帰した A</li>
 *   <li>C — Closed (解約・閉鎖)</li>
 *   <li>F — Force-close 経由で閉鎖</li>
 *   <li>D — Dormant (2 年以上取引実績なく休眠)</li>
 * </ul>
 *
 * @param acctNumber    口座番号 (CHAR(13) 主キー)
 * @param custId        顧客番号 (CHAR(10))
 * @param productCode   商品コード (CHAR(3))
 * @param branchCode    支店コード (CHAR(3))
 * @param acctStatus    口座ステータス (CHAR(1): P/A/S/L/C/F/D)
 * @param openedDate    開設日
 * @param closedDate    解約日 (nullable)
 * @param overdraftLimit 当枠上限 (nullable)
 * @param termDays      契約日数 (nullable)
 * @param dormancyDate  最終取引日 (nullable)
 */
public record Account(
        String acctNumber,
        String custId,
        String productCode,
        String branchCode,
        String acctStatus,
        LocalDate openedDate,
        LocalDate closedDate,
        Long overdraftLimit,
        Integer termDays,
        LocalDate dormancyDate
) {
    /** ALC-OPEN が新規作成する口座の初期ステータス。 */
    public static final String STATUS_PENDING = "P";

    /** ステータス定数。 */
    public static final String STATUS_ACTIVE = "A";
    public static final String STATUS_SUSPENDED = "S";
    public static final String STATUS_CLOSED = "C";
    public static final String STATUS_DORMANT = "D";
}
