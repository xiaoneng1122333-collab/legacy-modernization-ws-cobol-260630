package com.practicebank.masters.interestrate;

import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** InterestRateMapper を呼び出すリポジトリ。 */
@Repository
public class InterestRateRepository {

    private final InterestRateMapper mapper;

    public InterestRateRepository(InterestRateMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * IRATE-LOOKUP: 商品コード + 適用日 (複合主キー) で 1 件取得する。
     * 該当なしの場合は {@link Optional#empty()} を返す (COBOL の 04 NOT-FOUND に対応)。
     */
    public Optional<InterestRate> findByProductAndDate(String productCode, LocalDate effectiveDate) {
        return mapper.findByProductAndDate(productCode, effectiveDate);
    }

    /**
     * 全金利レコードを商品コード・適用日昇順で取得する。
     */
    public List<InterestRate> findAll() {
        return mapper.findAll();
    }

    /**
     * 指定商品コードに一致する金利レコードを全件取得する。
     */
    public List<InterestRate> findByProductCode(String productCode) {
        return mapper.findByProductCode(productCode);
    }

    /**
     * IRATE-LOAD: 1 件の金利レコードを登録する。
     */
    public int insert(InterestRate interestRate) {
        return mapper.insert(interestRate);
    }
}
