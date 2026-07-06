package com.practicebank.masters.accountlifecycle.program;

import com.practicebank.masters.accountlifecycle.domain.Account;
import com.practicebank.masters.accountlifecycle.repository.AccountLifecycleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 休眠口座一括移行バッチ (COBOL ALC-DORMANCY-SCAN 対応)。
 *
 * <p>Active 口座 (status="A") を総ざらいし、休眠基準日 (business-date から 730 日前)
 * を超過した口座を 1 件ずつ "D" へ移行する。各遷移は監査証拠として記録する
 * (COBOL の CALL "AUD-Write" 相当。本実装では構造化ログで代替)。
 *
 * <p>処理後は遷移・スキップ件数を出力する。いずれも 0 なら 04 (NO-CANDS) を返す
 * (COBOL: ファイル空または全移行済)。
 */
@Service
public class AlcDormancyScanService {

    private static final Logger log = LoggerFactory.getLogger(AlcDormancyScanService.class);

    /** 休眠判定日数 (COBOL: 730 日 = 2 年)。 */
    private static final int DORMANCY_DAYS = 730;

    private final AccountLifecycleRepository repository;

    public AlcDormancyScanService(AccountLifecycleRepository repository) {
        this.repository = repository;
    }

    /**
     * ALC-DORMANCY-SCAN の入力。
     *
     * @param businessDate バッチ業務日付 (YYYYMMDD)
     */
    public record DormancyInput(LocalDate businessDate) {
    }

    /**
     * ALC-DORMANCY-SCAN の出力。
     *
     * @param status       返却コード (00/04/12)
     * @param transitioned "D" へ移行した件数
     * @param skipped      条件不一致でスキップした件数
     */
    public record DormancyResult(ProgramStatus status, int transitioned, int skipped) {
    }

    /**
     * 休眠スキャンを実行する。
     *
     * <p>基準日 = businessDate - 730 日。status="A" かつ dormancyDate &lt; 基準日
     * のレコードを "D" に変更し、各遷移の監査証拠を記録する。
     */
    public DormancyResult scan(DormancyInput input) {
        try {
            long totalRows = repository.countAll();

            // ファイル空 (COBOL: レコードなし → status=04 NO-CANDS)
            if (totalRows == 0) {
                log.info("ALC-DORMANCY scan no-cands (empty table)");
                return new DormancyResult(ProgramStatus.NOT_FOUND, 0, 0);
            }

            LocalDate thresholdDate = input.businessDate().minusDays(DORMANCY_DAYS);
            List<String> candidates = repository.findActiveBeforeDormancyDate(thresholdDate);

            int transitioned = 0;
            for (String acctNumber : candidates) {
                int updated = repository.updateStatus(
                        acctNumber, Account.STATUS_ACTIVE, Account.STATUS_DORMANT, null);
                if (updated > 0) {
                    // 監査証拠 (COBOL: CALL AUD-Write STATUS_CHANGED from=A to=D reason=dormancy_24mo)
                    log.info("ALC-DORMANCY audit STATUS_CHANGED acct={} from=A to=D reason=dormancy_24mo", acctNumber);
                    transitioned++;
                }
                // updated == 0 は楽観ロック失敗 (他プロセスが先に更新) → COBOL の REWRITE FS 失敗相当
            }

            int skipped = (int) totalRows - transitioned;

            log.info("ALC-DORMANCY scan complete transitioned={} skipped={} threshold={}",
                    transitioned, skipped, thresholdDate);
            return new DormancyResult(ProgramStatus.OK, transitioned, skipped);
        } catch (RuntimeException ex) {
            log.error("ALC-DORMANCY scan IO failure", ex);
            return new DormancyResult(ProgramStatus.IO_FAIL, 0, 0);
        }
    }
}
