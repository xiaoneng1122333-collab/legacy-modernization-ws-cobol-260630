package com.practicebank.batch.integrationin;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * INTI-DECODE-BATCH デコードロジック。
 *
 * <p>COBOL {@code PROCESS-EBCDIC-LOOP / PARSE-DETAIL-FIELDS / TRANSLATE-CAT /
 * TRANSLATE-DATE / VALIDATE-AMOUNT / CHECK-ACCT-FORMAT / VERIFY-TRAILER} を移植。
 *
 * <p>Phase 2 制約: 実 EBCDIC バイナリは読まず、入力はテキスト行として渡す
 * (CSV ライクなテストフィクスチャ)。各行は 800 バイト固定長相当の文字列。
 * レコード種別は先頭 1 文字 (H/D/T)。
 *
 * <p>出力: 妥当明細のリスト + 拒否行 (理由コード付き) + トレイラ検証結果 + INTI-OUTPUT 相当構造。
 */
public final class IntiDecodeService {

    // COBOL コード値 (E101..E199)
    private static final String E_MISSING_HEADER = "E101";
    private static final String E_DUP_TRAILER = "E102";
    private static final String E_TRAILER_COUNT_MISMATCH = "E103";
    private static final String E_INVALID_CATEGORY = "E105";
    private static final String E_INVALID_ACCT = "E106";
    private static final String E_ZERO_AMOUNT = "E108";
    private static final String E_INVALID_DATE = "E110";
    private static final String E_OTHER = "E199";

    private static final Set<Integer> VALID_CATEGORIES = Set.of(10, 20, 30, 40);

    private IntiDecodeService() {
    }

    /**
     * デコード実行。
     *
     * @param batchId           HDR / DTL に埋め込むバッチ ID
     * @param businessDate      8 桁 (YYYYMMDD)
     * @param rawRecords        "800文字固定長相当" の入力レコード列 (行頭 H/D/T)
     * @param rejectThresholdPct 拒否率閾値 % (既定のまま渡せる)
     * @return デコード結果 (妥当明細, 拒否一覧, 集計, INTI-OUTPUT)
     */
    public static DecodeResult decode(String batchId,
                                      long businessDate,
                                      List<String> rawRecords,
                                      int rejectThresholdPct) {
        List<DecodedTransaction> decoded = new ArrayList<>();
        List<RejectLine> rejects = new ArrayList<>();

        boolean headerSeen = false;
        boolean trailerSeen = false;
        long recordsRead = 0;
        long detailsDecoded = 0;
        long detailsRejected = 0;
        long checksumAcc = 0;
        Long trailerExpectedCount = null;

        String currentReason = null;

        for (String raw : rawRecords) {
            recordsRead++;
            checksumAcc = accumulate(checksumAcc, raw);

            String recType = raw.length() >= 1 ? raw.substring(0, 1).toUpperCase() : "";

            switch (recType) {
                case "H" -> {
                    if (headerSeen) {
                        rejects.add(new RejectLine(E_DUP_TRAILER, raw, "duplicate header"));
                        detailsRejected++;
                    } else {
                        headerSeen = true;
                    }
                }
                case "D" -> {
                    if (!headerSeen) {
                        rejects.add(new RejectLine(E_MISSING_HEADER, raw, "missing header"));
                        detailsRejected++;
                        continue;
                    }
                    DetailParse dp = parseDetail(currentReason, raw);
                    currentReason = dp.reason;
                    if (currentReason != null) {
                        rejects.add(new RejectLine(currentReason, raw, expand(currentReason)));
                        detailsRejected++;
                        currentReason = null;
                    } else {
                        decoded.add(toTransaction(dp, batchId, businessDate));
                        detailsDecoded++;
                    }
                }
                case "T" -> {
                    if (!headerSeen) {
                        rejects.add(new RejectLine(E_MISSING_HEADER, raw, "missing header"));
                        detailsRejected++;
                        continue;
                    }
                    trailerSeen = true;
                    trailerExpectedCount = parseTrailerRecordCount(raw);
                }
                default -> {
                    rejects.add(new RejectLine(E_OTHER, raw, "unknown rec type"));
                    detailsRejected++;
                }
            }
        }

        // VERIFY-TRAILER 相当: 件数 / チェックサム整合
        boolean checksumMatch = true;
        String finalReason = null;
        if (!trailerSeen) {
            rejects.add(new RejectLine(E_DUP_TRAILER, "", "trailer missing"));
            detailsRejected++;
            checksumMatch = false;
            finalReason = E_DUP_TRAILER;
        } else if (trailerExpectedCount != null && trailerExpectedCount != detailsDecoded) {
            rejects.add(new RejectLine(E_TRAILER_COUNT_MISMATCH, "",
                    "trailer expected=" + trailerExpectedCount + " actual=" + detailsDecoded));
            detailsRejected++;
            checksumMatch = false;
            finalReason = E_TRAILER_COUNT_MISMATCH;
        }

        long total = detailsDecoded + detailsRejected;
        int rejectPct = total == 0 ? 0 : (int) (detailsRejected * 100 / total);

        // FINALIZE-OUTPUT 相当: INTI-OUTPUT 構築
        String status = computeStatus(headerSeen, trailerSeen, finalReason, rejectPct, rejectThresholdPct);
        IntiOutput output = new IntiOutput(
                status, recordsRead, detailsDecoded, detailsRejected,
                rejectPct, checksumMatch, 0);

        return new DecodeResult(decoded, rejects, output);
    }

    /** PARSE-DETAIL-FIELDS + TRANSLATE-CAT + TRANSLATE-DATE + VALIDATE-AMOUNT + CHECK-ACCT-FORMAT を 1 トランザクション分行う */
    private static DetailParse parseDetail(String preReason, String raw) {
        String reason = preReason;
        long bank = num(raw, 1, 4);
        // branch at offset 5 (3) は 2 回出てくる — COBOL版と同じ読み方 (上書き)
        long branchCode = num(raw, 5, 3);
        int catIn = (int) num(raw, 8, 2);
        long amount = num(raw, 10, 15);
        String payerAcct = str(raw, 25, 13);
        String payeeAcct = str(raw, 38, 13);
        String desc = str(raw, 51, 120);
        long seq = num(raw, 171, 10);
        branchCode = num(raw, 181, 3); // 上書き (COBOL版に合わせる)
        long productCode = num(raw, 184, 3);
        int yy = (int) num(raw, 187, 2);
        int mm = (int) num(raw, 189, 2);
        int dd = (int) num(raw, 191, 2);

        String catOut = null;
        if (catIn == 10) catOut = "10";
        else if (catIn == 20) catOut = "20";
        else if (catIn == 30) catOut = "30";
        else if (catIn == 40) catOut = "40";
        else reason = E_INVALID_CATEGORY;

        // 日付変換 (E110)
        int yyyy = (yy < 50) ? 2000 + yy : 1900 + yy;
        long dateYyyy = yyyy * 10000L + mm * 100L + dd;
        if (mm == 0 || mm > 12 || dd == 0 || dd > 31) {
            if (reason == null) reason = E_INVALID_DATE;
        }

        // 金額 (E108)
        if (amount == 0 && reason == null) {
            reason = E_ZERO_AMOUNT;
        }

        // 口座形式 (E106) — 数値のみ 13 桁
        if (!isNumeric13(payerAcct) && reason == null) {
            reason = E_INVALID_ACCT;
        }

        LocalDate businessDate = (reason == null && mm >= 1 && mm <= 12 && dd >= 1 && dd <= 31)
                ? LocalDate.of(yyyy, mm, dd) : LocalDate.of(1970, 1, 1);
        // ↑ 日付不正でも transactions 書き出しの例外を防ぐため epoch にフォールバック
        //   (ただし reason != null なら当該行は reject されるので businessDate は使われない)

        return new DetailParse(
                reason, seq, catOut, amount, payerAcct, payeeAcct,
                String.valueOf(branchCode), String.valueOf(productCode), desc,
                String.valueOf(bank), String.valueOf(branchCode), businessDate,
                dateYyyy);
    }

    private static long parseTrailerRecordCount(String raw) {
        return num(raw, 1, 10);
    }

    private static DecodedTransaction toTransaction(DetailParse dp, String batchId, long businessDate) {
        return new DecodedTransaction(
                "D", dp.seq, dp.catOut, dp.amount, "JPY", dp.payerAcct, dp.payeeAcct,
                dp.branchCode, dp.productCode, dp.desc, dp.sourceBank, dp.sourceBranch,
                dp.businessDate, batchId);
    }

    /** COBOL ACCUMULATE-CHECKSUM: 各文字の ORD を累積 mod 65536 */
    private static long accumulate(long acc, String raw) {
        if (raw == null) return acc;
        long sum = acc;
        for (int i = 0; i < raw.length() && i < 800; i++) {
            sum += raw.charAt(i);
        }
        return Math.floorMod(sum, 65536);
    }

    private static long num(String raw, int startZeroBased, int len) {
        if (raw == null) return 0;
        int from = startZeroBased;
        int to = Math.min(startZeroBased + len, raw.length());
        if (from >= raw.length()) return 0;
        String sub = raw.substring(from, to).trim();
        if (sub.isEmpty()) return 0;
        try {
            return Long.parseLong(sub);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String str(String raw, int startZeroBased, int len) {
        if (raw == null) return "";
        int from = startZeroBased;
        int to = Math.min(startZeroBased + len, raw.length());
        if (from >= raw.length()) return "";
        return raw.substring(from, to).trim();
    }

    private static boolean isNumeric13(String s) {
        if (s == null || s.isBlank()) return false;
        String t = s.trim();
        if (t.length() != 13) return false;
        for (int i = 0; i < t.length(); i++) {
            if (!Character.isDigit(t.charAt(i))) return false;
        }
        return true;
    }

    /** INTI-OUTPUT の INTI-STATUS を算出 (FINALIZE-OUTPUT 相当) */
    private static String computeStatus(boolean headerSeen, boolean trailerSeen,
                                        String finalReason, int rejectPct, int thresholdPct) {
        if (!headerSeen || !trailerSeen) return "04"; // PARTIAL
        if (finalReason != null) return "04";          // トレイラ不一致
        if (rejectPct > thresholdPct) return "04";    // 拒否率超過
        return "00";                                  // OK
    }

    private static String expand(String reason) {
        return switch (reason) {
            case E_MISSING_HEADER -> "missing header";
            case E_DUP_TRAILER -> "duplicate or missing trailer";
            case E_TRAILER_COUNT_MISMATCH -> "trailer record count mismatch";
            case E_INVALID_CATEGORY -> "invalid category";
            case E_INVALID_ACCT -> "invalid acct format";
            case E_ZERO_AMOUNT -> "zero amount";
            case E_INVALID_DATE -> "invalid date";
            default -> "other rejection";
        };
    }

    /** 1 明細パース結果 (中間раб位ト) */
    private record DetailParse(
            String reason,
            long seq,
            String catOut,
            long amount,
            String payerAcct,
            String payeeAcct,
            String branchCode,
            String productCode,
            String desc,
            String sourceBank,
            String sourceBranch,
            java.time.LocalDate businessDate,
            long dateYyyy
    ) {
    }

    /** 妥当明細 + 拒否一覧 + INTI-OUTPUT の束 */
    public record DecodeResult(
            List<DecodedTransaction> decoded,
            List<RejectLine> rejects,
            IntiOutput output
    ) {
    }

    /** 1 行分の拒否記録 (reject ファイル 1 行に相当) */
    public record RejectLine(String reason, String rawHead80, String expandedReason) {
        /** COBOL REJECT-LINE の "reason | raw(80) | reason" 形式 */
        public String toLine() {
            String head = rawHead80 == null ? "" : rawHead80;
            if (head.length() > 80) head = head.substring(0, 80);
            return reason + " | " + head + " | " + expandedReason;
        }
    }
}
