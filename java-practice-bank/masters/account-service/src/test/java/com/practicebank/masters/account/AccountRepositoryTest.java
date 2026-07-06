package com.practicebank.masters.account;

import com.practicebank.common.test.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Sql(scripts = "/sql/account.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AccountRepositoryTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer container = PostgresTestContainer.getInstance();
        container.start();
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
    }

    @Autowired
    private AccountRepository repository;

    // --- ACCT-EXISTS / ACCT-LOOKUP ---

    @Test
    void findByNumber_existingAccount_returnsAccount() {
        Optional<Account> result = repository.findByNumber("0010030000001");

        assertThat(result).isPresent();
        Account a = result.orElseThrow();
        assertThat(a.acctNumber()).isEqualTo("0010030000001");
        assertThat(a.acctName()).isEqualTo("山田太郎");
        assertThat(a.branchCode()).isEqualTo("001");
        assertThat(a.productCode()).isEqualTo("003");
        assertThat(a.acctStatus()).isEqualTo("A");
        assertThat(a.custId()).isEqualTo("0000000002");
        assertThat(a.openedDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void findByNumber_notFound_returnsEmpty() {
        Optional<Account> result = repository.findByNumber("9999999999999");
        assertThat(result).isEmpty();
    }

    // --- ACCT-LOOKUP-BY-CUSTOMER ---

    @Test
    void findByCustId_existingCustomer_returnsAccounts() {
        List<Account> accounts = repository.findByCustId("0000000005");

        assertThat(accounts).hasSize(2);
        assertThat(accounts).extracting(Account::acctNumber)
                .containsExactly("0010010000006", "0070010000005");
    }

    @Test
    void findByCustId_unknownCustomer_returnsEmpty() {
        List<Account> accounts = repository.findByCustId("9999999999");
        assertThat(accounts).isEmpty();
    }

    // --- ACCT-LOAD ---

    @Autowired
    private AccountLoadService loadService;

    @Test
    void load_insertsAndDetectsDuplicate() {
        Account newAccount = new Account("0010030000999", "新規顧客", "001", "003", "A",
                "0000000099", LocalDate.of(2026, 6, 1), null);
        Account duplicate = new Account("0010030000001", "重複", "001", "003", "A",
                "0000000002", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));

        AccountLoadService.LoadResult result = loadService.load(
                List.of(newAccount, duplicate));

        assertThat(result.loaded()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(repository.findByNumber("0010030000999")).isPresent();
    }

    // --- ACCT-UPDATE-DORMANCY-DATE ---

    @Autowired
    private AccountUpdateDormancyService dormancyService;

    @Test
    void updateDormancyDate_forwardDate_returnsUpdated() {
        AccountUpdateDormancyService.UpdateResult result =
                dormancyService.updateDormancyDate("0010030000001", LocalDate.of(2026, 6, 1));

        assertThat(result.status()).isEqualTo("00");
        assertThat(result.prevDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(result.wasNoop()).isFalse();
    }

    @Test
    void updateDormancyDate_sameDate_returnsNoop() {
        // Seed data has dormancy_date = 2026-01-01; passing the same value must be NOOP
        AccountUpdateDormancyService.UpdateResult result =
                dormancyService.updateDormancyDate("0010030000001", LocalDate.of(2026, 1, 1));

        assertThat(result.status()).isEqualTo("00");
        assertThat(result.wasNoop()).isTrue();
        assertThat(result.prevDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void updateDormancyDate_notFound_returnsNotFound() {
        AccountUpdateDormancyService.UpdateResult result =
                dormancyService.updateDormancyDate("9999999999999", LocalDate.of(2026, 6, 1));

        assertThat(result.status()).isEqualTo("04");
        assertThat(result.prevDate()).isNull();
    }

    @Test
    void updateDormancyDate_invalidStatus_returnsInvalid() {
        // Account 0010010000006 has status P (not A or D)
        AccountUpdateDormancyService.UpdateResult result =
                dormancyService.updateDormancyDate("0010010000006", LocalDate.of(2026, 6, 1));

        assertThat(result.status()).isEqualTo("08");
    }

    @Test
    void updateDormancyDate_rollbackDate_returnsInvalid() {
        // dormancy_date is 2026-01-01, trying to set 2025-12-01
        AccountUpdateDormancyService.UpdateResult result =
                dormancyService.updateDormancyDate("0040010000003", LocalDate.of(2025, 12, 1));

        assertThat(result.status()).isEqualTo("08");
    }

    @Test
    void updateDormancyDate_before1900_returnsInvalid() {
        AccountUpdateDormancyService.UpdateResult result =
                dormancyService.updateDormancyDate("0010030000001", LocalDate.of(1800, 1, 1));

        assertThat(result.status()).isEqualTo("08");
    }
}
