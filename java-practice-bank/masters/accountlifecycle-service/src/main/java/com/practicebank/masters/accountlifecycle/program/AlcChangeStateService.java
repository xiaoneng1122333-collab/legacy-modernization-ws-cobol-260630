package com.practicebank.masters.accountlifecycle.program;

import com.practicebank.masters.accountlifecycle.domain.Account;
import com.practicebank.masters.accountlifecycle.repository.AccountLifecycleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * 口座ステート遷移 (COBOL ALC-CHANGE-STATE 対応)。
 *
 * <p>口座番号でレコードを取得し、ACTION-CODE と現在ステータスから
 * 次ステータスを FSM 評価する。遷移許可時は REWRITE (UPDATE) し、
 * Close 系遷移 (CL/FC) 時は解約日を補完する。
 *
 * <p>状態機械 (P→A→S→L→C→F→D):
 * <pre>
 *   AC: P → A           (Activate)
 *   CN: P → C           (Cancel)
 *   SU: A/D → S         (Suspend, reason 必須)
 *   LS: S → A           (Lift Suspend)
 *   CL: A/D → C         (Close)
 *   FC: not C → C       (Force Close, reason 必須)
 * </pre>
 */
@Service
public class AlcChangeStateService {

    private static final Logger log = LoggerFactory.getLogger(AlcChangeStateService.class);

    private final AccountLifecycleRepository repository;

    public AlcChangeStateService(AccountLifecycleRepository repository) {
        this.repository = repository;
    }

    /**
     * ALC-CHANGE-STATE の入力。
     *
     * @param acctNumber   更新対象の口座番号 (13 桁)
     * @param actionCode   遷移指示 (AC/CN/SU/LS/CL/FC)
     * @param reasonText   SU / FC 時のみ必須
     * @param businessDate 業務日付 (YYYYMMDD)。Close 時に CLOSED-DATE へ転写
     */
    public record ChangeInput(String acctNumber, String actionCode,
                              String reasonText, LocalDate businessDate) {
    }

    /**
     * ALC-CHANGE-STATE の出力。
     *
     * @param status       返却コード (00/04/08/12)
     * @param fromStatus   変更前の口座ステータス (失敗時 = null)
     * @param targetStatus 変更後の口座ステータス (失敗時 = null)
     */
    public record ChangeResult(ProgramStatus status, String fromStatus, String targetStatus) {
    }

    // ── FSM 遷移定義 ────────────────────────────────────────────────────────
    // key: ACTION-CODE, value: 遷移元ステータス集合 → 次ステータス
    private static final Map<String, Transition> TRANSITIONS = Map.of(
            "AC", new Transition(Set.of("P"), "A", false),
            "CN", new Transition(Set.of("P"), "C", false),
            "SU", new Transition(Set.of("A", "D"), "S", true),
            "LS", new Transition(Set.of("S"), "A", false),
            "CL", new Transition(Set.of("A", "D"), "C", false),
            "FC", new Transition(Set.of("P", "A", "S", "D"), "C", true)
    );

    /** 1 遷移の定義。 */
    private record Transition(Set<String> fromStates, String toState, boolean reasonRequired) {
    }

    /**
     * ステート遷移を実行する。
     *
     * <p>フロー:
     * <ol>
     *   <li>ACTION-CODE 妥当性チェック → 未知なら 08</li>
     *   <li>口座存在チェック → 不在なら 04</li>
     *   <li>FSM 評価 (ACTION × 現在状態) → 不正遷移なら 08</li>
     *   <li>reason 不足 (SU/FC) → 08</li>
     *   <li>UPDATE (楽観ロック) → 失敗なら 12</li>
     * </ol>
     */
    public ChangeResult changeState(ChangeInput input) {
        String action = input.actionCode() == null ? "" : input.actionCode().trim().toUpperCase();

        // ACTION-CODE 妥当性 (COBOL: WHEN OTHER → status=08)
        Transition transition = TRANSITIONS.get(action);
        if (transition == null) {
            log.warn("ALC-CHANGE invalid action={}", input.actionCode());
            return new ChangeResult(ProgramStatus.INVALID, null, null);
        }

        // 口座存在チェック (COBOL: INVALID KEY → status=04)
        Account account = repository.findByNumber(input.acctNumber()).orElse(null);
        if (account == null) {
            log.warn("ALC-CHANGE not found acct={}", input.acctNumber());
            return new ChangeResult(ProgramStatus.NOT_FOUND, null, null);
        }

        String currentStatus = account.acctStatus();

        // FSM 遷移可否 (COBOL: フェーズ不正 → status=08)
        if (!transition.fromStates().contains(currentStatus)) {
            log.warn("ALC-CHANGE disallowed transition action={} from={}", action, currentStatus);
            return new ChangeResult(ProgramStatus.INVALID, null, null);
        }

        // reason 必須バリデーション (COBOL: SU/FC で reason 空白 → status=08)
        if (transition.reasonRequired() && isBlank(input.reasonText())) {
            log.warn("ALC-CHANGE reason required action={} acct={}", action, input.acctNumber());
            return new ChangeResult(ProgramStatus.INVALID, null, null);
        }

        String targetStatus = transition.toState();
        // Close 系遷移 (CL/FC) → CLOSED-DATE に business-date を設定
        LocalDate closedDate = ("C".equals(targetStatus)) ? input.businessDate() : account.closedDate();

        try {
            int updated = repository.updateStatus(input.acctNumber(), currentStatus, targetStatus, closedDate);
            if (updated == 0) {
                // 楽観ロック失敗 (他プロセスが先に更新) → COBOL の REWRITE FS 失敗相当
                log.warn("ALC-CHANGE rewrite conflict acct={}", input.acctNumber());
                return new ChangeResult(ProgramStatus.IO_FAIL, null, null);
            }
            log.info("ALC-CHANGE status changed acct={} from={} to={} action={}",
                    input.acctNumber(), currentStatus, targetStatus, action);
            return new ChangeResult(ProgramStatus.OK, currentStatus, targetStatus);
        } catch (RuntimeException ex) {
            log.error("ALC-CHANGE IO failure acct={}", input.acctNumber(), ex);
            return new ChangeResult(ProgramStatus.IO_FAIL, null, null);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
