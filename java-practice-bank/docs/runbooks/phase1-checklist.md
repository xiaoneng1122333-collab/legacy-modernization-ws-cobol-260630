# Phase 1 完了チェックリスト

> **対象:** COBOL → Java + AWS モダナイズ Phase 1 (基盤構築)
> **完了条件:** 全チェックを満たすこと

---

## 1. ビルド

- [ ] `cd java-practice-bank && ./gradlew clean build` が全モジュールで成功
- [ ] エラー・警告が 0 件
- [ ] 全モジュールが `settings.gradle.kts` に登録されている

## 2. データ移行

- [ ] `java-practice-bank/db/migration/` に 8 つの Flyway SQL が存在
- [ ] `V8__cobol_java_dual_schema.sql` で `cobol` / `java` / `shared` スキーマが作成される
- [ ] `batch/isam-to-rds-job` がビルド成功

## 3. マスタサービス

- [ ] `masters/calendar-service` がビルド成功
- [ ] `CalendarServiceApplication.java` が Spring Boot エントリポイント
- [ ] `CalendarMapper.java` が MyBatis インターフェース
- [ ] `CalendarRepositoryTest.java` が Testcontainers を使用

## 4. 共通モジュール

- [ ] `common-domain` — Money, AccountStatus, TransactionStatus, DayType
- [ ] `common-batch` — BatchJobConfig, ExitCodeMapper
- [ ] `common-mybatis` — MyBatisConfig, MoneyTypeHandler
- [ ] `common-test` — PostgresTestContainer, RedisTestContainer

## 5. CI/CD

- [ ] `.github/workflows/ci.yml` が PR でビルド+テストを実行
- [ ] `.github/workflows/cd.yml` が main マージで ECR プッシュ
- [ ] `Dockerfile` がマルチステージビルド

## 6. Terraform

- [ ] `infra/modules/network` — VPC, Subnet, Security Group
- [ ] `infra/modules/database` — Aurora, ElastiCache
- [ ] `infra/backend.tf` — S3 backend 設定
- [ ] `infra/environments/dev` — dev 環境定義
- [ ] `infra/.gitignore` で `.terraform/` と `*.tfstate` を除外

## 7. ディレクトリ構成

```
java-practice-bank/
├── build.gradle.kts ✅
├── settings.gradle.kts ✅
├── gradle.properties ✅
├── Dockerfile ✅
├── db/migration/ (8 files) ✅
├── common/
│   ├── common-domain ✅
│   ├── common-batch ✅
│   ├── common-mybatis ✅
│   └── common-test ✅
├── batch/
│   └── isam-to-rds-job ✅
├── masters/
│   └── calendar-service ✅
├── .github/workflows/ ✅
├── infra/
│   ├── backend.tf ✅
│   ├── modules/
│   │   ├── network ✅
│   │   └── database ✅
│   └── environments/dev ✅
└── docs/runbooks/ ✅
```

## 8. Phase 2 への引き継ぎ

- [ ] Phase 2 で作成する 22 サブモジュールが `settings.gradle.kts` にコメントで列挙済み
- [ ] 設計書 (`subsystems/*/design/`) が移植仕様として参照可能
- [ ] 並行検証スキーマ (`cobol` / `java` / `shared`) が準備済み

---

**確認者:** _________
**確認日:** 2026-___-___
**結果:** PASS / FAIL
