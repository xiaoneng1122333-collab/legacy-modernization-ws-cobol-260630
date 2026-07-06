package com.practicebank.masters.accountlifecycle;

import com.practicebank.common.test.PostgresTestContainer;
import com.practicebank.masters.accountlifecycle.program.AlcChangeStateService;
import com.practicebank.masters.accountlifecycle.program.AlcChangeStateService.ChangeInput;
import com.practicebank.masters.accountlifecycle.program.AlcChangeStateService.ChangeResult;
import com.practicebank.masters.accountlifecycle.program.AlcDormancyScanService;
import com.practicebank.masters.accountlifecycle.program.AlcDormancyScanService.DormancyInput;
import com.practicebank.masters.accountlifecycle.program.AlcDormancyScanService.DormancyResult;
import com.practicebank.masters.accountlifecycle.program.AlcOpenService;
import com.practicebank.masters.accountlifecycle.program.AlcOpenService.OpenInput;
import com.practicebank.masters.accountlifecycle.program.AlcOpenService.OpenResult;
import com.practicebank.masters.accountlifecycle.program.AlcReactivationScanService;
import com.practicebank.masters.accountlifecycle.program.AlcReactivationScanService.ReactivationInput;
import com.practicebank.masters.accountlifecycle.program.AlcReactivationScanService.ReactivationResult;
import com.practicebank.masters.accountlifecycle.program.ProgramStatus;
import com.practicebank.masters.accountlifecycle.repository.AccountLifecycleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Sql(scripts = "/sql/accounts.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AccountLifecycleServiceTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer container = PostgresTestContainer.getInstance();
        container.start();
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
    }

    @Autowired
    private AlcOpenService openService;

    @Autowired
    private AlcChangeStateService changeStateService;

    @Autowired
    private AlcDormancyScanService dormancyScanService;

    @Autowired
    private AlcReactivationScanService reactivationScanService;

    @Autowired
    private AccountLifecycleRepository repository;

    // ══════════════════════════════════════════════════════════════════════
    // ALC-OPEN
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void open_basicBranch001Product001_returnsNewAccountNumber() {
        OpenResult result = openService.open(new OpenInput(
                "99", "001", "001", LocalDate.of(2026, 6, 1), 0L, 0));

        assertThat(result.status()).isEqualTo(ProgramStatus.OK);
        assertThat(result.acctNumber()).isNotNull();
        // branch=001 + product=001 + serial(7) = 13 桁
        assertThat(result.acctNumber()).hasSize(13);
        assertThat(result.acctNumber()).startsWith("001001");
        // branch=001 product=001 に既存なし -> 9000000
        assertThat(result.acctNumber()).isEqualTo("0010019000000");
    }

    @Test
    void open_secondCall_serialIncrements() {
        OpenResult first = openService.open(new OpenInput(
                "99", "001", "001", LocalDate.of(2026, 6, 1), 0L, 0));
        OpenResult second = openService.open(new OpenInput(
                "99", "001", "001", LocalDate.of(2026, 6, 1), 0L, 0));

        assertThat(first.status()).isEqualTo(ProgramStatus.OK);
        assertThat(second.status()).isEqualTo(ProgramStatus.OK);
        assertThat(second.acctNumber()).isGreaterThan(first.acctNumber());
        // 2 回目は 9000001
        assertThat(second.acctNumber()).isEqualTo("0010019000001");
    }

    @Test
    void open_newBranch_startsAt9000000() {
        OpenResult result = openService.open(new OpenInput(
                "99", "001", "003", LocalDate.of(2026, 6, 1), 0L, 0));

        assertThat(result.status()).isEqualTo(ProgramStatus.OK);
        // branch=003 に既存なし -> 9000000
        assertThat(result.acctNumber()).isEqualTo("0030019000000");
    }

    @Test
    void open_zeroCustId_returnsInvalid() {
        OpenResult result = openService.open(new OpenInput(
                "0", "000", "001", LocalDate.of(2026, 6, 1), 0L, 0));

        assertThat(result.status()).isEqualTo(ProgramStatus.INVALID);
        assertThat(result.acctNumber()).isNull();
    }

    @Test
    void open_zeroBranch_returnsInvalid() {
        OpenResult result = openService.open(new OpenInput(
                "99", "000", "0", LocalDate.of(2026, 6, 1), 0L, 0));

        assertThat(result.status()).isEqualTo(ProgramStatus.INVALID);
    }

    @Test
    void open_zeroProduct_returnsInvalid() {
        OpenResult result = openService.open(new OpenInput(
                "99", "0", "001", LocalDate.of(2026, 6, 1), 0L, 0));

        assertThat(result.status()).isEqualTo(ProgramStatus.INVALID);
    }

    @Test
    void open_insertedAccount_hasStatusPending() {
        OpenResult result = openService.open(new OpenInput(
                "99", "001", "003", LocalDate.of(2026, 6, 1), 100000L, 365));

        assertThat(result.status()).isEqualTo(ProgramStatus.OK);
        var account = repository.findByNumber(result.acctNumber());
        assertThat(account).isPresent();
        assertThat(account.orElseThrow().acctStatus()).isEqualTo("P");
        assertThat(account.orElseThrow().overdraftLimit()).isEqualTo(100000L);
        assertThat(account.orElseThrow().termDays()).isEqualTo(365);
    }

    // ══════════════════════════════════════════════════════════════════════
    // ALC-CHANGE-STATE
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void changeState_pendingToActive_success() {
        ChangeResult result = changeStateService.changeState(new ChangeInput(
                "0010009000000", "AC", null, LocalDate.of(2026, 6, 1)));

        assertThat(result.status()).isEqualTo(ProgramStatus.OK);
        assertThat(result.fromStatus()).isEqualTo("P");
        assertThat(result.targetStatus()).isEqualTo("A");
        assertThat(repository.findByNumber("0010009000000").orElseThrow().acctStatus()).isEqualTo("A");
    }

    @Test
    void changeState_pendingToCancel_success() {
        ChangeResult result = changeStateService.changeState(new ChangeInput(
                "0010009000001", "CN", null, LocalDate.of(2026, 6, 1)));

        assertThat(result.status()).isEqualTo(ProgramStatus.OK);
        assertThat(result.fromStatus()).isEqualTo("P");
        assertThat(result.targetStatus()).isEqualTo("C");
    }

    @Test
    void changeState_activeToSuspend_withReason_success() {
        ChangeResult result = changeStateService.changeState(new ChangeInput(
                "0010009000010", "SU", "fraud investigation", LocalDate.of(2026, 6, 1)));

        assertThat(result.status()).isEqualTo(ProgramStatus.OK);
        assertThat(result.fromStatus()).isEqualTo("A");
        assertThat(result.targetStatus()).isEqualTo("S");
    }

    @Test
    void changeState_suspendToActive_success() {
        ChangeResult result = changeStateService.changeState(new ChangeInput(
                "0010009000020", "LS", null, LocalDate.of(2026, 6, 1)));

        assertThat(result.status()).isEqualTo(ProgramStatus.OK);
        assertThat(result.fromStatus()).isEqualTo("S");
        assertThat(result.targetStatus()).isEqualTo("A");
    }

    @Test
    void changeState_activeToClose_setsClosedDate() {
        ChangeResult result = changeStateService.changeState(new ChangeInput(
                "0010009000010", "CL", null, LocalDate.of(2026, 6, 15)));

        assertThat(result.status()).isEqualTo(ProgramStatus.OK);
        assertThat(result.fromStatus()).isEqualTo("A");
        assertThat(result.targetStatus()).isEqualTo("C");
        var account = repository.findByNumber("0010009000010").orElseThrow();
        assertThat(account.acctStatus()).isEqualTo("C");
        assertThat(account.closedDate()).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    void changeState_forceClose_fromSuspended_setsClosedDate() {
        ChangeResult result = changeStateService.changeState(new ChangeInput(
                "0010009000020", "FC", "operator forced", LocalDate.of(2026, 6, 15)));

        assertThat(result.status()).isEqualTo(ProgramStatus.OK);
        assertThat(result.fromStatus()).isEqualTo("S");
        assertThat(result.targetStatus()).isEqualTo("C");
        assertThat(repository.findByNumber("0010009000020").orElseThrow().closedDate())
                .isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    void changeState_unknownAction_returnsInvalid() {
        ChangeResult result = changeStateService.changeState(new ChangeInput(
                "0010009000000", "ZZ", null, LocalDate.of(2026, 6, 1)));

        assertThat(result.status()).isEqualTo(ProgramStatus.INVALID);
    }

    @Test
    void changeState_pendingToSuspend_disallowedTransition_returnsInvalid() {
        // SU は A/D からのみ許可 (P からは不可)
        ChangeResult result = changeStateService.changeState(new ChangeInput(
                "0010009000000", "SU", "reason", LocalDate.of(2026, 6, 1)));

        assertThat(result.status()).isEqualTo(ProgramStatus.INVALID);
    }

    @Test
    void changeState_suspendWithoutReason_returnsInvalid() {
        // SU は reason 必須
        ChangeResult result = changeStateService.changeState(new ChangeInput(
                "0010009000010", "SU", "   ", LocalDate.of(2026, 6, 1)));

        assertThat(result.status()).isEqualTo(ProgramStatus.INVALID);
    }

    @Test
    void changeState_forceCloseWithoutReason_returnsInvalid() {
        // FC は reason 必須
        ChangeResult result = changeStateService.changeState(new ChangeInput(
                "0010009000010", "FC", null, LocalDate.of(2026, 6, 1)));

        assertThat(result.status()).isEqualTo(ProgramStatus.INVALID);
    }

    @Test
    void changeState_notFound_returnsNotFound() {
        ChangeResult result = changeStateService.changeState(new ChangeInput(
                "9999999999999", "AC", null, LocalDate.of(2026, 6, 1)));

        assertThat(result.status()).isEqualTo(ProgramStatus.NOT_FOUND);
    }

    // ══════════════════════════════════════════════════════════════════════
    // ALC-DORMANCY-SCAN
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void dormancyScan_transitionsOverThresholdAccounts() {
        // BusinessDate=2029-06-01 -> 基準日=2027-06-02
        // 0010009000010 (dormancy=2026-06-01) と 0010009000011 (dormancy=2025-01-01) が対象
        DormancyResult result = dormancyScanService.scan(new DormancyInput(LocalDate.of(2029, 6, 1)));

        assertThat(result.status()).isEqualTo(ProgramStatus.OK);
        assertThat(result.transitioned()).isEqualTo(2);
        assertThat(result.skipped()).isPositive(); // 他レコードはスキップ

        assertThat(repository.findByNumber("0010009000010").orElseThrow().acctStatus()).isEqualTo("D");
        assertThat(repository.findByNumber("0010009000011").orElseThrow().acctStatus()).isEqualTo("D");
        // 基準日内の A 口座はそのまま
        assertThat(repository.findByNumber("0010009000012").orElseThrow().acctStatus()).isEqualTo("A");
    }

    @Test
    void dormancyScan_businessDateNearNow_noTransition() {
        // BusinessDate=2026-06-01 -> 基準日=2024-06-02
        // 全 A 口座の dormancy_date は 2026-06-01 以降なので移行なし
        DormancyResult result = dormancyScanService.scan(new DormancyInput(LocalDate.of(2026, 6, 1)));

        assertThat(result.status()).isEqualTo(ProgramStatus.OK);
        assertThat(result.transitioned()).isEqualTo(0);
        assertThat(result.skipped()).isPositive();
    }

    // ══════════════════════════════════════════════════════════════════════
    // ALC-REACTIVATION-SCAN (MVP スタブ)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void reactivationScan_stub_returnsNoCands() {
        ReactivationResult result = reactivationScanService.scan(
                new ReactivationInput(LocalDate.of(2026, 6, 1)));

        assertThat(result.status()).isEqualTo(ProgramStatus.NOT_FOUND); // 04 NO-CANDS
        assertThat(result.transitioned()).isEqualTo(0);
        assertThat(result.skipped()).isEqualTo(0);
    }
}
