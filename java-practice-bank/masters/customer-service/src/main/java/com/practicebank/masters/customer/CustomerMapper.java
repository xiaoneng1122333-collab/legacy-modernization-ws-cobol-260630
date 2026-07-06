package com.practicebank.masters.customer;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/** 顧客マスタテーブル (customers) への MyBatis マッパー。 */
@Mapper
public interface CustomerMapper {

    @Select("""
            SELECT cust_id, cust_name, cust_name_kana, cust_status, tier, phone, address
              FROM customers
             WHERE cust_id = #{custId}
            """)
    Optional<Customer> findById(@Param("custId") String custId);

    @Select("""
            SELECT cust_id, cust_name, cust_name_kana, cust_status, tier, phone, address
              FROM customers
             ORDER BY cust_id
            """)
    List<Customer> findAll();

    @Select("""
            SELECT cust_id, cust_name, cust_name_kana, cust_status, tier, phone, address
              FROM customers
             WHERE cust_name_kana LIKE #{prefix} || '%'
             ORDER BY cust_name_kana, cust_id
            """)
    List<Customer> findByNameKana(@Param("prefix") String prefix);

    @Select("""
            SELECT cust_id, cust_name, cust_name_kana, cust_status, tier, phone, address
              FROM customers
             WHERE phone = #{phone}
             ORDER BY cust_id
            """)
    List<Customer> findByPhone(@Param("phone") String phone);

    @Update("""
            UPDATE customers
               SET cust_status = #{custStatus}, updated_at = NOW()
             WHERE cust_id = #{custId}
            """)
    int updateStatus(@Param("custId") String custId, @Param("custStatus") String custStatus);
}
