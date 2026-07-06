package com.practicebank.masters.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.List;

/**
 * 口座マスタのデータロード処理。
 *
 * <p>COBOL の {@code ACCT-LOAD} (初回データロード) に対応する。
 * シードデータ ({@code accounts-mvp.dat} 相当) を {@code accounts} テーブルへ一括登録する。
 * 重複キー (口座番号) に遭遇した場合はスキップし、件数として報告する
 * (COBOL 版が INVALID KEY 時に重複件数を算出する挙動と同等)。
 *
 * <p>各 INSERT を独立したトランザクションで実行する。
 * {@code accounts_pkey} 違反でトランザクションが abort しても他のレコードに影響しない。
 */
@Service
public class AccountLoadService {

    private static final Logger log = LoggerFactory.getLogger(AccountLoadService.class);

    private final AccountRepository repository;
    private final PlatformTransactionManager txManager;

    public AccountLoadService(AccountRepository repository, PlatformTransactionManager txManager) {
        this.repository = repository;
        this.txManager = txManager;
    }

    /**
     * ロード結果。
     *
     * @param loaded  正常に登録された件数
     * @param skipped 重複等によりスキップされた件数
     */
    public record LoadResult(int loaded, int skipped) {
    }

    /**
     * 引数で渡された口座リストを 1 件ずつ登録する。
     *
     * <p>重複キー ({@code acct_number}) による {@link DuplicateKeyException} は
     * レコード個別のトランザクション境界で捕捉し、スキップ件数として集計する。
     *
     * @param accounts ロード対象の口座リスト
     * @return 正常登録数 / スキップ数
     */
    public LoadResult load(List<Account> accounts) {
        int loaded = 0;
        int skipped = 0;

        for (Account a : accounts) {
            TransactionStatus status = txManager.getTransaction(new DefaultTransactionDefinition());
            try {
                repository.insert(a);
                txManager.commit(status);
                loaded++;
            } catch (DuplicateKeyException ex) {
                txManager.rollback(status);
                log.warn("ACCT-LOAD skip duplicate acct_number={}", a.acctNumber());
                skipped++;
            } catch (RuntimeException ex) {
                txManager.rollback(status);
                throw ex;
            }
        }

        log.info("ACCT-LOAD complete loaded={} dups={}", loaded, skipped);
        return new LoadResult(loaded, skipped);
    }
}
