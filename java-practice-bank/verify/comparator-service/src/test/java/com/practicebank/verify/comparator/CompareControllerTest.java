package com.practicebank.verify.comparator;

import com.practicebank.verify.comparator.service.ComparatorService;
import com.practicebank.common.test.PostgresTestContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = {"/sql/comparator-schema.sql", "/sql/comparator-fixture.sql"},
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
     config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class CompareControllerTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer container = PostgresTestContainer.getInstance();
        container.start();
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
        registry.add("spring.java.datasource.url", container::getJdbcUrl);
        registry.add("spring.java.datasource.username", container::getUsername);
        registry.add("spring.java.datasource.password", container::getPassword);
    }

    @BeforeAll
    static void startContainer() {
        PostgresTestContainer.getInstance().start();
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void compareEndpoint_returns200AndJsonDiff() throws Exception {
        mockMvc.perform(get("/api/compare/2026-07-06"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.businessDate").value("2026-07-06"))
            .andExpect(jsonPath("$.overallStatus").value("MATCH"))
            .andExpect(jsonPath("$.mismatchCount").value(0))
            .andExpect(jsonPath("$.tables").isArray())
            .andExpect(jsonPath("$.tables.length()").value(7));
    }
}
