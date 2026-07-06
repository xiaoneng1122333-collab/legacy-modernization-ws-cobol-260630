package com.practicebank.masters.branch;

import com.practicebank.common.test.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Sql(scripts = "/sql/branch.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class BranchRepositoryTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer container = PostgresTestContainer.getInstance();
        container.start();
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
    }

    @Autowired
    private BranchRepository repository;

    // --- BR-LOOKUP ---

    @Test
    void findByCode_existingHeadOffice_returnsBranch() {
        Optional<Branch> result = repository.findByCode("001");

        assertThat(result).isPresent();
        Branch b = result.orElseThrow();
        assertThat(b.branchCode()).isEqualTo("001");
        assertThat(b.branchName()).isEqualTo("東京本店");
        assertThat(b.branchNameKana()).isEqualTo("トウキョウホンテン");
        assertThat(b.branchType()).isEqualTo("H");
    }

    @Test
    void findByCode_existingBranch_returnsBranch() {
        Optional<Branch> result = repository.findByCode("005");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().branchNameKana()).isEqualTo("オオサカホンテン");
    }

    @Test
    void findByCode_lastCode_returnsBranch() {
        Optional<Branch> result = repository.findByCode("010");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().branchName()).isEqualTo("福岡本店");
    }

    @Test
    void findByCode_notFound_returnsEmpty() {
        Optional<Branch> result = repository.findByCode("999");
        assertThat(result).isEmpty();
    }

    // --- BR-LIST-ALL ---

    @Test
    void findAll_returnsAllRowsOrderedByCode() {
        List<Branch> branches = repository.findAll();

        assertThat(branches).hasSize(10);
        assertThat(branches.get(0).branchCode()).isEqualTo("001");
        assertThat(branches.get(9).branchCode()).isEqualTo("010");
    }

    // --- BR-LIST-BY-REGION (branch_type) ---

    @Test
    void findByBranchType_H_returnsHeadOffices() {
        List<Branch> branches = repository.findByBranchType("H");

        assertThat(branches).hasSize(4);
        assertThat(branches).extracting(Branch::branchCode)
                .containsExactly("001", "005", "008", "010");
    }

    @Test
    void findByBranchType_B_returnsBranches() {
        List<Branch> branches = repository.findByBranchType("B");
        assertThat(branches).hasSize(6);
    }

    @Test
    void findByBranchType_unknownType_returnsEmpty() {
        List<Branch> branches = repository.findByBranchType("X");
        assertThat(branches).isEmpty();
    }

    // --- BR-LOAD ---

    @Autowired
    private BranchLoadService loadService;

    @Test
    void load_insertsAndDetectsDuplicate() {
        Branch newBranch = new Branch("020", "横浜支店", "ヨコハマ", "B",
                "神奈川県横浜市西区", "045-0000-0020");
        Branch duplicate = new Branch("001", "東京本店Dup", "トウキョウホンテンDup", "H",
                "東京都千代田区丸の内1-1-1", "03-1111-0001");

        BranchLoadService.LoadResult result = loadService.load(
                List.of(newBranch, duplicate));

        assertThat(result.loaded()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(repository.findByCode("020")).isPresent();
    }
}
