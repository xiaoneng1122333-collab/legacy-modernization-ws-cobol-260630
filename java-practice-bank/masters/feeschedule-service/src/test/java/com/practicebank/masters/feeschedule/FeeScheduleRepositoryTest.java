package com.practicebank.masters.feeschedule;

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
@Sql(scripts = "/sql/fee_schedule.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class FeeScheduleRepositoryTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer container = PostgresTestContainer.getInstance();
        container.start();
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
    }

    @Autowired
    private FeeScheduleRepository repository;

    // --- FEE-LOOKUP-BY-TIER ---

    @Test
    void lookupByTier_cat40_tier1_effective20260101_returnsZeroFee() {
        Optional<FeeSchedule> result = repository.lookupByTier("40", "1", LocalDate.of(2026, 1, 1));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().feeJpy()).isZero();
    }

    @Test
    void lookupByTier_cat40_tier3_effective20260101_returns880() {
        Optional<FeeSchedule> result = repository.lookupByTier("40", "3", LocalDate.of(2026, 1, 1));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().feeJpy()).isEqualTo(880L);
    }

    @Test
    void lookupByTier_cat40_tier3_effective20270101_returns968() {
        Optional<FeeSchedule> result = repository.lookupByTier("40", "3", LocalDate.of(2027, 1, 1));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().feeJpy()).isEqualTo(968L);
    }

    @Test
    void lookupByTier_cat99_notFound_returnsEmpty() {
        Optional<FeeSchedule> result = repository.lookupByTier("99", "1", LocalDate.of(2026, 1, 1));
        assertThat(result).isEmpty();
    }

    @Test
    void lookupByTier_cat40_tier99_notFound_returnsEmpty() {
        Optional<FeeSchedule> result = repository.lookupByTier("40", "99", LocalDate.of(2026, 1, 1));
        assertThat(result).isEmpty();
    }

    @Test
    void lookupByTier_effectiveDateBetweenRecords_returnsEarlier() {
        // 2026-06-01 は 2026-01-01 のレコードが有効 (2027-01-01 より前)
        Optional<FeeSchedule> result = repository.lookupByTier("40", "3", LocalDate.of(2026, 6, 1));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().feeJpy()).isEqualTo(880L);
    }

    // --- FEE-LOAD ---

    @Autowired
    private FeeLoadService loadService;

    @Test
    void load_insertsAndDetectsDuplicate() {
        FeeSchedule newFee = new FeeSchedule("40", "2", LocalDate.of(2026, 1, 1), 440L);
        FeeSchedule duplicate = new FeeSchedule("40", "1", LocalDate.of(2026, 1, 1), 999L);

        FeeLoadService.LoadResult result = loadService.load(List.of(newFee, duplicate));

        assertThat(result.loaded()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(repository.lookupByTier("40", "2", LocalDate.of(2026, 1, 1))).isPresent();
    }

    @Test
    void load_emptyList_returnsZeroCounts() {
        FeeLoadService.LoadResult result = loadService.load(List.of());

        assertThat(result.loaded()).isZero();
        assertThat(result.skipped()).isZero();
    }

    @Test
    void findAll_returnsAllSeedRows() {
        List<FeeSchedule> fees = repository.findAll();
        assertThat(fees).hasSize(12);
    }
}
