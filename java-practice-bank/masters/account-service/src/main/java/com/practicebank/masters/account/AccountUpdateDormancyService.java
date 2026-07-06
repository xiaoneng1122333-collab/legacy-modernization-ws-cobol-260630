package com.practicebank.masters.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 口座マスタの休眠日更新処理。
 *
 * <p>COBOL の {@code ACCT-UPDATE-DORMANCY-DATE} に対応する。
 * 口座番号でレコードを取得し、ステータスが "A" (ACTIVE) または "D" (DORMANT) の場合のみ
 * 休眠日を更新する。
 *
 * <p>更新ルール (COBOL 版と同等):
 * <ul>
 *   <li>新しい休眠日が 1900-01-01..9999-12-31 の範囲外 → 拒否 (INVALID)</li>
 *   <li>新しい休眠日が既存の休眠日より前 (巻戻し) → 拒否 (INVALID)</li>
 *   <li>新しい休眠日が既存と同値 → NOOP (更新せず WAS-NOOP=Y)</li>
 *   <li>ステータスが A/D 以外 → 拒否 (INVALID)</li>
 * </ul>
 */
@Service
public class AccountUpdateDormancyService {

    private static final Logger log = LoggerFactory.getLogger(AccountUpdateDormancyService.class);

    private final AccountRepository repository;

    public AccountUpdateDormancyService(AccountRepository repository) {
        this.repository = repository;
    }

    /**
     * 更新結果。
     *
     * @param status    API 結果コード ("00"=正常, "04"=NOT-FOUND, "08"=INVALID)
     * @param prevDate  更新前の休眠日 (該当なしの場合は null)
     * @param wasNoop   true = 同値更新スキップ
     */
    public record UpdateResult(String status, LocalDate prevDate, boolean wasNoop) {
    }

    /**
     * 口座番号で休眠日を更新する。
     *
     * <p>トランザクションは呼び出し元 (または Spring デフォルト) に委ねる。
     * 本メソッドは 1 レコードの読取 → バリデーション → 更新の原子動作。
     *
     * @param acctNumber 更新対象の口座番号
     * @param newDate    新しい休眠日 (YYYYMMDD 相当)
     * @return 更新結果
     */
    @Transactional
    public UpdateResult updateDormancyDate(String acctNumber, LocalDate newDate) {
        // ACCT-UPDATE-DORMANCY-DATE: 入力範囲チェック (1900-01-01..9999-12-31)
        if (newDate.isBefore(LocalDate.of(1900, 1, 1))) {
            log.warn("ACCT-UPDATE-DORMANCY-DATE invalid newDate={} (before 1900-01-01)", newDate);
            return new UpdateResult("08", null, false);
        }

        Optional<Account> found = repository.findByNumber(acctNumber);
        if (found.isEmpty()) {
            log.warn("ACCT-UPDATE-DORMANCY-DATE not-found acctNumber={}", acctNumber);
            return new UpdateResult("04", null, false);
        }

        Account account = found.get();

        // ACCT-UPDATE-DORMANCY-DATE: ステータスが A または D のみ更新可
        if (!"A".equals(account.acctStatus()) && !"D".equals(account.acctStatus())) {
            log.warn("ACCT-UPDATE-DORMANCY-DATE invalid-status acctNumber={} status={}",
                    acctNumber, account.acctStatus());
            return new UpdateResult("08", account.dormancyDate(), false);
        }

        LocalDate prevDate = account.dormancyDate();

        // ACCT-UPDATE-DORMANCY-DATE: 前回日付巻戻し拒否
        if (prevDate != null && newDate.isBefore(prevDate)) {
            log.warn("ACCT-UPDATE-DORMANCY-DATE rollback-rejected acctNumber={} prev={} new={}",
                    acctNumber, prevDate, newDate);
            return new UpdateResult("08", prevDate, false);
        }

        // ACCT-UPDATE-DORMANCY-DATE: 同値 → NOOP
        if (prevDate != null && newDate.isEqual(prevDate)) {
            log.info("ACCT-UPDATE-DORMANCY-DATE noop acctNumber={} date={}", acctNumber, newDate);
            return new UpdateResult("00", prevDate, true);
        }

        repository.updateDormancyDate(acctNumber, newDate);
        log.info("ACCT-UPDATE-DORMANCY-DATE updated acctNumber={} prev={} new={}",
                acctNumber, prevDate, newDate);
        return new UpdateResult("00", prevDate, false);
    }
}
