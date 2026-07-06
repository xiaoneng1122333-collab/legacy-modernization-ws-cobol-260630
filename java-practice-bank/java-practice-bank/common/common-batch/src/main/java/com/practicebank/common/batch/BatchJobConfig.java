package com.practicebank.common.batch;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.context.annotation.Configuration;

/** Spring Batch の共通有効化アノテーション。各ジョブモジュールはこれを @Import する。 */
@Configuration
@EnableBatchProcessing
public class BatchJobConfig {
}
