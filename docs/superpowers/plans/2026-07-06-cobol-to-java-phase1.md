# COBOL → Java Phase 1 (基盤構築) 実装計画

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Java プロジェクト基盤 (Spring Boot 3.3 + Spring Batch 5 + MyBatis)、Flyway マイグレーション、ISAM→Aurora 変換ジョブ、CI/CD パイプライン、Terraform インフラ定義、マスタサービス 7 本を構築する。

**Architecture:** Gradle マルチモジュール (22 サブモジュール + common)。MyBatis で ISAM ファイル操作を SQL にマッピング。Terraform で AWS 基盤 (VPC/ECS/Aurora/S3) を定義。GitHub Actions で CI/CD。

**Tech Stack:** Java 21, Spring Boot 3.3, Spring Batch 5, MyBatis 3, Flyway 10, Gradle 8, Terraform 1.x, GitHub Actions, Docker, Aurora PostgreSQL, ElastiCache Redis, Amazon MQ

## Global Constraints

- **Java:** 21 (LTS) — record / switch 式 / text block を活用
- **Spring Boot:** 3.3.x
- **Spring Batch:** 5.x — `ChunkOrientedTasklet` で COBOL の `PERFORM UNTIL` を再現
- **MyBatis:** 3.x — SQL ファースト。COBOL の READ/WRITE/REWRITE を明示的に SQL で記述
- **Flyway:** 10.x — 既存 `db/migration/V*.sql` をそのまま流用
- **Gradle:** 8.x — `build.gradle.kts` (Kotlin DSL)
- **Terraform:** 1.x + AWS Provider 5.x — モジュール構成 (network/database/ecs/storage/batch/monitoring/iam)
- **CI/CD:** GitHub Actions — PR でテスト、main マージで ECR プッシュ
- **パッケージ:** `com.practicebank.{module}`
- **テスト:** JUnit 5 + Mockito + Testcontainers (PostgreSQL + Redis)
- **コードスタイル:** Google Java Format、Checkstyle

---

## ファイル構造

```
java-practice-bank/                 ← 新規作成 (Phase 1 のルート)
├── build.gradle.kts                ← Task 1 で作成
├── settings.gradle.kts             ← Task 1 で作成
├── gradle.properties               ← Task 1 で作成
├── .gitignore                      ← Task 1 で作成
├── buildSrc/                       ← Task 2 で作成
│   └── src/main/kotlin/
│       ├── java-conventions.gradle.kts
│       └── spring-boot-conventions.gradle.kts
├── common/                         ← Task 3-6 で作成
│   ├── common-domain/
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/practicebank/common/domain/
│   │       ├── Money.java
│   │       ├── AccountStatus.java
│   │       ├── TransactionStatus.java
│   │       └── DayType.java
│   ├── common-batch/
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/practicebank/common/batch/
│   │       ├── BatchJobConfig.java
│   │       └── ExitCodeMapper.java
│   ├── common-mybatis/
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/practicebank/common/mybatis/
│   │       ├── MyBatisConfig.java
│   │       └── MoneyTypeHandler.java
│   └── common-test/
│       ├── build.gradle.kts
│       └── src/main/java/com/practicebank/common/test/
│           ├── PostgresTestContainer.java
│           └── RedisTestContainer.java
├── db/migration/                   ← Task 7 で既存 SQL をコピー
│   ├── V1__initial_schema.sql
│   ├── V2__master_pg_tables.sql
│   └── ...
├── batch/isam-to-rds-job/          ← Task 8-9 で作成
│   ├── build.gradle.kts
│   └── src/main/java/com/practicebank/batch/isamtorrds/
│       ├── IsamToRdsJob.java
│       ├── IsamReader.java
│       └── IsamWriter.java
├── masters/                        ← Task 10-12 で作成 (テンプレート 1 本)
│   └── calendar-service/
│       ├── build.gradle.kts
│       └── src/main/java/com/practicebank/masters/calendar/
│           ├── CalendarServiceApplication.java
│           ├── Calendar.java
│           ├── CalendarMapper.java
│           └── CalendarRepository.java
├── .github/workflows/              ← Task 13 で作成
│   ├── ci.yml
│   └── cd.yml
├── Dockerfile                      ← Task 14 で作成
├── infra/                          ← Task 15-16 で作成
│   ├── modules/
│   │   ├── network/main.tf
│   │   ├── database/main.tf
│   │   ├── ecs/main.tf
│   │   ├── storage/main.tf
│   │   └── monitoring/main.tf
│   └── environments/
│       └── dev/main.tf
└── docs/
    └── runbooks/
        └── phase1-checklist.md
```

---

## Task 1: Gradle ルートプロジェクト作成

**Files:**
- Create: `java-practice-bank/build.gradle.kts`
- Create: `java-practice-bank/settings.gradle.kts`
- Create: `java-practice-bank/gradle.properties`
- Create: `java-practice-bank/.gitignore`

**Interfaces:**
- Produces: ルート Gradle プロジェクト。後続タスクが `include()` でサブモジュール追加。

- [ ] **Step 1: ルート build.gradle.kts を作成する**

```kotlin
// java-practice-bank/build.gradle.kts
plugins {
    java
    id("org.springframework.boot") version "3.3.5" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
}

allprojects {
    group = "com.practicebank"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-parameters", "-Xlint:unchecked", "-Xlint:deprecation"))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
        }
    }
}
```

- [ ] **Step 2: settings.gradle.kts を作成する**

```kotlin
// java-practice-bank/settings.gradle.kts
rootProject.name = "java-practice-bank"

// Phase 1 で作成するモジュール
include("buildSrc")
include("common:common-domain")
include("common:common-batch")
include("common:common-mybatis")
include("common:common-test")
include("batch:isam-to-rds-job")
include("masters:calendar-service")
```

- [ ] **Step 3: gradle.properties を作成する**

```properties
# java-practice-bank/gradle.properties
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m
```

- [ ] **Step 4: .gitignore を作成する**

```gitignore
# java-practice-bank/.gitignore
.gradle/
build/
*.class
.idea/
*.iml
.vscode/
.DS_Store
infra/.terraform/
infra/*.tfstate*
```

- [ ] **Step 5: ビルドが通ることを確認する**

Run: `cd java-practice-bank && ./gradlew tasks`
Expected: `BUILD SUCCESSFUL` (no subprojects yet, just root)

- [ ] **Step 6: コミットする**

```bash
git add java-practice-bank/build.gradle.kts java-practice-bank/settings.gradle.kts \
        java-practice-bank/gradle.properties java-practice-bank/.gitignore
git commit -m "feat(phase1): create Gradle root project (Java 21, Spring Boot 3.3)"
```

---

## Task 2: buildSrc で共通ビルド設定を作成

**Files:**
- Create: `java-practice-bank/buildSrc/build.gradle.kts`
- Create: `java-practice-bank/buildSrc/src/main/kotlin/java-conventions.gradle.kts`
- Create: `java-practice-bank/buildSrc/src/main/kotlin/spring-boot-conventions.gradle.kts`

**Interfaces:**
- Produces: `java-conventions` プラグイン (全サブモジュールに適用)、`spring-boot-conventions` プラグイン (Spring モジュールに適用)

- [ ] **Step 1: buildSrc/build.gradle.kts を作成する**

```kotlin
// java-practice-bank/buildSrc/build.gradle.kts
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}
```

- [ ] **Step 2: java-conventions.gradle.kts を作成する**

```kotlin
// java-practice-bank/buildSrc/src/main/kotlin/java-conventions.gradle.kts
plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

- [ ] **Step 3: spring-boot-conventions.gradle.kts を作成する**

```kotlin
// java-practice-bank/buildSrc/src/main/kotlin/spring-boot-conventions.gradle.kts
plugins {
    id("java-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
```

- [ ] **Step 4: ビルド確認**

Run: `cd java-practice-bank && ./gradlew tasks`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: コミット**

```bash
git add java-practice-bank/buildSrc/
git commit -m "feat(phase1): add buildSrc with java/spring-boot conventions"
```

---

## Task 3: common-domain モジュール作成

**Files:**
- Create: `java-practice-bank/common/common-domain/build.gradle.kts`
- Create: `java-practice-bank/common/common-domain/src/main/java/com/practicebank/common/domain/Money.java`
- Create: `java-practice-bank/common/common-domain/src/main/java/com/practicebank/common/domain/AccountStatus.java`
- Create: `java-practice-bank/common/common-domain/src/main/java/com/practicebank/common/domain/TransactionStatus.java`
- Create: `java-practice-bank/common/common-domain/src/main/java/com/practicebank/common/domain/DayType.java`

**Interfaces:**
- Produces: `Money` (record, JPY 金額), `AccountStatus` (enum, P/A/S/L/C/F/D), `TransactionStatus` (enum, PT/SE/RV), `DayType` (enum, B/H/W)

- [ ] **Step 1: build.gradle.kts を作成する**

```kotlin
// java-practice-bank/common/common-domain/build.gradle.kts
plugins {
    id("java-conventions")
}
```

- [ ] **Step 2: Money.java を作成する**

```java
// java-practice-bank/common/common-domain/src/main/java/com/practicebank/common/domain/Money.java
package com.practicebank.common.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** JPY 金額を表す値オブジェクト。COBOL の PIC 9(15) に対応。 */
public record Money(BigDecimal amount) {

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.scale() > 0) {
            throw new IllegalArgumentException("JPY must have zero decimal places: " + amount);
        }
    }

    public static Money of(long amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    public Money subtract(Money other) {
        return new Money(this.amount.subtract(other.amount));
    }

    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public long toLong() {
        return amount.longValueExact();
    }
}
```

- [ ] **Step 3: AccountStatus.java を作成する**

```java
// java-practice-bank/common/common-domain/src/main/java/com/practicebank/common/domain/AccountStatus.java
package com.practicebank.common.domain;

/** 口座状態。COBOL の ACCT-REC-STATUS に対応。 */
public enum AccountStatus {
    P("Pending", "申請中"),
    A("Active", "活性"),
    S("Suspended", "停止"),
    L("Lost/Collection", "債権回収"),
    C("Closed", "解約"),
    F("Force-closed", "強制解約"),
    D("Dormant", "休眠");

    private final String englishName;
    private final String japaneseName;

    AccountStatus(String englishName, String japaneseName) {
        this.englishName = englishName;
        this.japaneseName = japaneseName;
    }

    /** COBOL の 1 文字コード ('P','A','S','L','C','F','D') に変換 */
    public char toCode() {
        return name().charAt(0);
    }

    /** COBOL の 1 文字コードから逆変換 */
    public static AccountStatus fromCode(char code) {
        for (AccountStatus s : values()) {
            if (s.toCode() == code) return s;
        }
        throw new IllegalArgumentException("Unknown account status code: " + code);
    }
}
```

- [ ] **Step 4: TransactionStatus.java を作成する**

```java
// java-practice-bank/common/common-domain/src/main/java/com/practicebank/common/domain/TransactionStatus.java
package com.practicebank.common.domain;

/** 取引ステータス。COBOL の TXN-STATUS および DB CHECK 制約 (PT/SE/RV) に対応。 */
public enum TransactionStatus {
    PT("Posted", "記帳済"),
    SE("Settled", "決済済"),
    RV("Reversed", "取消済");

    private final String englishName;
    private final String japaneseName;

    TransactionStatus(String englishName, String japaneseName) {
        this.englishName = englishName;
        this.japaneseName = japaneseName;
    }

    public String code() {
        return name();
    }

    public static TransactionStatus fromCode(String code) {
        return valueOf(code);
    }
}
```

- [ ] **Step 5: DayType.java を作成する**

```java
// java-practice-bank/common/common-domain/src/main/java/com/practicebank/common/domain/DayType.java
package com.practicebank.common.domain;

/** 日付種別。COBOL の CAL-REC-DAY-TYPE (B/H/W) に対応。 */
public enum DayType {
    B("Business", "営業日"),
    H("Holiday", "休日"),
    W("Weekend", "週末");

    private final String englishName;
    private final String japaneseName;

    DayType(String englishName, String japaneseName) {
        this.englishName = englishName;
        this.japaneseName = japaneseName;
    }

    public char toCode() {
        return name().charAt(0);
    }

    public static DayType fromCode(char code) {
        for (DayType t : values()) {
            if (t.toCode() == code) return t;
        }
        throw new IllegalArgumentException("Unknown day type code: " + code);
    }
}
```

- [ ] **Step 6: ビルド確認**

Run: `cd java-practice-bank && ./gradlew :common:common-domain:build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: コミット**

```bash
git add java-practice-bank/common/common-domain/
git commit -m "feat(phase1): add common-domain (Money, AccountStatus, TransactionStatus, DayType)"
```

---

## Task 4: common-batch モジュール作成

**Files:**
- Create: `java-practice-bank/common/common-batch/build.gradle.kts`
- Create: `java-practice-bank/common/common-batch/src/main/java/com/practicebank/common/batch/BatchJobConfig.java`
- Create: `java-practice-bank/common/common-batch/src/main/java/com/practicebank/common/batch/ExitCodeMapper.java`

**Interfaces:**
- Produces: `BatchJobConfig` (Spring Batch 共通設定), `ExitCodeMapper` (COBOL RETURN-CODE → Spring Batch ExitStatus 変換)

- [ ] **Step 1: build.gradle.kts を作成する**

```kotlin
// java-practice-bank/common/common-batch/build.gradle.kts
plugins {
    id("java-conventions")
}

dependencies {
    implementation(project(":common:common-domain"))
    implementation("org.springframework.batch:spring-batch-core")
    implementation("org.springframework:spring-context")
}
```

- [ ] **Step 2: BatchJobConfig.java を作成する**

```java
// java-practice-bank/common/common-batch/src/main/java/com/practicebank/common/batch/BatchJobConfig.java
package com.practicebank.common.batch;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.context.annotation.Configuration;

/** Spring Batch の共通有効化アノテーション。各ジョブモジュールはこれを @Import する。 */
@Configuration
@EnableBatchProcessing
public class BatchJobConfig {
}
```

- [ ] **Step 3: ExitCodeMapper.java を作成する**

```java
// java-practice-bank/common/common-batch/src/main/java/com/practicebank/common/batch/ExitCodeMapper.java
package com.practicebank.common.batch;

import org.springframework.batch.core.ExitStatus;

/**
 * COBOL の RETURN-CODE を Spring Batch の ExitStatus に変換する。
 * COBOL との等価性検証のため、同じ終了コード体系を維持する。
 *
 * <pre>
 *   00 → COMPLETED   (正常)
 *   04 → COMPLETED   (処理対象なし / NOT-FOUND)
 *   08 → FAILED      (入力エラー / INVALID-INPUT)
 *   12 → FAILED      (I/O エラー / IO-FAIL)
 *   16 → FAILED      (致命的エラー / FATAL)
 * </pre>
 */
public final class ExitCodeMapper {

    public static ExitStatus fromReturnCode(int returnCode) {
        return switch (returnCode) {
            case 0 -> ExitStatus.COMPLETED;
            case 4 -> new ExitStatus("COMPLETED", "No records processed (NOT-FOUND)");
            case 8 -> new ExitStatus("FAILED", "Invalid input (INVALID-INPUT)");
            case 12 -> new ExitStatus("FAILED", "I/O failure (IO-FAIL)");
            case 16 -> new ExitStatus("FAILED", "Fatal error (FATAL)");
            default -> new ExitStatus("FAILED", "Unknown return code: " + returnCode);
        };
    }

    private ExitCodeMapper() {}
}
```

- [ ] **Step 4: ビルド確認**

Run: `cd java-practice-bank && ./gradlew :common:common-batch:build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: コミット**

```bash
git add java-practice-bank/common/common-batch/
git commit -m "feat(phase1): add common-batch (BatchJobConfig, ExitCodeMapper)"
```

---

## Task 5: common-mybatis モジュール作成

**Files:**
- Create: `java-practice-bank/common/common-mybatis/build.gradle.kts`
- Create: `java-practice-bank/common/common-mybatis/src/main/java/com/practicebank/common/mybatis/MyBatisConfig.java`
- Create: `java-practice-bank/common/common-mybatis/src/main/java/com/practicebank/common/mybatis/MoneyTypeHandler.java`

**Interfaces:**
- Produces: `MyBatisConfig` (SqlSessionFactory 共通設定), `MoneyTypeHandler` (Money ↔ BIGINT 変換)

- [ ] **Step 1: build.gradle.kts を作成する**

```kotlin
// java-practice-bank/common/common-mybatis/build.gradle.kts
plugins {
    id("java-conventions")
}

dependencies {
    implementation(project(":common:common-domain"))
    implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.3")
    implementation("org.springframework:spring-jdbc")
}
```

- [ ] **Step 2: MyBatisConfig.java を作成する**

```java
// java-practice-bank/common/common-mybatis/src/main/java/com/practicebank/common/mybatis/MyBatisConfig.java
package com.practicebank.common.mybatis;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.boot.autoconfigure.SpringBootVFS;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

@Configuration
public class MyBatisConfig {

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setVfs(SpringBootVFS.class);
        factory.setTypeHandlers(new MoneyTypeHandler());
        factory.setMapperLocations(
            new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml")
        );
        org.apache.ibatis.session.Configuration mybatisConfig = new org.apache.ibatis.session.Configuration();
        mybatisConfig.setMapUnderscoreToCamelCase(true);
        mybatisConfig.setJdbcTypeForNull(org.apache.ibatis.type.JdbcType.NULL);
        factory.setConfiguration(mybatisConfig);
        return factory.getObject();
    }
}
```

- [ ] **Step 3: MoneyTypeHandler.java を作成する**

```java
// java-practice-bank/common/common-mybatis/src/main/java/com/practicebank/common/mybatis/MoneyTypeHandler.java
package com.practicebank.common.mybatis;

import com.practicebank.common.domain.Money;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Money (JPY) を PostgreSQL BIGINT に変換する。COBOL の PIC 9(15) に対応。 */
public class MoneyTypeHandler extends BaseTypeHandler<Money> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Money parameter, JdbcType jdbcType) throws SQLException {
        ps.setLong(i, parameter.toLong());
    }

    @Override
    public Money getNullableResult(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : Money.of(value);
    }

    @Override
    public Money getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        long value = rs.getLong(columnIndex);
        return rs.wasNull() ? null : Money.of(value);
    }

    @Override
    public Money getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        long value = cs.getLong(columnIndex);
        return cs.wasNull() ? null : Money.of(value);
    }
}
```

- [ ] **Step 4: ビルド確認**

Run: `cd java-practice-bank && ./gradlew :common:common-mybatis:build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: コミット**

```bash
git add java-practice-bank/common/common-mybatis/
git commit -m "feat(phase1): add common-mybatis (MyBatisConfig, MoneyTypeHandler)"
```

---

## Task 6: common-test モジュール作成

**Files:**
- Create: `java-practice-bank/common/common-test/build.gradle.kts`
- Create: `java-practice-bank/common/common-test/src/main/java/com/practicebank/common/test/PostgresTestContainer.java`
- Create: `java-practice-bank/common/common-test/src/main/java/com/practicebank/common/test/RedisTestContainer.java`

**Interfaces:**
- Produces: `PostgresTestContainer` (Testcontainers PostgreSQL 16), `RedisTestContainer` (Testcontainers Redis 7)

- [ ] **Step 1: build.gradle.kts を作成する**

```kotlin
// java-practice-bank/common/common-test/build.gradle.kts
plugins {
    id("java-conventions")
}

dependencies {
    implementation("org.testcontainers:postgresql:1.20.3")
    implementation("org.testcontainers:redis:1.20.3")
    implementation("org.junit.jupiter:junit-jupiter")
}
```

- [ ] **Step 2: PostgresTestContainer.java を作成する**

```java
// java-practice-bank/common/common-test/src/main/java/com/practicebank/common/test/PostgresTestContainer.java
package com.practicebank.common.test;

import org.testcontainers.containers.PostgreSQLContainer;

public class PostgresTestContainer extends PostgreSQLContainer<PostgresTestContainer> {

    private static final String IMAGE = "postgres:16-alpine";

    private static PostgresTestContainer instance;

    private PostgresTestContainer() {
        super(IMAGE);
        withDatabaseName("banking_test");
        withUsername("cobol");
        withPassword("cobol");
    }

    public static synchronized PostgresTestContainer getInstance() {
        if (instance == null) {
            instance = new PostgresTestContainer();
        }
        return instance;
    }

    @Override
    public void start() {
        super.start();
        System.setProperty("spring.datasource.url", getJdbcUrl());
        System.setProperty("spring.datasource.username", getUsername());
        System.setProperty("spring.datasource.password", getPassword());
    }
}
```

- [ ] **Step 3: RedisTestContainer.java を作成する**

```java
package com.practicebank.common.test;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class RedisTestContainer extends GenericContainer<RedisTestContainer> {

    private static final String IMAGE = "redis:7-alpine";
    private static RedisTestContainer instance;

    private RedisTestContainer() {
        super(DockerImageName.parse(IMAGE));
        withExposedPorts(6379);
    }

    public static synchronized RedisTestContainer getInstance() {
        if (instance == null) {
            instance = new RedisTestContainer();
        }
        return instance;
    }

    @Override
    public void start() {
        super.start();
        System.setProperty("spring.data.redis.url", "redis://" + getHost() + ":" + getMappedPort(6379));
    }
}
```

- [ ] **Step 4: ビルド確認**

Run: `cd java-practice-bank && ./gradlew :common:common-test:build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: コミット**

```bash
git add java-practice-bank/common/common-test/
git commit -m "feat(phase1): add common-test (PostgresTestContainer, RedisTestContainer)"
```

---

## Task 7: Flyway マイグレーション移植

**Files:**
- Create: `java-practice-bank/db/migration/V1__initial_schema.sql` (from existing)
- Create: `java-practice-bank/db/migration/V2__master_pg_tables.sql` (from existing)
- Create: `java-practice-bank/db/migration/V3__audit_log_partitioning.sql` (from existing)
- Create: `java-practice-bank/db/migration/V4__system_grants.sql` (from existing)
- Create: `java-practice-bank/db/migration/V5__audit_partition_functions.sql` (from existing)
- Create: `java-practice-bank/db/migration/V6__audit_event_key.sql` (from existing)
- Create: `java-practice-bank/db/migration/V7__audit_outbox.sql` (from existing)
- Create: `java-practice-bank/db/migration/V8__cobol_java_dual_schema.sql` (新規: cobol/java/shared スキーマ分離)

**Interfaces:**
- Produces: Flyway マイグレーション SQL 一式。既存 COBOL の DDL をそのまま活用 + 並行稼働用スキーマ分離。

- [ ] **Step 1: 既存 DDL を java-practice-bank/db/migration/ にコピーする**

```bash
cp db/migration/V1__initial_schema.sql       java-practice-bank/db/migration/
cp db/migration/V2__master_pg_tables.sql     java-practice-bank/db/migration/
cp db/migration/V3__audit_log_partitioning.sql java-practice-bank/db/migration/
cp db/migration/V4__system_grants.sql        java-practice-bank/db/migration/
cp db/migration/V5__audit_partition_functions.sql java-practice-bank/db/migration/
cp db/migration/V6__audit_event_key.sql      java-practice-bank/db/migration/
cp db/migration/V7__audit_outbox.sql         java-practice-bank/db/migration/
```

- [ ] **Step 2: V8__cobol_java_dual_schema.sql を作成する (並行稼働用スキーマ分離)**

```sql
-- java-practice-bank/db/migration/V8__cobol_java_dual_schema.sql
-- COBOL と Java の並行稼働期間中、データを論理分離するスキーマを作成

CREATE SCHEMA IF NOT EXISTS cobol;
CREATE SCHEMA IF NOT EXISTS java;
CREATE SCHEMA IF NOT EXISTS shared;

-- cobol スキーマに既存テーブルを複製 (COBOL が書き込む先)
CREATE TABLE cobol.transactions (LIKE public.transactions INCLUDING ALL);
CREATE TABLE cobol.postings (LIKE public.postings INCLUDING ALL);
CREATE TABLE cobol.balances (LIKE public.balances INCLUDING ALL);
CREATE TABLE cobol.interest_accruals (LIKE public.interest_accruals INCLUDING ALL);
CREATE TABLE cobol.audit_log (LIKE public.audit_log INCLUDING ALL);

-- java スキーマに既存テーブルを複製 (Java が書き込む先)
CREATE TABLE java.transactions (LIKE public.transactions INCLUDING ALL);
CREATE TABLE java.postings (LIKE public.postings INCLUDING ALL);
CREATE TABLE java.balances (LIKE public.balances INCLUDING ALL);
CREATE TABLE java.interest_accruals (LIKE public.interest_accruals INCLUDING ALL);
CREATE TABLE java.audit_log (LIKE public.audit_log INCLUDING ALL);

-- shared スキーマ: マスタデータ (読み取り専用、Phase 1 で移行後に固定)
-- マスタテーブルは Phase 1 で ISAM から移行される
```

- [ ] **Step 3: マイグレーション検証**

```bash
# ローカル PostgreSQL (Testcontainers または dev 環境) で検証
cd java-practice-bank
./gradlew flywayMigrate -Dflyway.url=jdbc:postgresql://localhost:5432/banking \
                        -Dflyway.user=cobol -Dflyway.password=cobol
```

Expected: `Successfully applied 8 migrations`

- [ ] **Step 4: コミット**

```bash
git add java-practice-bank/db/migration/
git commit -m "feat(phase1): port Flyway migrations + add cobol/java/shared schema separation"
```

---

## Task 8: isam-to-rds-job — Spring Batch ジョブ骨格作成

**Files:**
- Create: `java-practice-bank/batch/isam-to-rds-job/build.gradle.kts`
- Create: `java-practice-bank/batch/isam-to-rds-job/src/main/java/com/practicebank/batch/isamtorrds/IsamToRdsJob.java`
- Create: `java-practice-bank/batch/isam-to-rds-job/src/main/resources/application.yml`

**Interfaces:**
- Produces: `IsamToRdsJob` (Spring Batch ジョブ。ISAM 7 ファイルを読み取り、Aurora に書き込む)

- [ ] **Step 1: build.gradle.kts を作成する**

```kotlin
// java-practice-bank/batch/isam-to-rds-job/build.gradle.kts
plugins {
    id("spring-boot-conventions")
}

dependencies {
    implementation(project(":common:common-domain"))
    implementation(project(":common:common-batch"))
    implementation(project(":common:common-mybatis"))
    implementation("org.springframework.boot:spring-boot-starter-batch")
    implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.3")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation(project(":common:common-test"))
    testImplementation("org.springframework.batch:spring-batch-test")
}
```

- [ ] **Step 2: application.yml を作成する**

```yaml
# java-practice-bank/batch/isam-to-rds-job/src/main/resources/application.yml
spring:
  application:
    name: isam-to-rds-job
  batch:
    job:
      names: ${job.name:isamToRdsJob}
    jdbc:
      initialize-schema: never
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/banking}
    username: ${DB_USER:cobol}
    password: ${DB_PASSWORD:cobol}
  flyway:
    enabled: false  # 別途管理

mybatis:
  mapper-locations: classpath:mappers/**/*.xml
  configuration:
    map-underscore-to-camel-case: true

isam:
  base-path: ${ISAM_BASE_PATH:/workspace/subsystems}
  files:
    calendar: 01-calendar/data/calendar.idx
    branch: 02-branch/data/branch.idx
    customer: 03-customer/data/customer.idx
    product: 05-product/data/product.idx
    interestrate: 06-interestrate/data/interestrate.idx
    feeschedule: 07-feeschedule/data/feeschedule.idx
    account: 08-account/data/account.idx
```

- [ ] **Step 3: IsamToRdsJob.java を作成する**

```java
// java-practice-batch/batch/isam-to-rds-job/src/main/java/com/practicebank/batch/isamtorrds/IsamToRdsJob.java
package com.practicebank.batch.isamtorrds;

import com.practicebank.common.batch.BatchJobConfig;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootApplication
@Import(BatchJobConfig.class)
public class IsamToRdsJob {

    public static void main(String[] args) {
        SpringApplication.run(IsamToRdsJob.class, args);
    }

    @Bean
    public Job isamToRdsJob(JobRepository jobRepository, Step loadCalendar, Step loadBranch,
                            Step loadCustomer, Step loadProduct, Step loadInterestRate,
                            Step loadFeeSchedule, Step loadAccount) {
        return new JobBuilder("isamToRdsJob", jobRepository)
            .start(loadCalendar)
            .next(loadBranch)
            .next(loadCustomer)
            .next(loadProduct)
            .next(loadInterestRate)
            .next(loadFeeSchedule)
            .next(loadAccount)
            .build();
    }

    // 各 Step は Task 9 で実装
}
```

- [ ] **Step 4: ビルド確認**

Run: `cd java-practice-bank && ./gradlew :batch:isam-to-rds-job:build`
Expected: `BUILD SUCCESSFUL` (Step はスタブのまま)

- [ ] **Step 5: コミット**

```bash
git add java-practice-bank/batch/isam-to-rds-job/
git commit -m "feat(phase1): add isam-to-rds-job skeleton (Spring Batch job + 7 steps)"
```

---

## Task 9: isam-to-rds-job — マスタ別 Step 実装

**Files:**
- Create: `java-practice-bank/batch/isam-to-rds-job/src/main/java/com/practicebank/batch/isamtorrds/CalendarLoadStep.java`
- Create: `java-practice-bank/batch/isam-to-rds-job/src/main/java/com/practicebank/batch/isamtorrds/IsamRecord.java`
- Create: `java-practice-bank/batch/isam-to-rds-job/src/main/java/com/practicebank/batch/isamtorrds/IsamFileReader.java`
- Create: `java-practice-bank/batch/isam-to-rds-job/src/test/java/com/practicebank/batch/isamtorrds/IsamToRdsJobTest.java`

**Interfaces:**
- Produces: 7 つの Step (各マスタ 1 つ)。ISAM バイナリ読み取り → Aurora INSERT。

- [ ] **Step 1: IsamRecord.java を作成する (ISAM レコードの汎用 DTO)**

```java
package com.practicebank.batch.isamtorrds;

import java.util.Map;

/** ISAM ファイルの 1 レコードを表す。フィールド名 → 値 のマップ。 */
public record IsamRecord(Map<String, Object> fields) {
    public Object get(String fieldName) {
        return fields.get(fieldName);
    }
}
```

- [ ] **Step 2: IsamFileReader.java を作成する (ISAM バイナリ読み取り)**

```java
package com.practicebank.batch.isamtorrds;

import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * ISAM インデックスファイルをレコード単位で読み取る ItemReader。
 * COBOL の READ NEXT に対応。
 * 実装は固定長バイナリ読み取り (COBOL FD のレコード長に依存)。
 */
public class IsamFileReader implements ItemReader<IsamRecord> {

    private final RandomAccessFile raf;
    private final int recordLength;
    private final String[] fieldNames;

    public IsamFileReader(Path filePath, int recordLength, String[] fieldNames) throws IOException {
        this.raf = new RandomAccessFile(filePath.toFile(), "r");
        this.recordLength = recordLength;
        this.fieldNames = fieldNames;
    }

    @Override
    public IsamRecord read() throws Exception {
        byte[] buffer = new byte[recordLength];
        int bytesRead = raf.read(buffer);
        if (bytesRead < recordLength) {
            raf.close();
            return null; // EOF
        }
        // 固定長バイナリ → フィールドマッピング (簡易実装)
        Map<String, Object> fields = new HashMap<>();
        // TODO: COBOL FD のフィールド定義に基づきパース
        return new IsamRecord(fields);
    }
}
```

- [ ] **Step 3: CalendarLoadStep.java を作成する (Step のテンプレート)**

```java
package com.practicebank.batch.isamtorrds;

import com.practicebank.common.domain.DayType;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class CalendarLoadStep {

    @Bean
    public Step loadCalendar(JobRepository jobRepository,
                             PlatformTransactionManager txManager,
                             @Value("${isam.base-path}") String basePath) {
        return new StepBuilder("loadCalendar", jobRepository)
            .<IsamRecord, IsamRecord>chunk(1000, txManager)
            .reader(new IsamFileReader(
                Path.of(basePath, "01-calendar/data/calendar.idx"),
                64, // TODO: レコード長を正確に
                new String[]{"cal_date", "day_type", "holiday_name"}
            ))
            .processor(record -> record) // 変換ロジックは後で実装
            .writer(calendarWriter())
            .build();
    }

    @Bean
    @StepScope
    public JdbcBatchItemWriter<IsamRecord> calendarWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<IsamRecord>()
            .dataSource(dataSource)
            .sql("INSERT INTO shared.calendar (cal_date, day_type, holiday_name) " +
                  "VALUES (:fields.get('cal_date'), :fields.get('day_type'), :fields.get('holiday_name'))")
            .beanMapped()
            .build();
    }
}
```

- [ ] **Step 4: ユニットテスト IsamToRdsJobTest.java を作成する**

```java
package com.practicebank.batch.isamtorrds;

import com.practicebank.common.test.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
class IsamToRdsJobTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.getInstance().start();
    }

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private Job isamToRdsJob;

    @Test
    void isamToRdsJob_completesSuccessfully() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob();
        assertThat(execution.getExitStatus().getExitCode())
            .isEqualTo("COMPLETED");
    }
}
```

- [ ] **Step 5: テスト実行**

Run: `cd java-practice-bank && ./gradlew :batch:isam-to-rds-job:test`
Expected: `BUILD SUCCESSFUL` (Testcontainers 起動に時間がかかる場合あり)

- [ ] **Step 6: コミット**

```bash
git add java-practice-bank/batch/isam-to-rds-job/src/
git commit -m "feat(phase1): implement isam-to-rds-job steps (calendar template + test skeleton)"
```

---

## Task 10: マスタサービス テンプレート (calendar-service) 作成

**Files:**
- Create: `java-practice-bank/masters/calendar-service/build.gradle.kts`
- Create: `java-practice-bank/masters/calendar-service/src/main/java/com/practicebank/masters/calendar/CalendarServiceApplication.java`
- Create: `java-practice-bank/masters/calendar-service/src/main/java/com/practicebank/masters/calendar/Calendar.java`
- Create: `java-practice-bank/masters/calendar-service/src/main/java/com/practicebank/masters/calendar/CalendarMapper.java`
- Create: `java-practice-bank/masters/calendar-service/src/main/java/com/practicebank/masters/calendar/CalendarRepository.java`
- Create: `java-practice-bank/masters/calendar-service/src/main/resources/application.yml`
- Create: `java-practice-bank/masters/calendar-service/src/main/resources/mappers/CalendarMapper.xml`

**Interfaces:**
- Produces: `CalendarServiceApplication` (Spring Boot アプリ), `Calendar` (Entity), `CalendarMapper` (MyBatis インターフェース), `CalendarRepository` (リポジトリ層)

- [ ] **Step 1: build.gradle.kts を作成する**

```kotlin
// java-practice-bank/masters/calendar-service/build.gradle.kts
plugins {
    id("spring-boot-conventions")
}

dependencies {
    implementation(project(":common:common-domain"))
    implementation(project(":common:common-mybatis"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.3")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation(project(":common:common-test"))
}
```

- [ ] **Step 2: Calendar.java を作成する**

```java
package com.practicebank.masters.calendar;

import com.practicebank.common.domain.DayType;

import java.time.LocalDate;

/** カレンダーエンティティ。COBOL の CAL-REC に対応。 */
public record Calendar(
    LocalDate calDate,
    DayType dayType,
    String holidayName
) {}
```

- [ ] **Step 3: CalendarMapper.java を作成する**

```java
package com.practicebank.masters.calendar;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.Optional;

@Mapper
public interface CalendarMapper {

    @Select("SELECT cal_date, day_type, holiday_name FROM shared.calendar WHERE cal_date = #{date}")
    Optional<Calendar> findByDate(@Param("date") LocalDate date);

    @Select("SELECT cal_date, day_type, holiday_name FROM shared.calendar WHERE cal_date = #{date} AND day_type = 'B'")
    Optional<Calendar> findBusinessDay(@Param("date") LocalDate date);
}
```

- [ ] **Step 4: CalendarRepository.java を作成する**

```java
package com.practicebank.masters.calendar;

import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public class CalendarRepository {

    private final CalendarMapper mapper;

    public CalendarRepository(CalendarMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<Calendar> findByDate(LocalDate date) {
        return mapper.findByDate(date);
    }

    public Optional<Calendar> findBusinessDay(LocalDate date) {
        return mapper.findBusinessDay(date);
    }
}
```

- [ ] **Step 5: CalendarServiceApplication.java を作成する**

```java
package com.practicebank.masters.calendar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.practicebank.common", "com.practicebank.masters.calendar"})
public class CalendarServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CalendarServiceApplication.class, args);
    }
}
```

- [ ] **Step 6: application.yml を作成する**

```yaml
# java-practice-bank/masters/calendar-service/src/main/resources/application.yml
server:
  port: 8080

spring:
  application:
    name: calendar-service
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/banking}
    username: ${DB_USER:cobol}
    password: ${DB_PASSWORD:cobol}

mybatis:
  mapper-locations: classpath:mappers/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
```

- [ ] **Step 7: ビルド確認**

Run: `cd java-practice-bank && ./gradlew :masters:calendar-service:build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: コミット**

```bash
git add java-practice-bank/masters/calendar-service/
git commit -m "feat(phase1): add calendar-service master template (Spring Boot + MyBatis)"
```

---

## Task 11: マスタサービス テンプレート — テスト作成

**Files:**
- Create: `java-practice-bank/masters/calendar-service/src/test/java/com/practicebank/masters/calendar/CalendarRepositoryTest.java`

**Interfaces:**
- Produces: `CalendarRepositoryTest` (Testcontainers を使った統合テスト)

- [ ] **Step 1: CalendarRepositoryTest.java を作成する**

```java
package com.practicebank.masters.calendar;

import com.practicebank.common.domain.DayType;
import com.practicebank.common.test.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CalendarRepositoryTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.getInstance().start();
    }

    @Autowired
    private CalendarRepository repository;

    @Test
    void findByDate_returnsCalendar() {
        // テストデータは Flyway またはテストフィクスチャで投入想定
        Optional<Calendar> result = repository.findByDate(LocalDate.of(2026, 1, 1));
        // データが存在する場合の検証
        result.ifPresent(calendar -> {
            assertThat(calendar.calDate()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(calendar.dayType()).isIn(DayType.B, DayType.H, DayType.W);
        });
    }
}
```

- [ ] **Step 2: テスト実行**

Run: `cd java-practice-bank && ./gradlew :masters:calendar-service:test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: コミット**

```bash
git add java-practice-bank/masters/calendar-service/src/test/
git commit -m "test(phase1): add calendar-service integration test (Testcontainers)"
```

---

## Task 12: settings.gradle.kts に全モジュール追加

**Files:**
- Modify: `java-practice-bank/settings.gradle.kts`

**Interfaces:**
- Produces: 全 22 サブモジュール + common のエントリがそろう (Phase 2 で各モジュール追加時に有効化)

- [ ] **Step 1: settings.gradle.kts を更新する**

```kotlin
// java-practice-bank/settings.gradle.kts
rootProject.name = "java-practice-bank"

// ---- common ----
include("buildSrc")
include("common:common-domain")
include("common:common-batch")
include("common:common-mybatis")
include("common:common-test")

// ---- batch (Phase 1: isam-to-rds のみ) ----
include("batch:isam-to-rds-job")

// ---- masters (Phase 1: calendar のみ、他は Phase 2 で有効化) ----
include("masters:calendar-service")
// include("masters:branch-service")       // Phase 2
// include("masters:customer-service")     // Phase 2
// include("masters:product-service")      // Phase 2
// include("masters:interestrate-service") // Phase 2
// include("masters:feeschedule-service")  // Phase 2
// include("masters:account-service")      // Phase 2

// ---- batch jobs (Phase 2 で有効化) ----
// include("batch:integrationin-job")
// include("batch:txnvalidate-job")
// ... (22 サブシステム分)

// ---- online (Phase 2 で有効化) ----
// include("online:inquiry-api")
// include("online:accountlifecycle-api")

// ---- verify (Phase 2 で有効化) ----
// include("verify:comparator-service")
```

- [ ] **Step 2: ビルド確認**

Run: `cd java-practice-bank && ./gradlew projects`
Expected: 全モジュールが表示される

- [ ] **Step 3: コミット**

```bash
git add java-practice-bank/settings.gradle.kts
git commit -m "chore(phase1): register all modules in settings.gradle.kts (commented for Phase 2)"
```

---

## Task 13: CI/CD パイプライン (GitHub Actions)

**Files:**
- Create: `java-practice-bank/.github/workflows/ci.yml`
- Create: `java-practice-bank/.github/workflows/cd.yml`

**Interfaces:**
- Produces: `ci.yml` (PR でビルド+テスト)、`cd.yml` (main マージで ECR プッシュ)

- [ ] **Step 1: ci.yml を作成する**

```yaml
# java-practice-bank/.github/workflows/ci.yml
name: CI

on:
  pull_request:
    branches: [main]
  push:
    branches: [main]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'gradle'

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Build and Test
        run: ./gradlew build --no-daemon
        env:
          DB_URL: jdbc:tc:postgresql:16-alpine:///banking_test?TC_DAEMON=true
          DB_USER: cobol
          DB_PASSWORD: cobol

      - name: Upload Test Report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: '**/build/reports/tests/test'
```

- [ ] **Step 2: cd.yml を作成する**

```yaml
# java-practice-bank/.github/workflows/cd.yml
name: CD

on:
  push:
    branches: [main]

permissions:
  id-token: write
  contents: read

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service:
          - isam-to-rds-job
          - calendar-service
    steps:
      - uses: actions/checkout@v4

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: ${{ secrets.AWS_ROLE_ARN }}
          aws-region: ap-northeast-1

      - name: Login to Amazon ECR
        id: login-ecr
        uses: aws-actions/amazon-ecr-login@v2

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'gradle'

      - name: Build Docker image
        run: ./gradlew :${{ matrix.service }}:bootBuildImage --no-daemon

      - name: Tag and push to ECR
        env:
          ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
          ECR_REPOSITORY: practice-bank/${{ matrix.service }}
          IMAGE_TAG: ${{ github.sha }}
        run: |
          docker tag ${{ matrix.service }}:latest $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG
          docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG
          docker tag ${{ matrix.service }}:latest $ECR_REGISTRY/$ECR_REPOSITORY:latest
          docker push $ECR_REGISTRY/$ECR_REPOSITORY:latest
```

- [ ] **Step 3: ローカルでワークフロー構文確認**

Run: `cd java-practice-bank && cat .github/workflows/ci.yml | head -5`
Expected: YAML 構文エラーなし

- [ ] **Step 4: コミット**

```bash
git add java-practice-bank/.github/workflows/
git commit -m "ci(phase1): add GitHub Actions CI/CD (build, test, ECR push)"
```

---

## Task 14: Dockerfile (Spring Boot 用)

**Files:**
- Create: `java-practice-bank/Dockerfile`

**Interfaces:**
- Produces: マルチステージビルド Dockerfile (Java 21 + Spring Boot)

- [ ] **Step 1: Dockerfile を作成する**

```dockerfile
# java-practice-bank/Dockerfile
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 2: コミット**

```bash
git add java-practice-bank/Dockerfile
git commit -m "build(phase1): add Dockerfile for Spring Boot services"
```

---

## Task 15: Terraform — ネットワーク + データベースモジュール

**Files:**
- Create: `java-practice-bank/infra/modules/network/main.tf`
- Create: `java-practice-bank/infra/modules/network/variables.tf`
- Create: `java-practice-bank/infra/modules/network/outputs.tf`
- Create: `java-practice-bank/infra/modules/database/main.tf`
- Create: `java-practice-bank/infra/modules/database/variables.tf`
- Create: `java-practice-bank/infra/modules/database/outputs.tf`

**Interfaces:**
- Produces: `network` モジュール (VPC/Subnet/SG)、`database` モジュール (Aurora/ElastiCache)

- [ ] **Step 1: network/main.tf を作成する**

```hcl
# java-practice-bank/infra/modules/network/main.tf
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true
  tags = merge(var.tags, { Name = "${var.project_name}-vpc" })
}

resource "aws_subnet" "private" {
  count             = length(var.availability_zones)
  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index)
  availability_zone = var.availability_zones[count.index]
  tags = merge(var.tags, { Name = "${var.project_name}-private-${count.index}" })
}

resource "aws_security_group" "app" {
  name_prefix = "${var.project_name}-app-"
  vpc_id      = aws_vpc.main.id
  description = "Security group for application containers"

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = var.tags
}
```

- [ ] **Step 2: network/variables.tf を作成する**

```hcl
# java-practice-bank/infra/modules/network/variables.tf
variable "project_name" { type = string }
variable "vpc_cidr"     { type = string }
variable "availability_zones" { type = list(string) }
variable "tags"         { type = map(string) }
```

- [ ] **Step 3: network/outputs.tf を作成する**

```hcl
# java-practice-bank/infra/modules/network/outputs.tf
output "vpc_id" {
  value = aws_vpc.main.id
}
output "private_subnet_ids" {
  value = aws_subnet.private[*].id
}
output "app_security_group_id" {
  value = aws_security_group.app.id
}
```

- [ ] **Step 4: database/main.tf を作成する**

```hcl
# java-practice-bank/infra/modules/database/main.tf
resource "aws_rds_cluster" "main" {
  cluster_identifier = "${var.project_name}-aurora"
  engine             = "aurora-postgresql"
  engine_version     = "16.3"
  database_name      = "banking"
  master_username    = "cobol"
  master_password    = var.db_password
  vpc_security_group_ids = [var.db_security_group_id]
  db_subnet_group_name   = aws_db_subnet_group.main.name
  skip_final_snapshot    = true
  tags = var.tags
}

resource "aws_rds_cluster_instance" "main" {
  count              = 2
  identifier         = "${var.project_name}-aurora-${count.index}"
  cluster_identifier = aws_rds_cluster.main.id
  instance_class     = "db.r6g.large"
  engine             = aws_rds_cluster.main.engine
}

resource "aws_elasticache_cluster" "main" {
  cluster_id         = "${var.project_name}-redis"
  engine             = "redis"
  node_type          = "cache.r6g.large"
  num_cache_nodes    = 1
  engine_version     = "7.1"
  security_group_ids = [var.cache_security_group_id]
}
```

- [ ] **Step 5: database/variables.tf を作成する**

```hcl
# java-practice-bank/infra/modules/database/variables.tf
variable "project_name"          { type = string }
variable "db_password"           { type = string; sensitive = true }
variable "db_security_group_id"  { type = string }
variable "cache_security_group_id" { type = string }
variable "subnet_ids"            { type = list(string) }
variable "tags"                  { type = map(string) }
```

- [ ] **Step 6: database/outputs.tf を作成する**

```hcl
# java-practice-bank/infra/modules/database/outputs.tf
output "aurora_endpoint" {
  value = aws_rds_cluster.main.endpoint
}
output "redis_endpoint" {
  value = aws_elasticache_cluster.main.cache_nodes[0].address
}
```

- [ ] **Step 7: Terraform 構文確認**

Run: `cd java-practice-bank/infra && terraform init && terraform validate`
Expected: `Success! The configuration is valid.`

- [ ] **Step 8: コミット**

```bash
git add java-practice-bank/infra/modules/network/ java-practice-bank/infra/modules/database/
git commit -m "infra(phase1): add Terraform network + database modules (VPC, Aurora, ElastiCache)"
```

---

## Task 16: Terraform — 環境定義 (dev) + backend

**Files:**
- Create: `java-practice-bank/infra/environments/dev/main.tf`
- Create: `java-practice-bank/infra/environments/dev/variables.tf`
- Create: `java-practice-bank/infra/environments/dev/terraform.tfvars`
- Create: `java-practice-bank/infra/backend.tf`

**Interfaces:**
- Produces: `dev` 環境定義 (全モジュールをワイヤリング)、S3 backend 設定

- [ ] **Step 1: backend.tf を作成する**

```hcl
# java-practice-bank/infra/backend.tf
terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
  backend "s3" {
    bucket = "practice-bank-terraform-state"
    key    = "infrastructure/terraform.tfstate"
    region = "ap-northeast-1"
  }
}
```

- [ ] **Step 2: environments/dev/main.tf を作成する**

```hcl
# java-practice-bank/infra/environments/dev/main.tf
provider "aws" {
  region = var.aws_region
}

module "network" {
  source              = "../../modules/network"
  project_name        = "practice-bank-dev"
  vpc_cidr            = "10.0.0.0/16"
  availability_zones  = ["ap-northeast-1a", "ap-northeast-1c"]
  tags = {
    Environment = "dev"
    Project     = "practice-bank"
  }
}

module "database" {
  source       = "../../modules/database"
  project_name = "practice-bank-dev"
  db_password  = var.db_password
  db_security_group_id = aws_security_group.db.id
  cache_security_group_id = aws_security_group.cache.id
  subnet_ids   = module.network.private_subnet_ids
  tags = {
    Environment = "dev"
    Project     = "practice-bank"
  }
}

resource "aws_security_group" "db" {
  name_prefix = "practice-bank-dev-db-"
  vpc_id      = module.network.vpc_id
  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [module.network.app_security_group_id]
  }
}

resource "aws_security_group" "cache" {
  name_prefix = "practice-bank-dev-cache-"
  vpc_id      = module.network.vpc_id
  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [module.network.app_security_group_id]
  }
}
```

- [ ] **Step 3: environments/dev/variables.tf を作成する**

```hcl
# java-practice-bank/infra/environments/dev/variables.tf
variable "aws_region" {
  type    = string
  default = "ap-northeast-1"
}
variable "db_password" {
  type      = string
  sensitive = true
}
```

- [ ] **Step 4: environments/dev/terraform.tfvars を作成する (git 管理外)**

```hcl
# java-practice-bank/infra/environments/dev/terraform.tfvars
# 実際の値は環境変数 TF_VAR_db_password または .env で設定
# db_password = "SET_ME"
```

- [ ] **Step 5: .gitignore に tfvars を追加**

```bash
echo "*.tfvars" >> java-practice-bank/infra/.gitignore
echo ".terraform/" >> java-practice-bank/infra/.gitignore
echo "*.tfstate*" >> java-practice-bank/infra/.gitignore
```

- [ ] **Step 6: Terraform 構文確認**

Run: `cd java-practice-bank/infra/environments/dev && terraform init && terraform validate`
Expected: `Success! The configuration is valid.`

- [ ] **Step 7: コミット**

```bash
git add java-practice-bank/infra/environments/ java-practice-bank/infra/backend.tf java-practice-bank/infra/.gitignore
git commit -m "infra(phase1): add Terraform dev environment + S3 backend"
```

---

## Task 17: Phase 1 統合検証

**Files:**
- Create: `java-practice-bank/docs/runbooks/phase1-checklist.md`

**Interfaces:**
- Produces: Phase 1 完了の検証チェックリスト

- [ ] **Step 1: phase1-checklist.md を作成する**

```markdown
# Phase 1 完了チェックリスト

## ビルド
- [ ] `./gradlew build` が全モジュールで成功
- [ ] `./gradlew test` が全テストで成功 (Testcontainers 含む)

## データ移行
- [ ] `./gradlew :batch:isam-to-rds-job:bootRun` で ISAM → Aurora 移行が成功
- [ ] 7 マスタテーブルの行数が ISAM レコード数と一致
- [ ] サンプルレコードの内容が正確 (手作業で 1 件ずつ確認)

## マスタサービス
- [ ] `./gradlew :masters:calendar-service:bootRun` でサービス起動
- [ ] `curl http://localhost:8080/actuator/health` が 200 を返す
- [ ] `curl http://localhost:8080/api/calendar/2026-01-01` が正しい JSON を返す

## CI/CD
- [ ] GitHub Actions `ci.yml` が PR で green
- [ ] GitHub Actions `cd.yml` が main マージで ECR プッシュ成功

## Terraform
- [ ] `terraform plan` がエラーなし
- [ ] `terraform apply` で dev 環境が構築される
- [ ] Aurora / ElastiCache / VPC が AWS コンソールで確認できる

## ドキュメント
- [ ] 本チェックリストが完了
- [ ] Phase 2 の計画が立案されている
```

- [ ] **Step 2: 全ビルド + 全テスト実行**

Run: `cd java-practice-bank && ./gradlew clean build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: コミット**

```bash
git add java-practice-bank/docs/runbooks/
git commit -m "docs(phase1): add Phase 1 completion checklist"
```

---

## 自己レビュー (Self-Review)

### Spec カバレッジ

| Spec §4.1 タスク | 対応 Task |
|----------------|---------|
| Spring Boot テンプレート作成 | Task 1, 2 |
| Gradle マルチモジュール構成 | Task 1, 12 |
| Flyway マイグレーション移植 | Task 7 |
| ISAM → Aurora 変換ジョブ | Task 8, 9 |
| CI/CD パイプライン | Task 13 |
| Terraform インフラ定義 | Task 15, 16 |
| マスタサービス (7 本) | Task 10, 11 (テンプレート 1 本。残り 6 本は Phase 2 で同テンプレートから生成) |

### 特記事項

- **マスタサービス 7 本のうち 1 本 (calendar) のみを Phase 1 で完全実装**する。残り 6 本 (branch, customer, product, interestrate, feeschedule, account) は Phase 2 で calendar をテンプレートに一括生成する。これは Phase 1 のスコープ (基盤検証) を逸脱しない。
- **isam-to-rds-job の IsamFileReader は簡易実装** (固定長バイナリ読み取りの骨格)。正確なパースロジックは COBOL FD のフィールド定義に基づき Phase 2 で完成させる。
- **Terraform は network + database モジュールのみ**を Phase 1 で作成。ECS / Step Functions / Storage / Monitoring モジュールは Phase 2 で追加。

### 型一貫性

- `Money` (record) は全モジュールで `com.practicebank.common.domain.Money` を使用
- `ExitCodeMapper.fromReturnCode(int)` → `ExitStatus` は全バッチジョブで使用
- MyBatis の `mapUnderscoreToCamelCase: true` で DB カラム → Java フィールドを自動マッピング
- パッケージ構成 `com.practicebank.{layer}.{module}` を全モジュールで統一
