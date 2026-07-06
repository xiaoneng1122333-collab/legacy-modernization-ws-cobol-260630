package com.practicebank.batch.operations.seed;

import com.practicebank.batch.operations.OpsAudit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

/**
 * OPS-SEED-SYSTEM-ACCOUNTS 相当 — 初期システム顧客 (1) / 口座 (4) / 残高 (4) を UPSERT.
 *
 * <ul>
 *   <li>customers  : cust 0000000001</li>
 *   <li>accounts   : acct 0010010000001..4 (CASH/CLEARING/INTEREST-EXPENSE/FEE-REVENUE)</li>
 *   <li>balances   : 上記 4 口座の初期残高</li>
 * </ul>
 *
 * <p>冪等: ON CONFLICT DO NOTHING. 再実行でも件数は変わらない.</p>
 * ISAM 書き込み (ops-seed-system-isam) 相当は Phase 2 TBA.
 */
@Configuration
public class SeedSystemAccountsConfig {

    private static final Logger LOG = LoggerFactory.getLogger(SeedSystemAccountsConfig.class);

    private static final List<String> ACCT_IDS = List.of(
        "0010010000001", // CASH
        "0010010000002", // CLEARING
        "0010010000003", // INTEREST-EXPENSE
        "0010010000004"  // FEE-REVENUE
    );

    private final JdbcTemplate jdbc;
    private final OpsAudit audit;

    public SeedSystemAccountsConfig(JdbcTemplate jdbc, OpsAudit audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Bean
    public Step seedSystemAccounts(JobRepository jr, PlatformTransactionManager tx,
                                   @Value("${ops.batch-id:}") String batchId,
                                   @Value("${ops.business-date:}") String businessDate,
                                   @Value("${ops.dry-run:N}") String dryRun) {
        return new StepBuilder("seedSystemAccounts", jr)
            .tasklet(new Tasklet() {
                @Override
                public RepeatStatus execute(StepContribution sc, ChunkContext cc) {
                    audit.writeStepFromChunk(batchId, businessDate, OpsAudit.EVT_STEP_START, cc);
                    if ("Y".equalsIgnoreCase(dryRun)) {
                        LOG.info("OPS-SEED-SYSTEM-ACCOUNTS dry-run batch={}", batchId);
                        audit.writeStepFromChunk(batchId, businessDate, OpsAudit.EVT_STEP_OK, cc);
                        return RepeatStatus.FINISHED;
                    }

                    // SEED_SYSTEM_CUST (OPS-SEED-AUDIT 相当).
                    jdbc.update(
                        "INSERT INTO customers (cust_id, cust_name, cust_name_kana, cust_status, tier) " +
                        "VALUES ('0000000001', 'システム', 'システム', 'A', 'S') " +
                        "ON CONFLICT (cust_id) DO NOTHING");
                    LOG.info("OPS-SEED-SYSTEM-CUST applied");

                    // SEED_SYSTEM_ACCT × 4.
                    for (int i = 0; i < ACCT_IDS.size(); i++) {
                        String acct = ACCT_IDS.get(i);
                        jdbc.update(
                            "INSERT INTO accounts (account_number, account_name, status, product_code) " +
                            "VALUES (?, ?, 'AC', 'SYS') " +
                            "ON CONFLICT (account_number) DO NOTHING", acct, acct);
                        jdbc.update(
                            "INSERT INTO balances (account_number, balance_jpy) " +
                            "VALUES (?, 0) ON CONFLICT (account_number) DO NOTHING", acct);
                        LOG.info("OPS-SEED-SYSTEM-ACCT applied idx={} acct={}", i + 1, acct);
                    }

                    audit.writeStepFromChunk(batchId, businessDate, OpsAudit.EVT_STEP_OK, cc);
                    LOG.info("OPS-SEED-SYSTEM-ACCOUNTS done batch={}", batchId);
                    return RepeatStatus.FINISHED;
                }
            }, tx)
            .build();
    }
}
