package com.practicebank.batch.txnvalidate;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * バリデーションルール — TXVAL-VALIDATE-BATCH の E001..E019 コード体系を Java で表現.
 * <p>
 * 本実装ではマスタ参照 (CAL-LOOKUP / BR-LOOKUP / PROD-LOOKUP) を必要とするルールは
 * Phase 2 で別途マスタ整備後に有効化. 当面はスキップ可能フラグで制御する.
 */
public final class ValidationRules {

    /** 妥当なカテゴリ: 10=預金, 20=払出, 30=振替, 40=電送 */
    private static final Set<String> VALID_CATEGORIES = Set.of("10", "20", "30", "40");

    /** カテゴリ 30/40 ではあて先口座が必須 */
    private static final Set<String> COUNTER_ACCOUNT_REQUIRED = Set.of("30", "40");

    /** カテゴリ 10/20 ではあて先口座が不要 */
    private static final Set<String> COUNTER_ACCOUNT_FORBIDDEN = Set.of("10", "20");

    /** 金額上限 (99,999,999 — E010 閾値) */
    private static final BigDecimal AMOUNT_MAX = new BigDecimal("99999999");

    private ValidationRules() {
    }

    /**
     * @return エラーコード (E001..E019) のリスト. 問題ない場合は空.
     */
    public static List<String> validate(TxnTransaction txn) {
        List<String> errors = new ArrayList<>();
        if (txn == null) {
            return errors;
        }

        // E002 カテゴリ不正
        if (txn.category() == null || !VALID_CATEGORIES.contains(txn.category().trim())) {
            errors.add("E002");
        }

        // E003 口座番号非数値 (CHAR(13) 桁数 & 数字チェック)
        if (!isNumericAccount(txn.accountNumber())) {
            errors.add("E003");
        }

        // E007 あて先口座なし (振替/電送で必須)
        String cat = txn.category() == null ? "" : txn.category().trim();
        if (COUNTER_ACCOUNT_REQUIRED.contains(cat)
                && (txn.counterAccountNumber() == null || txn.counterAccountNumber().isBlank())) {
            errors.add("E007");
        }

        // E008 自己送金 — 送金人 == あて先
        if (txn.isSelfTransfer()) {
            errors.add("E008");
        }

        // E009 金額ゼロ / E010 金額超過
        if (txn.amountJpy() != null) {
            if (txn.amountJpy().compareTo(BigDecimal.ZERO) == 0) {
                errors.add("E009");
            } else if (txn.amountJpy().compareTo(AMOUNT_MAX) > 0) {
                errors.add("E010");
            }
        }

        // E012 非営業日 (土日を簡易判定 — CAL-LOOKUP 舐替えは Phase 2 マスタ整備時に)
        if (txn.businessDate() != null && isWeekend(txn.businessDate())) {
            errors.add("E012");
        }

        // E013 通貨不正 (JPY 限定)
        if (txn.currency() == null || !"JPY".equals(txn.currency().trim())) {
            errors.add("E013");
        }

        // E018 預金カテゴリにあり不要なあて先口座あり
        if (COUNTER_ACCOUNT_FORBIDDEN.contains(cat)
                && txn.counterAccountNumber() != null
                && !txn.counterAccountNumber().isBlank()) {
            errors.add("E018");
        }

        return errors;
    }

    private static boolean isNumericAccount(String account) {
        if (account == null || account.isBlank()) {
            return false;
        }
        return account.trim().chars().allMatch(Character::isDigit);
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }
}
