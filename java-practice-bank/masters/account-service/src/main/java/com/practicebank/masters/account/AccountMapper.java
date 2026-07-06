package com.practicebank.masters.account;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 口座マスタテーブル (accounts) への MyBatis マッパー。
 *
 * <p>COBOL の 5 プログラムに対応する操作を提供する:
 * <ul>
 *   <li>ACCT-EXISTS              → {@link #findByNumber(String)}</li>
 *   <li>ACCT-LOAD                → {@link #insert(Account)}</li>
 *   <li>ACCT-LOOKUP              → {@link #findByNumber(String)}</li>
 *   <li>ACCT-LOOKUP-BY-CUSTOMER  → {@link #findByCustId(String)}</li>
 *   <li>ACCT-UPDATE-DORMANCY-DATE → {@link #updateDormancyDate(String, LocalDate)}</li>
 * </ul>
 */
@Mapper
public interface AccountMapper {

    /**
     * ACCT-EXISTS / ACCT-LOOKUP: 口座番号 (主キー) による単一検索。
     */
    Optional<Account> findByNumber(@Param("acctNumber") String acctNumber);

    /**
     * ACCT-LOAD: 1 件の口座レコードを登録する。
     */
    int insert(@Param("a") Account account);

    /**
     * ACCT-LOOKUP-BY-CUSTOMER: 顧客番号が一致する口座を全件取得する。
     *
     * <p>COBOL 版は ISAM 代替キー (CUST-ID, WITH DUPLICATES) でスキャンしていたが、
     * RDB 版では {@code accounts.cust_id} 列でフィルタする。
     */
    List<Account> findByCustId(@Param("custId") String custId);

    /**
     * ACCT-UPDATE-DORMANCY-DATE: 口座番号で休眠日を更新する。
     *
     * <p>更新対象が存在しない場合は 0 が返る。
     *
     * @return 更新件数 (0 or 1)
     */
    int updateDormancyDate(@Param("acctNumber") String acctNumber,
                           @Param("dormancyDate") LocalDate dormancyDate);
}
