package com.practicebank.batch.integrationin;

import com.practicebank.common.test.PostgresTestContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * INTI-DECODE-BATCH context-only test.
 *
 * <p>Phase 2 スコープ: Spring Boot アプリコンテキストがロードされ、
 * {@code integrationInDecodeJob} bean が存在することを確認するのみ。
 * 実ジョブ起動 (フィクスチャ読み取り → decode → DB 書き出し) は
 * Testcontainers が必要なため Phase 2 後半で別タスクとして実施する。
 */
@SpringBootTest
@SpringBatchTest
@Sql(scripts = {"/sql/inti-schema.sql"},
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
     config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class IntiDecodeJobTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.getInstance().start();
    }

    @BeforeAll
    static void startContainer() {
        PostgresTestContainer.getInstance().start();
    }

    @Autowired
    private Job integrationInDecodeJob;

    @Test
    void contextLoads_andJobBeanPresent() {
        assertThat(integrationInDecodeJob).isNotNull();
        assertThat(integrationInDecodeJob.getName()).isEqualTo("integrationInDecodeJob");
    }
}
