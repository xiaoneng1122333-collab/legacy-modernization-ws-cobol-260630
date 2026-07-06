package com.practicebank.masters.interestrate;

import com.practicebank.common.test.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Sql(scripts = "/sql/interestrate.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class InterestRateRepositoryTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer container = PostgresTestContainer.getInstance();
        container.start();
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
    }

    @Autowired
    private InterestRateRepository repository;

    // --- IRATE-LOOKUP ---

    @Test
    void findByProductAndDate_existingProduct001_returnsRate() {
        Optional<InterestRate> result = repository.findByProductAndDate("001", LocalDate.of(2026, 1, 1));

        assertThat(result).isPresent();
        InterestRate r = result.orElseThrow();
        assertThat(r.productCode()).isEqualTo("001");
        assertThat(r.effectiveDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(r.annualRate()).isEqualByComparingTo(new BigDecimal("0.001000"));
        assertThat(r.tierThresholdJpy()).isEqualTo(0L);
    }

    @Test
    void findByProductAndDate_existingProduct002_returnsRate() {
        Optional<InterestRate> result = repository.findByProductAndDate("002", LocalDate.of(2027, 1, 1));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().annualRate()).isEqualByComparingTo(new BigDecimal("0.055000"));
    }

    @Test
    void findByProductAndDate_zeroRateProduct003_returnsZeroRate() {
        Optional<InterestRate> result = repository.findByProductAndDate("003", LocalDate.of(2026, 1, 1));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().annualRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void findByProductAndDate_nullTierThreshold_returnsNull() {
        Optional<InterestRate> result = repository.findByProductAndDate("003", LocalDate.of(2026, 1, 1));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().tierThresholdJpy()).isNull();
    }

    @Test
    void findByProductAndDate_notFound_returnsEmpty() {
        Optional<InterestRate> result = repository.findByProductAndDate("999", LocalDate.of(2026, 1, 1));
        assertThat(result).isEmpty();
    }

    @Test
    void findByProductAndDate_wrongEffectiveDate_returnsEmpty() {
        // product 001 has 2026-01-01 and 2027-01-01 but not 2025-01-01
        Optional<InterestRate> result = repository.findByProductAndDate("001", LocalDate.of(2025, 1, 1));
        assertThat(result).isEmpty();
    }

    // --- findAll ---

    @Test
    void findAll_returnsAllRowsOrdered() {
        List<InterestRate> rates = repository.findAll();

        assertThat(rates).hasSize(6);
        assertThat(rates.get(0).productCode()).isEqualTo("001");
        assertThat(rates.get(0).effectiveDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(rates.get(5).productCode()).isEqualTo("004");
    }

    // --- findByProductCode ---

    @Test
    void findByProductCode_001_returnsTwoRows() {
        List<InterestRate> rates = repository.findByProductCode("001");

        assertThat(rates).hasSize(2);
        assertThat(rates).extracting(InterestRate::effectiveDate)
                .containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
    }

    @Test
    void findByProductCode_unknown_returnsEmpty() {
        List<InterestRate> rates = repository.findByProductCode("999");
        assertThat(rates).isEmpty();
    }

    // --- IRATE-LOAD ---

    @Autowired
    private InterestRateLoadService loadService;

    @Test
    void load_insertsAndDetectsDuplicate() {
        InterestRate newRate = new InterestRate("005", LocalDate.of(2026, 1, 1),
                new BigDecimal("0.030000"), 0L);
        InterestRate duplicate = new InterestRate("001", LocalDate.of(2026, 1, 1),
                new BigDecimal("0.099999"), 0L);

        InterestRateLoadService.LoadResult result = loadService.load(
                List.of(newRate, duplicate));

        assertThat(result.loaded()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(repository.findByProductAndDate("005", LocalDate.of(2026, 1, 1))).isPresent();
    }
}
