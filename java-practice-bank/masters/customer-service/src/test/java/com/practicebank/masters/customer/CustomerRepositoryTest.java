package com.practicebank.masters.customer;

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
@Sql(scripts = "/sql/customer.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class CustomerRepositoryTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer container = PostgresTestContainer.getInstance();
        container.start();
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
    }

    @Autowired
    private CustomerRepository repository;

    @Autowired
    private CustomerStatusChangeService statusChangeService;

    @Test
    void findById_returnsCustomerRecord() {
        Customer cust = repository.findById("0000000002").orElseThrow();

        assertThat(cust.custId()).isEqualTo("0000000002");
        assertThat(cust.custName()).contains("田中");
    }

    @Test
    void findAll_returnsAllRecords() {
        List<Customer> all = repository.findAll();
        assertThat(all).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void findByNameKana_prefixSearch() {
        List<Customer> tanakaList = repository.findByNameKana("タナカ");

        assertThat(tanakaList).hasSize(2);
        assertThat(tanakaList).allMatch(c -> c.custNameKana().startsWith("タナカ"));
    }

    @Test
    void findByPhone_matchesMultipleCustomers() {
        List<Customer> matches = repository.findByPhone("03-1234-5678");

        assertThat(matches).hasSize(2);
    }

    @Test
    void statusChange_updatesAndWritesAudit() {
        CustomerStatusChangeService.StatusChangeResult result =
                statusChangeService.changeStatus("0000000005", "S", "20260701");

        assertThat(result.statusCode()).isEqualTo(0);
        assertThat(result.customer().custStatus()).isEqualTo("S");

        Customer after = repository.findById("0000000005").orElseThrow();
        assertThat(after.custStatus()).isEqualTo("S");
    }

    @Test
    void statusChange_notFoundReturns04() {
        CustomerStatusChangeService.StatusChangeResult result =
                statusChangeService.changeStatus("9999999999", "S", "20260701");

        assertThat(result.statusCode()).isEqualTo(4);
        assertThat(result.customer()).isNull();
    }
}
