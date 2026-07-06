package com.practicebank.masters.accountlifecycle.program;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 休眠口座再活性化バッチ (COBOL ALC-REACTIVATION-SCAN 対応)。
 *
 * <p>MVP スタブ。現状は入力を未使用で 04 (NO-CANDS) を返却するプレースホルダー。
 * 将来実装される「休眠口座 (status="D") を再活性化 ("A") へ戻す」バッチの
 * インタフェースを先取りする。
 */
@Service
public class AlcReactivationScanService {

    private static final Logger log = LoggerFactory.getLogger(AlcReactivationScanService.class);

    /**
     * ALC-REACTIVATION-SCAN の入力。
     *
     * @param businessDate バッチ業務日付 (YYYYMMDD)。将来の再活性化判定に使用
     */
    public record ReactivationInput(LocalDate businessDate) {
    }

    /**
     * ALC-REACTIVATION-SCAN の出力。
     *
     * @param status       返却コード (現状: 常に 04 NO-CANDS)
     * @param transitioned 再活性化した件数 (現状: 0)
     * @param skipped      スキップ件数 (現状: 0)
     */
    public record ReactivationResult(ProgramStatus status, int transitioned, int skipped) {
    }

    /**
     * 再活性化スキャンを実行する (MVP スタブ)。
     *
     * <p>現状はカウンタを 0 で初期化し、status=04 を返却するのみ。
     * COBOL の GOBACK 相当。
     */
    public ReactivationResult scan(ReactivationInput input) {
        log.info("ALC-REACTIVATION scan (MVP stub) businessDate={}", input.businessDate());
        // COBOL: TRANSITIONED=0, SKIPPED=0, status=04 NO-CANDS
        return new ReactivationResult(ProgramStatus.NOT_FOUND, 0, 0);
    }
}
