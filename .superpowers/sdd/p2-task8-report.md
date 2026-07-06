# Phase 2 Task 8 — accountlifecycle-service 実装レポート

> **プログラム:** `alc-open` / `alc-change-state` / `alc-dormancy-scan` / `alc-reactivation-scan`
> **サブシステム:** 09-accountlifecycle
> **実装日:** 2026-07-06

---

## 1. ステータス

| 項目 | 結果 |
|------|------|
| ビルド | ✅ `BUILD SUCCESSFUL` |
| テスト | ✅ 21 tests, 0 failures, 0 errors |
| コード配置 | ✅ `java-practice-bank/masters/accountlifecycle-service/` |
| settings.gradle.kts | ✅ `include("masters:accountlifecycle-service")` を追加 (product-service 直後) |
| DB マイグレーション | ✅ `db/migration/V8__account_lifecycle_columns.sql` 追加 |

---

## 2. コミット SHA

(コミット前 — 後述 §8 の手順で確定)

---

## 3. 設計書との対応

### 3.1 プログラム → Java クラス対応

| COBOL プログラム | 種別 | Java クラス | 責務 |
|----------------|------|------------|------|
| ALC-OPEN | オンライン | `AlcOpenService` | 新規口座開設・枝番採番 (status="P" INSERT) |
| ALC-CHANGE-STATE | オンライン | `AlcChangeStateService` | FSM ステート遷移 (AC/CN/SU/LS/CL/FC) |
| ALC-DORMANCY-SCAN | バッチ | `AlcDormancyScanService` | Active→Dormant 一括移行 (730 日基準) |
| ALC-REACTIVATION-SCAN | バッチ (MVP スタブ) | `AlcReactivationScanService` | 04 NO-CANDS を返すプレースホルダー |

### 3.2 状態機械 (P→A→S→L→C→F→D)

`AlcChangeStateService.TRANSITIONS` に FSM を実装:

| ACTION | 遷移元 | 次状態 | reason 必須 |
|--------|--------|--------|:-----------:|
| AC | P | A | — |
| CN | P | C | — |
| SU | A / D | S | ✅ |
| LS | S | A | — |
| CL | A / D | C | — |
| FC | P / A / S / D (not C) | C | ✅ |

- Close 系遷移 (CL/FC) で `closed_date` に `business_date` を設定 (設計書 3.2 準拠)。
- AUDRITE 相当の監査証拠は構造化 SLF4J ログ (`STATUS_CHANGED ... from=.. to=.. reason=..`) で記録。
- 未知 ACTION → `08 INVALID` / 口座不在 → `04 NOT_FOUND` / 書込失敗 → `12 IO_FAIL`。

### 3.3 返却コード (ProgramStatus)

`"00"` OK / `"04"` NOT-FOUND / `"08"` INVALID / `"12"` IO-FAIL / `"16"` FATAL
(COBOL 88 レベル値と同一 — copybook `ALC-*-STATUS` に準拠)。

### 3.4 枝番採番 (ALC-OPEN)

- スキーム: `branch(3) + product(3) + serial(7)` = 13 桁 (copybook `ACCT-REC-NUMBER PIC 9(13)` 準拠)。
- 連番範囲: 9000000–9999999 (設計書 §2.1 の `9000000 から 9999999 の範囲で未使用の連番`)。
- COBOL が `LOOP で READ して空きを探す` 処理と等価な実装:
  プレフィクス検索 (`findNumbersByPrefix`) → TreeSet で既存集合構築 → 最初の空きを決定。
- 上限超過時は `08 INVALID` 返却。

---

## 4. ファイル構成

```
db/migration/V8__account_lifecycle_columns.sql   accounts テーブルに 3 列追加
java-practice-bank/settings.gradle.kts           include 追加
java-practice-bank/masters/accountlifecycle-service/
├── build.gradle.kts                             テンプレート踏襲
├── src/main/resources/application.yml           port 8092
├── src/main/java/.../accountlifecycle/
│   ├── AccountLifecycleServiceApplication.java  Boot 起動クラス
│   ├── domain/Account.java                     Java 17 record (10 フィールド)
│   ├── mapper/AccountLifecycleMapper.java      MyBatis @Mapper インターフェース
│   ├── repository/AccountLifecycleRepository.java
│   └── program/
│       ├── ProgramStatus.java                  返却コード enum
│       ├── AlcOpenService.java                 ALC-OPEN (P 挿入 + 枝番)
│       ├── AlcChangeStateService.java          ALC-CHANGE-STATE (FSM)
│       ├── AlcDormancyScanService.java         ALC-DORMANCY-SCAN (730 日)
│       └── AlcReactivationScanService.java     ALC-REACTIVATION-SCAN (スタブ)
├── src/main/resources/mappers/AccountLifecycleMapper.xml
└── src/test/
    ├── java/.../AccountLifecycleServiceTest.java  21 テスト (Testcontainers)
    └── resources/sql/accounts.sql                シード 13 件 (P/A/S/C/D + 休眠超過/基準日内)
```

---

## 5. テスト (21 件 / 全パス)

| 分類 | テスト | 検証ポイント |
|------|--------|------------|
| **ALC-OPEN** | `open_basicBranch001Product001_returnsNewAccountNumber` | 初回 = 0010019000000 採番 |
| | `open_secondCall_serialIncrements` | 2 回目 = 9000001 |
| | `open_newBranch_startsAt9000000` | 新規 branch は 9000000 から |
| | `open_zeroCustId_returnsInvalid` | cust=0 → 08 |
| | `open_zeroBranch_returnsInvalid` | branch=0 → 08 |
| | `open_zeroProduct_returnsInvalid` | product=0 → 08 |
| | `open_insertedAccount_hasStatusPending` | INSERT 後 status=P |
| **ALC-CHANGE-STATE** | `changeState_pendingToActive_success` | P→A (AC) |
| | `changeState_pendingToCancel_success` | P→C (CN) |
| | `changeState_activeToSuspend_withReason_success` | A→S (SU, reason) |
| | `changeState_suspendToActive_success` | S→A (LS) |
| | `changeState_activeToClose_setsClosedDate` | A→C, closedDate 設定 |
| | `changeState_forceClose_fromSuspended_setsClosedDate` | S→C (FC), closedDate |
| | `changeState_unknownAction_returnsInvalid` | ZZ → 08 |
| | `changeState_pendingToSuspend_disallowed` | P→S 禁止 → 08 |
| | `changeState_suspendWithoutReason_returnsInvalid` | SU reason 不足 → 08 |
| | `changeState_forceCloseWithoutReason_returnsInvalid` | FC reason 不足 → 08 |
| | `changeState_notFound_returnsNotFound` | 不在 → 04 |
| **ALC-DORMANCY-SCAN** | `dormancyScan_transitionsOverThreshold` | 基準日超過 2 件 → D |
| | `dormancyScan_businessDateNearNow_noTransition` | 移行 0, スキップ >0 |
| **ALC-REACTIVATION-SCAN** | `reactivationScan_stub_returnsNoCands` | 04 NO-CANDS |

---

## 6. 懸念事項 (Concerns)

1. **accounts テーブル重複スキーマのリスク**
   現行 `db/migration/V2__master_pg_tables.sql` は 08-account サブシステムのもので、
   `acct_name` 列を含むが `closed_date` / `overdraft_limit` / `term_days` が不足。
   今回 V8 で不足 3 列を追加したため、依存関係は解消した。ただし将来 08-account
   (account-service) が同一テーブルを扱うため、列追加は mutually compatible な
   方法 (ADD COLUMN only) に留めている。

2. **account-service スタブとの不整合 (低)**
   `masters/account-service/` は未コミットのスタブで `Account.java` が
   スモールスキーマ (`acct_name`, `opened_date`, `dormancy_date` のみ) を使用している。
   account-service 本実装時に今回の 10 フィールド `Account` と統合が必要。
   account-service は settings.gradle.kts でまだ include 済みだがスタブのみのため
   ビルドに影響しない。

3. **dormancy_date NULL の扱い**
   COBOL は `PIC 9(8)` 初期値 00000000 を比較するが、RDB 安全側として NULL を
   休眠判定対象外 (`IS NOT NULL` 条件付き) とした。これにより「取引実績未登録」
   口座が一括移行される誤りを避ける。COBOL との差異として明示。

4. **REACTIVATION-SCAN が MVP スタブ**
   設計書 `alc-reactivation-scan.md` の指定どおり常に `04 NO-CANDS` を返す
   プレースホルダー。将来実装時は同一トリガ (status="D" 最近接ルール) を
   実装するインターフェース (`scan(ReactivationInput)`) を先取りした。

5. **監査証拠の代替**
   COBOL の `CALL "AUD-Write"` 共有ユーティリティに相当する監査証拠の永続化は
   未実装 (構造化 SLF4J ログで代替)。本番実装時には audit_log テーブル or
   メッセージキュー書き込みに差し替え予定。

---

## 7. ビルド/実行方法

```bash
cd java-practice-bank
./gradlew :masters:accountlifecycle-service:build
# 結果: BUILD SUCCESSFUL / 21 tests passed
```

---

## 8. コミット手順

```bash
git add -A \
  db/migration/V8__account_lifecycle_columns.sql \
  java-practice-bank/settings.gradle.kts \
  java-practice-bank/masters/accountlifecycle-service/

git commit -m "feat(phase2): add accountlifecycle-service (4 programs)
"
```
