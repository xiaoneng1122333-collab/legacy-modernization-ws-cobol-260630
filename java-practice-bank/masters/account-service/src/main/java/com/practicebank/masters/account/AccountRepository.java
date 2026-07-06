package com.practicebank.masters.account;

import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** AccountMapper を呼び出すリポジトリ。 */
@Repository
public class AccountRepository {

    private final AccountMapper mapper;

    public AccountRepository(AccountMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * ACCT-EXISTS / ACCT-LOOKUP: 口座番号 (主キー) で 1 件取得する。
     * 該当なしの場合は {@link Optional#empty()} を返す (COBOL の 04 NOT-FOUND に対応)。
     */
    public Optional<Account> findByNumber(String acctNumber) {
        return mapper.findByNumber(acctNumber);
    }

    /**
     * ACCT-LOOKUP-BY-CUSTOMER: 顧客番号が一致する口座を全件取得する。
     */
    public List<Account> findByCustId(String custId) {
        return mapper.findByCustId(custId);
    }

    /**
     * ACCT-LOAD: 1 件の口座レコードを登録する。
     */
    public int insert(Account account) {
        return mapper.insert(account);
    }

    /**
     * ACCT-UPDATE-DORMANCY-DATE: 休眠日を更新する。
     *
     * @return 更新件数 (0 or 1)
     */
    public int updateDormancyDate(String acctNumber, LocalDate dormancyDate) {
        return mapper.updateDormancyDate(acctNumber, dormancyDate);
    }
}
