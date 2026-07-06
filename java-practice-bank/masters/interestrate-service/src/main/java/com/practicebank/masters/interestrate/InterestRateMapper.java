package com.practicebank.masters.interestrate;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 金利マスタテーブル (interest_rates) への MyBatis マッパー。
 *
 * <p>COBOL の 2 プログラムに対応する操作を提供する:
 * <ul>
 *   <li>IRATE-LOOKUP → {@link #findByProductAndDate(String, LocalDate)}</li>
 *   <li>IRATE-LOAD   → {@link #insert(InterestRate)} (一括は {@link InterestRateLoadService})</li>
 * </ul>
 */
@Mapper
public interface InterestRateMapper {

    /**
     * IRATE-LOOKUP: 商品コード + 適用日 (複合主キー) による単一検索。
     */
    Optional<InterestRate> findByProductAndDate(@Param("productCode") String productCode,
                                                @Param("effectiveDate") LocalDate effectiveDate);

    /**
     * IRATE-LOAD: 1 件の金利レコードを登録する。
     */
    int insert(@Param("ir") InterestRate interestRate);

    /**
     * 全金利レコードを商品コード・適用日昇順で取得する。
     */
    List<InterestRate> findAll();

    /**
     * 指定商品コードに一致する金利レコードを全件取得する (適用日昇順)。
     */
    List<InterestRate> findByProductCode(@Param("productCode") String productCode);
}
