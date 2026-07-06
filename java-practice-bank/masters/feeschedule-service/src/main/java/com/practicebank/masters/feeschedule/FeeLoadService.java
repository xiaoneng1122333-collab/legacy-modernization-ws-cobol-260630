package com.practicebank.masters.feeschedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.List;

/**
 * 手数料マスタのデータロード処理。
 *
 * <p>COBOL の {@code FEE-LOAD} (初回データロード) に対応する。
 * シードデータ ({@code feeschedules-mvp.dat} 相当) を {@code fee_schedules} テーブルへ一括登録する。
 * 重複キー (カテゴリ+ティヤ+有効開始日) に遭遇した場合はスキップし、件数として報告する。
 *
 * <p>各 INSERT を独立したトランザクションで実行する。
 * {@code fee_schedules_pkey} 違反でトランザクションが abort しても他のレコードに影響しない。
 */
@Service
public class FeeLoadService {

    private static final Logger log = LoggerFactory.getLogger(FeeLoadService.class);

    private final FeeScheduleRepository repository;
    private final PlatformTransactionManager txManager;

    public FeeLoadService(FeeScheduleRepository repository, PlatformTransactionManager txManager) {
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
     * 引数で渡された手数料リストを 1 件ずつ登録する。
     *
     * <p>重複キー ({@code category, tier, effective_date}) による
     * {@link DuplicateKeyException} はレコード個別のトランザクション境界で捕捉し、
     * スキップ件数として集計する。
     *
     * @param fees ロード対象の手数料リスト
     * @return 正常登録数 / スキップ数
     */
    public LoadResult load(List<FeeSchedule> fees) {
        int loaded = 0;
        int skipped = 0;

        for (FeeSchedule f : fees) {
            TransactionStatus status = txManager.getTransaction(new DefaultTransactionDefinition());
            try {
                repository.insert(f);
                txManager.commit(status);
                loaded++;
            } catch (DuplicateKeyException ex) {
                txManager.rollback(status);
                log.warn("FEE-LOAD skip duplicate category={} tier={} effectiveDate={}",
                        f.category(), f.tier(), f.effectiveDate());
                skipped++;
            } catch (RuntimeException ex) {
                txManager.rollback(status);
                throw ex;
            }
        }

        log.info("FEE-LOAD complete loaded={} dups={}", loaded, skipped);
        return new LoadResult(loaded, skipped);
    }
}
