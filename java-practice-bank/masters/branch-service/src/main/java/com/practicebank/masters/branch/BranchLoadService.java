package com.practicebank.masters.branch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.List;

/**
 * 支店マスタのデータロード処理。
 *
 * <p>COBOL の {@code BR-LOAD} (初回データロード) に対応する。
 * シードデータ ({@code branches-mvp.dat} 相当) を {@code branches} テーブルへ一括登録する。
 * 重複キー (支店コード) に遭遇した場合はスキップし、件数として報告する
 * (COBOL 版が INVALID KEY 時に重複件数を算出する挙動と同等)。
 *
 * <p>各 INSERT を独立したトランザクションで実行する。
 * {@code branches_pkey} 違反でトランザクションが abort しても他のレコードに影響しない。
 */
@Service
public class BranchLoadService {

    private static final Logger log = LoggerFactory.getLogger(BranchLoadService.class);

    private final BranchRepository repository;
    private final PlatformTransactionManager txManager;

    public BranchLoadService(BranchRepository repository, PlatformTransactionManager txManager) {
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
     * 引数で渡された支店リストを 1 件ずつ登録する。
     *
     * <p>重複キー ({@code branch_code}) による {@link DuplicateKeyException} は
     * レコード個別のトランザクション境界で捕捉し、スキップ件数として集計する。
     *
     * @param branches ロード対象の支店リスト
     * @return 正常登録数 / スキップ数
     */
    public LoadResult load(List<Branch> branches) {
        int loaded = 0;
        int skipped = 0;

        for (Branch b : branches) {
            TransactionStatus status = txManager.getTransaction(new DefaultTransactionDefinition());
            try {
                repository.insert(b);
                txManager.commit(status);
                loaded++;
            } catch (DuplicateKeyException ex) {
                txManager.rollback(status);
                log.warn("BR-LOAD skip duplicate branch_code={}", b.branchCode());
                skipped++;
            } catch (RuntimeException ex) {
                txManager.rollback(status);
                throw ex;
            }
        }

        log.info("BR-LOAD complete loaded={} dups={}", loaded, skipped);
        return new LoadResult(loaded, skipped);
    }
}
