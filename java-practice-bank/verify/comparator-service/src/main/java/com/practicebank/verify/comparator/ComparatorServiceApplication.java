package com.practicebank.verify.comparator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot エントリポイント — parallel verification サービス.
 * 日次の COBOL 出力 (schema=postgres) と Java 出力 (schema=javaschema / jdbc 別接続運用) を比較し,
 * JSON diff レポートを返却する REST API を提供する.
 *
 * <p>BatchAutoConfiguration は common-batch 依存経由で有効化されるが, comparator は Spring Batch を
 * 直接使わないため除外し, データソースは {@code DataSourceConfig} で 2 つ (COBOL/Java) 明示設定する.</p>
 */
@SpringBootApplication(exclude = {BatchAutoConfiguration.class})
@ComponentScan(basePackages = {"com.practicebank.verify.comparator"})
public class ComparatorServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ComparatorServiceApplication.class, args);
    }
}
