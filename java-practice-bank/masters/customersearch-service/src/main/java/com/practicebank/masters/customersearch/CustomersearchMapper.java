package com.practicebank.masters.customersearch;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/** 顧客マスタテーブル (customers) への MyBatis マッパー。 */
@Mapper
public interface CustomersearchMapper {

    /**
     * CSRCH-AND: カナ前方一致 AND 電話前方一致の積集合検索。
     * カナ両方に前方一致する顧客を ID 順で返す (最大 100 件)。
     */
    @Select("""
            SELECT CAST(cust_id AS BIGINT) AS cust_id,
                   cust_name_kana, cust_name, phone, address
              FROM customers
             WHERE cust_name_kana LIKE CONCAT(#{kana}, '%')
               AND phone         LIKE CONCAT(#{phone}, '%')
             ORDER BY cust_id
             LIMIT 100
            """)
    List<Customer> searchByKanaAndPhone(
            @Param("kana") String kana,
            @Param("phone") String phone);

    /**
     * CSRCH-BY-ADDRESS: 住所部分一致検索。
     * address に部分文字列を含む最初の 1 件を返す。
     */
    @Select("""
            SELECT CAST(cust_id AS BIGINT) AS cust_id,
                   cust_name_kana, cust_name, phone, address
              FROM customers
             WHERE address LIKE CONCAT('%', #{substr}, '%')
             ORDER BY cust_id
             LIMIT 1
            """)
    Optional<Customer> searchByAddress(@Param("substr") String substr);

    /**
     * CSRCH-LIST-PAGED: ページング全件取得。
     * start-after より大きい ID を ID 順で page-size 件返す。
     */
    @Select("""
            SELECT CAST(cust_id AS BIGINT) AS cust_id,
                   cust_name_kana, cust_name, phone, address
              FROM customers
             WHERE CAST(cust_id AS BIGINT) > #{startAfter}
             ORDER BY cust_id
             LIMIT #{pageSize}
            """)
    List<Customer> listPaged(
            @Param("startAfter") Long startAfter,
            @Param("pageSize") int pageSize);
}
