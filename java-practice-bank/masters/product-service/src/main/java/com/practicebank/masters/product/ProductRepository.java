package com.practicebank.masters.product;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** ProductMapper を呼び出すリポジトリ。 */
@Repository
public class ProductRepository {

    private final ProductMapper mapper;

    public ProductRepository(ProductMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<Product> findById(String productCode) {
        return mapper.findById(productCode);
    }

    public List<Product> findAll() {
        return mapper.findAll();
    }

    public int insert(Product product) {
        return mapper.insert(product);
    }

    public int update(Product product) {
        return mapper.update(product);
    }
}
