package com.practicebank.batch.operations.masterload;

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

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * OPS-MASTER-LOAD 相当 — 7 個のマスターデータを別サブシステムのローダで投入する.
 * Phase 2 では shell/make 呼出は行わず, smoke + 監査出力のみ.
 *
 * <p>マスターロード順序 (ops-batch-daily.md 記載の依存順):</p>
 * <ol>
 *   <li>calendar   (01-calendar)</li>
 *   <li>branch     (02-branch)</li>
 *   <li>customer   (03-customer)</li>
 *   <li>product    (05-product)</li>
 *   <li>interestrate (06-interestrate)</li>
 *   <li>feeschedule (07-feeschedule)</li>
 *   <li>account    (08-account)</li>
 * </ol>
 */
@Configuration
public class MasterLoadConfig {

    private static final Logger LOG = LoggerFactory.getLogger(MasterLoadConfig.class);

    /** マスターロード一覧 (順序 = 実行業務依存順). */
    public record MasterDef(String stepName, String masterName, String subsysDir, String loaderBin) {}

    public static final List<MasterDef> MASTERS = List.of(
        new MasterDef("masterLoadCalendar",   "calendar",      "01-calendar",        "cal-load"),
        new MasterDef("masterLoadBranch",     "branch",        "02-branch",          "br-load"),
        new MasterDef("masterLoadCustomer",   "customer",      "03-customer",        "cust-load"),
        new MasterDef("masterLoadProduct",    "product",       "05-product",         "prod-load"),
        new MasterDef("masterLoadInterestRate","interestrate", "06-interestrate",    "irate-load"),
        new MasterDef("masterLoadFeeSchedule","feeschedule",   "07-feeschedule",     "fee-load"),
        new MasterDef("masterLoadAccount",    "account",       "08-account",         "acct-load")
    );

    private final JdbcTemplate jdbc;
    private final OpsAudit audit;

    public MasterLoadConfig(JdbcTemplate jdbc, OpsAudit audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    /** マスター 1 件分の smoke Tasklet. */
    private Tasklet loadTasklet(MasterDef master, String batchId, String businessDate, boolean dryRun) {
        final String name = master.masterName();
        return (StepContribution sc, ChunkContext cc) -> {
            audit.writeStepFromChunk(batchId, businessDate, OpsAudit.EVT_STEP_START, cc);
            if (dryRun) {
                LOG.info("OPS-MASTER-LOAD-DRYRUN master={} subsys={} bin={}",
                    name, master.subsysDir(), master.loaderBin());
            } else {
                // OPS-MASTER-LOAD.sh が make -C <subsys> load-idx を実行する部分.
                // Phase 2 では smoke. 将来 lib/subsystem-loader から接続.
                LOG.info("OPS-MASTER-LOAD-EXEC master={} subsys={} bin={} (smoke — real loader TBA)",
                    name, master.subsysDir(), master.loaderBin());
            }
            audit.writeStepFromChunk(batchId, businessDate, OpsAudit.EVT_STEP_OK, cc);
            LOG.info("OPS-MASTER-LOAD-OK master={}", name);
            return RepeatStatus.FINISHED;
        };
    }

    private Step buildMasterStep(MasterDef def, JobRepository jr, PlatformTransactionManager tx,
                                 String batchId, String businessDate, boolean dryRun) {
        return new StepBuilder(def.stepName(), jr)
            .tasklet(loadTasklet(def, batchId, businessDate, dryRun), tx)
            .build();
    }

    @Bean
    public Step masterLoadCalendar(JobRepository jr, PlatformTransactionManager tx,
                                   @Value("${ops.batch-id:}") String batchId,
                                   @Value("${ops.business-date:}") String businessDate,
                                   @Value("${ops.dry-run:N}") String dryRun) {
        return buildMasterStep(MASTERS.get(0), jr, tx, batchId, businessDate, "Y".equalsIgnoreCase(dryRun));
    }

    @Bean
    public Step masterLoadBranch(JobRepository jr, PlatformTransactionManager tx,
                                 @Value("${ops.batch-id:}") String batchId,
                                 @Value("${ops.business-date:}") String businessDate,
                                 @Value("${ops.dry-run:N}") String dryRun) {
        return buildMasterStep(MASTERS.get(1), jr, tx, batchId, businessDate, "Y".equalsIgnoreCase(dryRun));
    }

    @Bean
    public Step masterLoadCustomer(JobRepository jr, PlatformTransactionManager tx,
                                   @Value("${ops.batch-id:}") String batchId,
                                   @Value("${ops.business-date:}") String businessDate,
                                   @Value("${ops.dry-run:N}") String dryRun) {
        return buildMasterStep(MASTERS.get(2), jr, tx, batchId, businessDate, "Y".equalsIgnoreCase(dryRun));
    }

    @Bean
    public Step masterLoadProduct(JobRepository jr, PlatformTransactionManager tx,
                                  @Value("${ops.batch-id:}") String batchId,
                                  @Value("${ops.business-date:}") String businessDate,
                                  @Value("${ops.dry-run:N}") String dryRun) {
        return buildMasterStep(MASTERS.get(3), jr, tx, batchId, businessDate, "Y".equalsIgnoreCase(dryRun));
    }

    @Bean
    public Step masterLoadInterestRate(JobRepository jr, PlatformTransactionManager tx,
                                       @Value("${ops.batch-id:}") String batchId,
                                       @Value("${ops.business-date:}") String businessDate,
                                       @Value("${ops.dry-run:N}") String dryRun) {
        return buildMasterStep(MASTERS.get(4), jr, tx, batchId, businessDate, "Y".equalsIgnoreCase(dryRun));
    }

    @Bean
    public Step masterLoadFeeSchedule(JobRepository jr, PlatformTransactionManager tx,
                                      @Value("${ops.batch-id:}") String batchId,
                                      @Value("${ops.business-date:}") String businessDate,
                                      @Value("${ops.dry-run:N}") String dryRun) {
        return buildMasterStep(MASTERS.get(5), jr, tx, batchId, businessDate, "Y".equalsIgnoreCase(dryRun));
    }

    @Bean
    public Step masterLoadAccount(JobRepository jr, PlatformTransactionManager tx,
                                  @Value("${ops.batch-id:}") String batchId,
                                  @Value("${ops.business-date:}") String businessDate,
                                  @Value("${ops.dry-run:N}") String dryRun) {
        return buildMasterStep(MASTERS.get(6), jr, tx, batchId, businessDate, "Y".equalsIgnoreCase(dryRun));
    }
}
