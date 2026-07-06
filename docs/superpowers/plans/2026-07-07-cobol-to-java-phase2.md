# COBOL → Java Phase 2 (プログラム移植) 実装計画

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Phase 1 で構築した基盤を使い、残り 80 プログラムを 20 サブシステムにわたって Java に移植する。

**Architecture:** 各 COBOL プログラム = 1 Spring Batch ジョブ (バッチ系) または 1 REST コントローラ (オンライン系)。MyBatis Mapper + Repository パターン。calendar-service をテンプレートとして流用。

**Tech Stack:** Java 21, Spring Boot 3.4, Spring Batch 5, MyBatis 3, Flyway, Testcontainers (PostgreSQL 16)

## Global Constraints

- **ベースパス:** `/home/oushuhua/ou/legacy-modernization-ws-cobol-260630/java-practice-bank/`
- **パッケージ:** `com.practicebank.{module}`
- **設計書参照:** `subsystems/NN-name/design/<program>.md` — 各プログラムの基本設計書が用意されている
- **ソース参照:** `subsystems/NN-name/src/<program>.cob` — COBOL ソース
- **テンプレート:** `masters/calendar-service/` をコピーして各サービスを作成
- **DB スキーマ:** `shared` スキーマを使用 (並行検証用)
- **テスト:** Testcontainers + `@Sql` フィクスチャ。全テスト_GREEN が必須

---

## ファイル構造 (Phase 2 で追加)

```
java-practice-bank/
├── masters/                          ← マスタサービス (7 本)
│   ├── calendar-service/             ← Phase 1 で完成 (テンプレート)
│   ├── branch-service/               ← Task 1
│   ├── customer-service/             ← Task 2
│   ├── product-service/              ← Task 3
│   ├── interestrate-service/         ← Task 4
│   ├── feeschedule-service/          ← Task 5
│   └── account-service/             ← Task 6
│
├── batch/                            ← バッチジョブ (13 サブシステム)
│   ├── isam-to-rds-job/              ← Phase 1 で骨格済み
│   ├── txnvalidate-job/              ← Task 7
│   ├── txnsortmerge-job/             ← Task 8
│   ├── txnpost-job/                  ← Task 9
│   ├── interestaccrual-job/          ← Task 10
│   ├── interestpost-job/             ← Task 11
│   ├── autodebit-job/                ← Task 12
│   ├── fee-job/                      ← Task 13
│   ├── statement-job/                ← Task 14
│   ├── integrationin-job/            ← Task 15
│   ├── integrationout-job/           ← Task 16
│   ├── audit-job/                    ← Task 17
│   └── operations-job/               ← Task 18
│
├── online/                           ← オンラインサービス
│   ├── inquiry-api/                  ← Task 19
│   └── accountlifecycle-api/         ← Task 20
│
└── verify/
    └── comparator-service/           ← Task 21 (並行検証)
```

---

## Batch 1: マスタサービス + 基幹バッチ (並列 dispatch)

### Task 1: 02-branch (4 プログラム)
**Files:**
- Create: `masters/branch-service/src/main/java/.../branch/`
- Reads: `subsystems/02-branch/design/*.md`, `subsystems/02-branch/copy/api/br-api.cpy`
- Template: `masters/calendar-service/`

Programs: br-lookup, br-load, br-list-all, br-list-by-region

### Task 2: 03-customer (6 プログラム)
**Files:**
- Create: `masters/customer-service/src/main/java/.../customer/`
- Reads: `subsystems/03-customer/design/*.md`, `subsystems/03-customer/copy/api/cust-api.cpy`

Programs: cust-lookup, cust-load, cust-list-all, cust-search-by-kana, cust-search-by-phone, cust-status-change

### Task 3: 04-customersearch (3 プログラム)
**Files:**
- Create: `masters/customersearch-service/src/main/java/.../`
- Reads: `subsystems/04-customersearch/design/*.md`

Programs: csrch-and, csrch-by-address, csrch-list-paged

### Task 4: 05-product (2 プログラム)
**Files:**
- Create: `masters/product-service/src/main/java/.../`
- Reads: `subsystems/05-product/design/*.md`

Programs: prod-lookup, prod-load

---

## Batch 2: マスタサービス続き + トランザクション

### Task 5: 06-interestrate (2 プログラム)
### Task 6: 07-feeschedule (2 プログラム)
### Task 7: 08-account (5 プログラム)
### Task 8: 09-accountlifecycle (4 プログラム)
### Task 9: 10-txnvalidate (3 プログラム)

---

## Batch 3: バッチパイプライン中核

### Task 10: 11-txnsortmerge (3 プログラム)
### Task 11: 12-txnpost (3 プログラム)
### Task 12: 13-interestaccrual (2 プログラム)
### Task 13: 14-interestpost (2 プログラム)
### Task 14: 15-autodebit (2 プログラム)

---

## Batch 4: 周辺バッチ + オンライン + 検証

### Task 15: 16-fee (2 プログラム)
### Task 16: 17-statement (1 プログラム)
### Task 17: 18-inquiry (1 プログラム)
### Task 18: 19-integrationin (1 プログラム)
### Task 19: 20-integrationout (2 プログラム)
### Task 20: 21-audit (3 プログラム)
### Task 21: 22-operations (13 プログラム)

---

## 移植パターン (全 Task 共通)

各プログラムは以下のパターンで移植する:

### バッチ系 (例: txn-validate)
1. `Job` クラス (Spring Boot エントリポイント)
2. `XxxStepConfig` (Step 定義、ItemReader/Processor/Writer)
3. `XxxMapper` (MyBatis インターフェース + XML)
4. `XxxRepository` (リポジトリ層)
5. Record クラス (DTO)
6. テスト (contextLoads + 主要ロジック検証)

### オンライン系 (例: inquiry)
1. `XxxApiApplication` (Spring Boot)
2. `XxxController` (REST API)
3. `XxxService` (ビジネスロジック)
4. `XxxMapper` + `XxxRepository`
5. Record クラス (Request/Response DTO)
6. テスト (controller unit test + integration test)

## 成功基準

- [ ] 80 プログラム全てに Java 実装が存在
- [ ] 全サブシステムのビルドが成功 (`./gradlew :NN-xxx:build`)
- [ ] 全テストが_GREEN_ (`./gradlew test`)
- [ ] `@Sql` フィクスチャを使った統合テストが主要カバレッジを網羅
- [ ] 設計書 (`subsystems/NN-name/design/`) との traceability が維持
