package com.practicebank.masters.product;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/** 製品マスタテーブル (m_products) への MyBatis マッパー。 */
@Mapper
public interface ProductMapper {

    @Select("""
            SELECT product_code, product_name, product_type,
                   interest_eligible, fee_eligible, min_balance_jpy,
                   created_at, updated_at
              FROM m_products
             WHERE product_code = #{productCode}
            """)
    Optional<Product> findById(@Param("productCode") String productCode);

    @Select("""
            SELECT product_code, product_name, product_type,
                   interest_eligible, fee_eligible, min_balance_jpy,
                   created_at, updated_at
              FROM m_products
             ORDER BY product_code
            """)
    List<Product> findAll();

    @Insert("""
            INSERT INTO m_products (product_code, product_name, product_type,
                                    interest_eligible, fee_eligible, min_balance_jpy)
                 VALUES (#{productCode}, #{productName}, #{productType},
                         #{interestEligible}, #{feeEligible}, #{minBalanceJpy})
            """)
    int insert(Product product);

    @Update("""
            UPDATE m_products
               SET product_name = #{productName},
                   product_type = #{productType},
                   interest_eligible = #{interestEligible},
                   fee_eligible = #{feeEligible},
                   min_balance_jpy = #{minBalanceJpy}
             WHERE product_code = #{productCode}
            """)
    int update(Product product);
}
