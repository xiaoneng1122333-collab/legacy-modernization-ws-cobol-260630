package com.practicebank.masters.accountlifecycle.mapper;

import com.practicebank.masters.accountlifecycle.domain.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 口座マスタテーブル (accounts) への MyBatis マッパー。
 *
 * <p>COBOL の 4 プログラムに対応する操作を提供する:
 * <ul>
 *   <li>ALC-OPEN            → {@link #findNumbersByPrefix(String)}, {@link #insert(Account)}</li>
 *   <li>ALC-CHANGE-STATE    → {@link #findByNumber(String)}, {@link #updateStatus}</li>
 *   <li>ALC-DORMANCY-SCAN   → {@link #findActiveBeforeDormancyDate(LocalDate)}, {@link #updateStatus}</li>
 *   <li>ALC-REACTIVATION-SCAN → (MVP スタブ: DB 未使用)</li>
 * </ul>
 */
@Mapper
public interface AccountLifecycleMapper {

    /**
     * ALC-OPEN: 枝番採番のため、branch+product プレフィクスに一致する
     * 既存口座番号を検索する。
     *
     * @param prefix branchCode(3) + productCode(3) の 6 文字
     * @return 該当する口座番号のリスト
     */
    List<String> findNumbersByPrefix(@Param("prefix") String prefix);

    /**
     * ALC-OPEN: 新規口座を 1 件登録する (status="P")。
     */
    int insert(@Param("a") Account account);

    /**
     * ALC-CHANGE-STATE: 口座番号 (主キー) で 1 件取得する。
     * 該当なしの場合は {@link Optional#empty()} を返す (COBOL の 04 NOT-FOUND に対応)。
     */
    Optional<Account> findByNumber(@Param("acctNumber") String acctNumber);

    /**
     * ALC-CHANGE-STATE / ALC-DORMANCY-SCAN: ステータスを更新する。
     *
     * <p>COBOL の REWRITE に対応。楽観的ロックとして {@code expectedStatus} を
     * WHERE 条件に含め、一致した行のみ更新する。
     *
     * @param acctNumber     更新対象の口座番号
     * @param expectedStatus 更新前に期待する現在ステータス (楽観ロック)
     * @param newStatus      新ステータス
     * @param closedDate     解約日 (Close 系遷移時のみ設定, それ以外 null)
     * @return 更新行数 (0 は期待ステータス不一致)
     */
    int updateStatus(@Param("acctNumber") String acctNumber,
                     @Param("expectedStatus") String expectedStatus,
                     @Param("newStatus") String newStatus,
                     @Param("closedDate") LocalDate closedDate);

    /**
     * ALC-DORMANCY-SCAN: 休眠基準日を超過した Active 口座を検索する。
     *
     * <p>status="A" かつ dormancyDate &lt; thresholdDate の行を返す。
     * dormancyDate が NULL の口座は取引実績なしとみなさず対象外とする設計とする
     * (COBOL では PIC 9(8) の初期値 00000000 比較だが、RDB では NULL を安全側に倒す)。
     *
     * @param thresholdDate 基準日 (businessDate - 730 日)
     * @return 移行対象の口座番号リスト
     */
    List<String> findActiveBeforeDormancyDate(@Param("thresholdDate") LocalDate thresholdDate);

    /**
     * ALC-DORMANCY-SCAN: 全件スキャン時のスキップ集計用に総行数を取得する。
     * ファイル空 (0 件) 判定 (COBOL の 04 NO-CANDS) に使用する。
     */
    long countAll();
}
