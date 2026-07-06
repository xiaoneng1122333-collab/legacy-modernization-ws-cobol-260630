package com.practicebank.masters.accountlifecycle.repository;

import com.practicebank.masters.accountlifecycle.domain.Account;
import com.practicebank.masters.accountlifecycle.mapper.AccountLifecycleMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** AccountLifecycleMapper を呼び出すリポジトリ。 */
@Repository
public class AccountLifecycleRepository {

    private final AccountLifecycleMapper mapper;

    public AccountLifecycleRepository(AccountLifecycleMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * ALC-OPEN: プレフィクスに一致する既存口座番号を検索 (枝番採番用)。
     */
    public List<String> findNumbersByPrefix(String prefix) {
        return mapper.findNumbersByPrefix(prefix);
    }

    /**
     * ALC-OPEN: 新規口座を 1 件登録する。
     */
    public int insert(Account account) {
        return mapper.insert(account);
    }

    /**
     * ALC-CHANGE-STATE: 口座番号で 1 件取得する。
     * 該当なしの場合は {@link Optional#empty()} を返す (COBOL の 04 NOT-FOUND に対応)。
     */
    public Optional<Account> findByNumber(String acctNumber) {
        return mapper.findByNumber(acctNumber);
    }

    /**
     * ALC-CHANGE-STATE / ALC-DORMANCY-SCAN: ステータスを更新する (楽観ロック付き)。
     *
     * @return 更新行数 (0 は期待ステータス不一致)
     */
    public int updateStatus(String acctNumber, String expectedStatus,
                            String newStatus, LocalDate closedDate) {
        return mapper.updateStatus(acctNumber, expectedStatus, newStatus, closedDate);
    }

    /**
     * ALC-DORMANCY-SCAN: 休眠基準日超過の Active 口座番号を検索する。
     */
    public List<String> findActiveBeforeDormancyDate(LocalDate thresholdDate) {
        return mapper.findActiveBeforeDormancyDate(thresholdDate);
    }

    /**
     * ALC-DORMANCY-SCAN: 総行数 (ファイル空判定用)。
     */
    public long countAll() {
        return mapper.countAll();
    }
}
