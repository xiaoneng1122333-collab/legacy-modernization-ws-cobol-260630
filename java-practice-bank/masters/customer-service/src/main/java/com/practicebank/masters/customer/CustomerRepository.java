package com.practicebank.masters.customer;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** CustomerMapper を呼び出すリポジトリ。 */
@Repository
public class CustomerRepository {

    private final CustomerMapper mapper;

    public CustomerRepository(CustomerMapper mapper) {
        this.mapper = mapper;
    }

    /** CUST-LOOKUP: 顧客 ID をキーに 1 件検索。 */
    public Optional<Customer> findById(String custId) {
        return mapper.findById(custId);
    }

    /** CUST-LIST-ALL: 全件を主キー順で取得。 */
    public List<Customer> findAll() {
        return mapper.findAll();
    }

    /** CUST-SEARCH-BY-KANA: カナ名前方一致検索。 */
    public List<Customer> findByNameKana(String prefix) {
        return mapper.findByNameKana(prefix);
    }

    /** CUST-SEARCH-BY-PHONE: 電話番号完全一致検索（WITH DUPLICATES）。 */
    public List<Customer> findByPhone(String phone) {
        return mapper.findByPhone(phone);
    }
}
