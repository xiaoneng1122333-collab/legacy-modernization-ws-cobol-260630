package com.practicebank.batch.operations;

import com.practicebank.common.batch.BatchJobConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot エントリポイント — 22-operations サブシステム:
 * OPS-BATCH-DAILY 相当の日次パイプライン.
 *
 * <p>実行順 (COBOL の 6 ステップを Java Step にマッピング):</p>
 * <ol>
 *   <li>batch-run-start  — batch_run INSERT status=RN</li>
 *   <li>master-load (7 masters: calendar → branch → customer → product → interestrate → feeschedule → account)</li>
 *   <li>ops-step-19-inti  — 19-integrationin INTI-DECODE-BATCH</li>
 *   <li>ops-step-13-iacr  — 13-interestaccrual IACR-RUN-DAILY</li>
 *   <li>ops-step-15-ad    — 15-autodebit AD-RUN-DAILY</li>
 *   <li>ops-step-16-fee   — 16-fee FEE-CHARGE</li>
 *   <li>ops-step-17-stmt  — 17-statement STMT-GENERATE-BATCH</li>
 *   <li>ops-step-20-drain — 20-integrationout INTO-DRAIN-QUEUE</li>
 *   <li>ops-finalize       — トランザクションを SE (確定)</li>
 *   <li>batch-run-complete — batch_run UPDATE status=OK/FL</li>
 * </ol>
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.practicebank.common.batch", "com.practicebank.batch.operations"})
@Import(BatchJobConfig.class)
public class OperationsJobApplication {
    public static void main(String[] args) {
        SpringApplication.run(OperationsJobApplication.class, args);
    }
}
