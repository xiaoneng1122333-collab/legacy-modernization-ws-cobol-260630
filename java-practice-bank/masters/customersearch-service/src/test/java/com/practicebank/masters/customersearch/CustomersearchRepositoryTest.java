package com.practicebank.masters.customersearch;

import com.practicebank.common.test.PostgresTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql(scripts = "/sql/customersearch.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class CustomersearchRepositoryTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer container = PostgresTestContainer.getInstance();
        container.start();
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
    }

    @Autowired
    private CustomersearchRepository repository;

    @BeforeEach
    void setUp() {
        // Full table scan on each test – fine for small fixtures.
    }

    // ── CSRCH-AND ──────────────────────────────────────────────────────
    @Nested
    @DisplayName("CSRCH-AND")
    class CsrchAnd {

        @Test
        @DisplayName("kana and phone prefix → returns matching customer with status=0")
        void matchingKanaAndPhone_returnsCustomer() {
            CustomersearchOutput out = repository.csrchAnd("タナカ", "03");

            assertThat(out.isOk()).isTrue();
            assertThat(out.matchId()).isEqualTo(1L);
            assertThat(out.matchKana()).isEqualTo("タナカ タロウ");
            assertThat(out.matchKanji()).isEqualTo("田中 太郎");
            assertThat(out.matchPhone()).isEqualTo("0312345678");
            assertThat(out.lastId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("kana matches but phone differs → EOF status=10")
        void noIntersection_returnsEof() {
            CustomersearchOutput out = repository.csrchAnd("スズキ", "03");

            assertThat(out.isEof()).isTrue();
            assertThat(out.status()).isEqualTo(10);
            assertThat(out.matchId()).isNull();
        }

        @Test
        @DisplayName("empty result → EOF status=10")
        void emptyPrefix_returnsEof() {
            CustomersearchOutput out = repository.csrchAnd("XXX", "999");

            assertThat(out.isEof()).isTrue();
        }
    }

    // ── CSRCH-BY-ADDRESS ───────────────────────────────────────────────
    @Nested
    @DisplayName("CSRCH-BY-ADDRESS")
    class CsrchByAddress {

        @Test
        @DisplayName("address contains 渋谷区 → first match returned with status=0")
        void addressSubstringMatch_returnsCustomer() {
            CustomersearchOutput out = repository.csrchByAddress("渋谷区");

            assertThat(out.isOk()).isTrue();
            assertThat(out.matchId()).isEqualTo(2L); // first by id
            assertThat(out.matchAddr()).contains("渋谷区");
        }

        @Test
        @DisplayName("address not found → EOF status=10")
        void noSubstringMatch_returnsEof() {
            CustomersearchOutput out = repository.csrchByAddress("存在しない地名");

            assertThat(out.isEof()).isTrue();
        }
    }

    // ── CSRCH-LIST-PAGED ───────────────────────────────────────────────
    @Nested
    @DisplayName("CSRCH-LIST-PAGED")
    class CsrchListPaged {

        @Test
        @DisplayName("start=0, page-size=5 → 5 customers ordered by id (first 5 by id)")
        void firstPage_returnsFive() {
            List<Customer> page = repository.csrchListPaged(0L, 5);

            assertThat(page).hasSize(5);
            // First 5 by id: 1, 2, 3, 10, 11
            assertThat(page).extracting(Customer::id).containsExactly(1L, 2L, 3L, 10L, 11L);
        }

        @Test
        @DisplayName("start-after=100, page-size=3 → ids 101..103")
        void subsequentPage_startsAfterCursor() {
            List<Customer> page = repository.csrchListPaged(100L, 3);

            assertThat(page).hasSize(3);
            assertThat(page).extracting(Customer::id).containsExactly(101L, 102L, 103L);
        }

        @Test
        @DisplayName("page beyond last row → empty list")
        void beyondLastPage_returnsEmpty() {
            List<Customer> page = repository.csrchListPaged(999L, 5);
            assertThat(page).isEmpty();
        }

        @Test
        @DisplayName("csrchListPagedNext → next row after given id")
        void nextRow_returnsSuccessor() {
            CustomersearchOutput out = repository.csrchListPagedNext(104L);

            assertThat(out.isOk()).isTrue();
            assertThat(out.matchId()).isEqualTo(105L);
            assertThat(out.lastId()).isEqualTo(105L);
        }

        @Test
        @DisplayName("csrchListPagedNext on last row → EOF")
        void nextRowAtEnd_returnsEof() {
            CustomersearchOutput out = repository.csrchListPagedNext(109L);
            assertThat(out.isEof()).isTrue();
        }
    }
}
