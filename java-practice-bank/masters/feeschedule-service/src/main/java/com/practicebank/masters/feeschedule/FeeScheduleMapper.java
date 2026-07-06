package com.practicebank.masters.feeschedule;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 手数料マスタテーブル (fee_schedules) への MyBatis マッパー。
 *
 * <p>COBOL の 2 プログラムに対応する操作を提供する:
 * <ul>
 *   <li>FEE-LOAD           → {@link #insert(FeeSchedule)} (一括は {@link FeeLoadService})</li>
 *   <li>FEE-LOOKUP-BY-TIER → {@link #lookupByTier(String, String, LocalDate)}</li>
 * </ul>
 */
@Mapper
public interface FeeScheduleMapper {

    /**
     * FEE-LOOKUP-BY-TIER: カテゴリ・ティヤ・有効開始日による単一検索。
     * <p>有効開始日以降で直近の料金レコードを返却（指定日時点で有効な料金検索）。
     */
    Optional<FeeSchedule> lookupByTier(
            @Param("category") String category,
            @Param("tier") String tier,
            @Param("effectiveDate") LocalDate effectiveDate);

    /**
     * FEE-LOAD: 1 件の手数料レコードを登録する。
     */
    int insert(@Param("f") FeeSchedule fee);

    /**
     * FEE-LOAD: 全手数料レコードを取得する。
     */
    List<FeeSchedule> findAll();
}
