package com.practicebank.masters.product;

import com.practicebank.common.test.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Sql(scripts = "/sql/product.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ProductRepositoryTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer container = PostgresTestContainer.getInstance();
        container.start();
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
    }

    @Autowired
    private ProductRepository repository;

    @Test
    void findById_returnsSavingsProduct() {
        Product product = repository.findById("001").orElseThrow();

        assertThat(product.productCode()).isEqualTo("001");
        assertThat(product.productName()).isEqualTo("普通預金");
        assertThat(product.productType()).isEqualTo("S");
        assertThat(product.interestEligible()).isEqualTo("Y");
        assertThat(product.feeEligible()).isEqualTo("Y");
    }

    @Test
    void findById_returnsTimeDepositProduct() {
        Product product = repository.findById("002").orElseThrow();

        assertThat(product.productCode()).isEqualTo("002");
        assertThat(product.productName()).isEqualTo("定期預金");
        assertThat(product.productType()).isEqualTo("T");
    }

    @Test
    void findById_returnsCheckingProduct() {
        Product product = repository.findById("003").orElseThrow();

        assertThat(product.productCode()).isEqualTo("003");
        assertThat(product.productName()).isEqualTo("当座預金");
        assertThat(product.productType()).isEqualTo("C");
    }

    @Test
    void findById_returnsEmptyForUnknownCode() {
        assertThat(repository.findById("999")).isEmpty();
    }

    @Test
    void findAll_returnsAllProducts() {
        List<Product> products = repository.findAll();

        assertThat(products).hasSize(3);
    }
}
