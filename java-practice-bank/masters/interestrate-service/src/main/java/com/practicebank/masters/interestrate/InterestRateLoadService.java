package com.practicebank.masters.interestrate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.List;

/**
 * 金利マスタのデータロード処理。
 *
 * <p>COBOL の {@code IRATE-LOAD} (初回データロード) に対応する。
 * シードデータ ({@code interestrates-mvp.dat} 相当) を {@code interest_rates} テーブルへ一括登録する。
 * 重複キー (product_code, effective_date) に遭遇した場合はスキップし、件数として報告する
 * (COBOL 版が INVALID KEY 時に重複件数を算出する挙動と同等)。
 *
 * <p>各 INSERT を独立したトランザクションで実行する。
 * {@code interest_rates_pkey} 違反でトランザクションが abort しても他のレコードに影響しない。
 */
@Service
public class InterestRateLoadService {

    private static final Logger log = LoggerFactory.getLogger(InterestRateLoadService.class);

    private final InterestRateRepository repository;
    private final PlatformTransactionManager txManager;

    public InterestRateLoadService(InterestRateRepository repository, PlatformTransactionManager txManager) {
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
     * 引数で渡された金利リストを 1 件ずつ登録する。
     *
     * <p>重複キー ({@code product_code, effective_date}) による {@link DuplicateKeyException} は
     * レコード個別のトランザクション境界で捕捉し、スキップ件数として集計する。
     *
     * @param rates ロード対象の金利リスト
     * @return 正常登録数 / スキップ数
     */
    public LoadResult load(List<InterestRate> rates) {
        int loaded = 0;
        int skipped = 0;

        for (InterestRate r : rates) {
            TransactionStatus status = txManager.getTransaction(new DefaultTransactionDefinition());
            try {
                repository.insert(r);
                txManager.commit(status);
                loaded++;
            } catch (DuplicateKeyException ex) {
                txManager.rollback(status);
                log.warn("IRATE-LOAD skip duplicate product_code={} effective_date={}",
                        r.productCode(), r.effectiveDate());
                skipped++;
            } catch (RuntimeException ex) {
                txManager.rollback(status);
                throw ex;
            }
        }

        log.info("IRATE-LOAD complete loaded={} dups={}", loaded, skipped);
        return new LoadResult(loaded, skipped);
    }
}
