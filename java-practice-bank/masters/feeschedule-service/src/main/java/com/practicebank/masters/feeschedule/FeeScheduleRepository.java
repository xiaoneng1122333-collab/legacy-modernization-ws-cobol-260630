package com.practicebank.masters.feeschedule;

import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** FeeScheduleMapper を呼び出すリポジトリ。 */
@Repository
public class FeeScheduleRepository {

    private final FeeScheduleMapper mapper;

    public FeeScheduleRepository(FeeScheduleMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * FEE-LOOKUP-BY-TIER: カテゴリ・ティヤ・有効開始日で 1 件取得する。
     * <p>有効開始日以降で直近の料金（fee_effective_date ≦ 指定日）を返却する。
     * 該当なしの場合は {@link Optional#empty()} を返す (COBOL の 04 NOT-FOUND に対応)。
     */
    public Optional<FeeSchedule> lookupByTier(String category, String tier, LocalDate effectiveDate) {
        return mapper.lookupByTier(category, tier, effectiveDate);
    }

    /**
     * FEE-LOAD: 1 件の手数料レコードを登録する。
     */
    public int insert(FeeSchedule fee) {
        return mapper.insert(fee);
    }

    /**
     * FEE-LOAD: 全手数料レコードを取得する。
     */
    public List<FeeSchedule> findAll() {
        return mapper.findAll();
    }
}
