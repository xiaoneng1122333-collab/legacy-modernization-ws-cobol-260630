package com.practicebank.masters.accountlifecycle.program;

import com.practicebank.masters.accountlifecycle.domain.Account;
import com.practicebank.masters.accountlifecycle.repository.AccountLifecycleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 新規口座開設 (COBOL ALC-OPEN 対応)。
 *
 * <p>branch + product プレフィクスに対し 9000000-9999999 の範囲で未使用の
 * 連番を採番し、13 桁口座番号を生成。ステータス "P" で INSERT する。
 *
 * <p>COBOL の採番ループ (9000000 から READ で空きを探す) と等価:
 * 既存番号をプレフィクス検索し、最初の空きスロットを決定する。
 */
@Service
public class AlcOpenService {

    private static final Logger log = LoggerFactory.getLogger(AlcOpenService.class);

    /** 連番の下限 (COBOL: 9000000)。 */
    private static final int SERIAL_MIN = 9_000_000;
    /** 連番の上限 (COBOL: 9999999)。 */
    private static final int SERIAL_MAX = 9_999_999;
    /** 連番部の桁数。 */
    private static final int SERIAL_LEN = 7;

    private final AccountLifecycleRepository repository;

    public AlcOpenService(AccountLifecycleRepository repository) {
        this.repository = repository;
    }

    /**
     * ALC-OPEN の入力。
     *
     * @param custId        顧客番号 (10 桁, 0 不可)
     * @param productCode   商品コード (3 桁, 0 不可)
     * @param branchCode    支店コード (3 桁, 0 不可)
     * @param openedDate    開設日 (YYYYMMDD)
     * @param overdraftLimit 当枠上限 (nullable)
     * @param termDays      契約日数 (nullable)
     */
    public record OpenInput(String custId, String productCode, String branchCode,
                            LocalDate openedDate, Long overdraftLimit, Integer termDays) {
    }

    /**
     * ALC-OPEN の出力。
     *
     * @param status    返却コード (00/08/12)
     * @param acctNumber 採番された口座番号 (失敗時 = null)
     */
    public record OpenResult(ProgramStatus status, String acctNumber) {
    }

    /**
     * 新規口座を開設する。
     *
     * <p>入力バリエーション (いずれかの必須項目が "0" または空白) では
     * status=08 で即座に返却する (COBOL の GOBACK 相当)。
     * 連番上限を超えた場合も 08 を返却する。
     */
    public OpenResult open(OpenInput input) {
        // 入力バリデーション (COBOL: CUST/PRODUCT/BRANCH = 0 → status=08)
        if (isZero(input.custId()) || isZero(input.productCode()) || isZero(input.branchCode())) {
            log.warn("ALC-OPEN invalid input: cust={} prod={} branch={}",
                    input.custId(), input.productCode(), input.branchCode());
            return new OpenResult(ProgramStatus.INVALID, null);
        }

        String prefix = input.branchCode() + input.productCode(); // 6 文字
        String acctNumber = nextFreeNumber(prefix);

        if (acctNumber == null) {
            // 連番上限超過 (COBOL: WS-SERIAL > 9999999 → status=08)
            log.warn("ALC-OPEN serial overflow prefix={}", prefix);
            return new OpenResult(ProgramStatus.INVALID, null);
        }

        Account account = new Account(
                acctNumber,
                padLeft(input.custId(), 10),
                padLeft(input.productCode(), 3),
                padLeft(input.branchCode(), 3),
                Account.STATUS_PENDING,
                input.openedDate(),
                null,                    // closed_date
                input.overdraftLimit(),
                input.termDays(),
                null                     // dormancy_date
        );

        try {
            repository.insert(account);
            log.info("ALC-OPEN opened acct={} cust={} branch={} product={}",
                    acctNumber, input.custId(), input.branchCode(), input.productCode());
            return new OpenResult(ProgramStatus.OK, acctNumber);
        } catch (RuntimeException ex) {
            // WRITE 失敗 (COBOL: WS-FS != "00" → status=12)
            log.error("ALC-OPEN IO failure acct={}", acctNumber, ex);
            return new OpenResult(ProgramStatus.IO_FAIL, null);
        }
    }

    /**
     * プレフィクスに対し、9000000-9999999 の範囲で未使用の最小連番を採番する。
     *
     * <p>COBOL が LOOP で READ して空きを探す処理と等価。
     *
     * @return 採番結果の 13 桁口座番号。空きがない場合は null。
     */
    private String nextFreeNumber(String prefix) {
        List<String> existing = repository.findNumbersByPrefix(prefix);

        // 連番部分を Set に収集 (数値順にソート済想定だが、保証のため TreeSet)
        java.util.TreeSet<Integer> used = new java.util.TreeSet<>();
        int prefixLen = prefix.length();
        for (String num : existing) {
            if (num != null && num.length() > prefixLen) {
                try {
                    used.add(Integer.parseInt(num.substring(prefixLen)));
                } catch (NumberFormatException ignored) {
                    // 数値で無視できる_suffix はスキップ
                }
            }
        }

        int serial = SERIAL_MIN;
        while (serial <= SERIAL_MAX && used.contains(serial)) {
            serial++;
        }
        if (serial > SERIAL_MAX) {
            return null; // 空きなし
        }
        return prefix + String.format("%0" + SERIAL_LEN + "d", serial);
    }

    /** 数値文字列が "0" または空白かを判定 (COBOL のゼロチェック相当)。 */
    private boolean isZero(String value) {
        return value == null || value.isBlank() || value.trim().matches("^0+$");
    }

    /** 数値を左ゼロパディングして固定長文字列にする。 */
    private String padLeft(String value, int length) {
        if (value == null) {
            return "0".repeat(length);
        }
        String digits = value.trim();
        if (digits.length() >= length) {
            return digits.substring(digits.length() - length);
        }
        return "0".repeat(length - digits.length()) + digits;
    }
}
