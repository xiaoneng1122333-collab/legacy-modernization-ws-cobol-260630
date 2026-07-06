# データ辞書 (Data Dictionary)

> 対象システム: レガシ COBOL 銀行バッチ処理システム (legacy-modernization-ws-cobol-260630)
> 作成日: 2026-07-06
> 対象リビジョン: DDL V1/V2/V3 + 全サブシステム copybook (22 サブシステム + shared)

---

## 1. はじめに

### 1.1 目的

本事実書は、レガシ COBOL 銀行バッチ処理システムの**データ資産**を体系的に定義する。
ISAM インデックスファイル、PostgreSQL テーブル、COBOL copybook で定義される
インターフェースレコード、および一時ファイルの構造を一元的に記述し、
モダナイゼーション（COBOL → 現代的スタック移行）におけるデータマッピングの
出発点とする。

### 1.2 スコープ

| 層 | データストア | 数 |
|---|---|---|
| マスタ (ISAM) | `.idx` インデックスファイル | 7 |
| マスタ (PostgreSQL) | `accounts`, `customers`, `branches`, `products`, `calendar`, `interest_rates`, `fee_schedules` | 7 |
| トランザクション (PostgreSQL) | `transactions`, `postings`, `balances`, `interest_accruals`, `autodebit_schedules`, `batch_run`, `audit_log` | 7 |
| トランザクション (ISAM) | `txn-ready`, `txn-sorted`, `txn-valid`, `txn-error` | 4+ |
| インターフェース (copybook) | サブシステム API 入出力領域 | 22 サブシステム + shared |
| 一時ファイル | チェックポイント / サマリ / リコン / ドーマンシー修復 | 10+ |

### 1.3 命名規則

| 規則 | 説明 |
|---|---|
| ISAM ファイル | `fd-<name>.cpy` に FD 記述、`RECORD CONTAINS n CHARACTERS` でレコード長明示 |
| copybook | `xxx-api.cpy` (API 入出力)、`fd-xxx.cpy` (ファイル記述)、`ws-xxx.cpy` (ワークステーション) |
| PIC 表記 | COBOL 標準: `9(n)` 数値、`X(n)` 英数、`S9(n)V9(m) COMP-3` 符号付き十進 (パック) |
| 88-level | 条件名 (列挙値)。例: `88 ACCT-ST-ACTIVE VALUE "A"` |

### 1.4 凡例

| 略語 | 意味 |
|---|---|
| PK | Primary Key |
| FK | Foreign Key |
| CK | CHECK 制約 |
| UQ | Unique Index |
| COMP-3 | パック十進 (COBOL `USAGE IS COMP-3`) |
| ISAM | Indexed Sequential Access Method (COBOL インデックスファイル) |

---

## 2. データストア一覧

### 2.1 マスタデータストア

| # | ストア種別 | 論理名 | 物理名 | 主キー | レコード長 | 読取 / 書込プログラム |
|---|---|---|---|---|---|---|
| 1 | ISAM | カレンダー | `calendar.idx` | `CAL-REC-DATE` | 60 | 01-CALENDAR / 22-OPS |
| 2 | ISAM | 支店マスタ | `branch.idx` | `BR-REC-CODE` | 124 | 02-BRANCH / 08-ACCT |
| 3 | ISAM | 顧客マスタ | `customer.idx` | `CR-ID` | 359 | 03-CUSTOMER / 08-ACCT |
| 4 | ISAM | 商品マスタ | `product.idx` | `PRD-REC-CODE` | 110 | 05-PRODUCT / 08-ACCT |
| 5 | ISAM | 金利マスタ | `interestrate.idx` | `IR-REC-PRODUCT`+`TIER`+`EFF-FROM` | 38 | 06-IRATE / 13-IACR |
| 6 | ISAM | 手数料マスタ | `feeschedule.idx` | `FS-REC-CATEGORY`+`TIER`+`EFF-FROM` | 41 | 07-FS / 16-FEE |
| 7 | ISAM | 口座マスタ | `account.idx` | `ACCT-REC-NUMBER` | 76 | 08-ACCT / 09-ALC |
| 8 | PG | 口座 | `accounts` | `acct_number` | — | 08-ACCT / 09-ALC / 12-TXNPOST |
| 9 | PG | 顧客 | `customers` | `cust_id` | — | 03-CUSTOMER / 08-ACCT |
| 10 | PG | 支店 | `branches` | `branch_code` | — | 02-BRANCH |
| 11 | PG | 商品 | `products` | `product_code` | — | 05-PRODUCT |
| 12 | PG | カレンダー | `calendar` | `cal_date` | — | 01-CALENDAR |
| 13 | PG | 金利 | `interest_rates` | `(product_code, effective_date)` | — | 06-IRATE |
| 14 | PG | 手数料 | `fee_schedules` | `(category, tier, effective_date)` | — | 07-FS |

### 2.2 トランザクションデータストア

| # | ストア種別 | 論理名 | 物理名 | 主キー | 読取 / 書込プログラム |
|---|---|---|---|---|---|
| 1 | PG | 取引 | `transactions` | `txn_id` | 10-TXVAL / 12-TXNPOST |
| 2 | PG | 仕訳 | `postings` | `posting_id` | 12-TXNPOST |
| 3 | PG | 残高 | `balances` | `account_number` | 12-TXNPOST |
| 4 | PG | 利息計算 | `interest_accruals` | `accrual_id` | 13-IACR / 14-IPST |
| 5 | PG | 自動引落 | `autodebit_schedules` | `instruction_id` | 15-AUTODEBIT |
| 6 | PG | バッチ実行 | `batch_run` | `batch_id` | 22-OPS |
| 7 | PG | 監査ログ | `audit_log` | `(business_date, audit_id)` | 全サブシステム |
| 8 | ISAM | 取引-ready | `txn-ready.dat` | (順次) | 11-TXSM → 12-TXNPOST |
| 9 | ISAM | 取引-sorted | `txn-sorted.dat` | (順次) | 11-TXSM |
| 10 | ISAM | 取引-valid | `txn-valid.dat` | (順次) | 10-TXVAL |
| 11 | ISAM | 取引-error | `txn-error.dat` | (順次) | 10-TXVAL / 11-TXSM |

### 2.3 運営データストア

| # | ストア種別 | 論理名 | 物理名 | 読取 / 書込プログラム |
|---|---|---|---|---|
| 1 | ISAM | チェックポイント | `txn-checkpoint.dat` | 10-TXVAL / 11-TXSM / 12-TXNPOST / 13-IACR / 14-IPST / 15-AD |
| 2 | ISAM | 利息サマリ | `iacr-summary.dat` | 13-IACR |
| 3 | ISAM | ドーマンシー修復 | `dormancy-repair.dat` | 12-TXNPOST |
| 4 | ISAM | リコン前日 | `txn-recon-prev.dat` | 11-TXSM |
| 5 | ISAM | リコン保留 | `txn-recon-defer.dat` | 12-TXNPOST |
| 6 | ISAM | 自動引落失敗 | `autodebit-failed.dat` | 15-AUTODEBIT |

---

## 3. マスタデータ定義

### 3.1 ISAM ファイル (7 種)

#### 3.1.1 カレンダーファイル (`calendar.idx`)

- **FD**: `fd-calendar.cpy`
- **レコード長**: 60 バイト
- **主キー**: `CAL-REC-DATE`

| フィールド | PIC | 長さ | 説明 | 制約 / 88-level |
|---|---|---|---|---|
| `CAL-REC-DATE` | `PIC 9(8)` | 8 | 日付 (YYYYMMDD) | PK |
| `CAL-REC-DAY-TYPE` | `PIC X(1)` | 1 | 日タイプ | `"B"` 営業日 / `"H"` 祝日 / `"W"` 週末 |
| `CAL-REC-HOLIDAY-NAME` | `PIC X(40)` | 40 | 祝日名 | 祝日以外は SPACE |
| `CAL-REC-FILLER` | `PIC X(11)` | 11 | 予約領域 | — |

- **読取**: 01-CALENDAR, 22-OPS, 12-TXNPOST (営業日判定)
- **書込**: 22-OPS (カレンダー登録・更新)

---

#### 3.1.2 支店マスタ (`branch.idx`)

- **FD**: `fd-branch.cpy`
- **レコード長**: 124 バイト
- **主キー**: `BR-REC-CODE`

| フィールド | PIC | 長さ | 説明 | 制約 / 88-level |
|---|---|---|---|---|
| `BR-REC-CODE` | `PIC X(3)` | 3 | 支店コード | PK |
| `BR-REC-NAME-KANJI` | `PIC X(40)` | 40 | 支店名 (漢字) | — |
| `BR-REC-NAME-KANA` | `PIC X(40)` | 40 | 支店名 (カナ) | — |
| `BR-REC-REGION` | `PIC X(20)` | 20 | 地域名 | — |
| `BR-REC-OPENED-DATE` | `PIC 9(8)` | 8 | 開設日 | — |
| `BR-REC-STATUS` | `PIC X(1)` | 1 | 支店ステータス | — |
| `BR-REC-FILLER` | `PIC X(20)` | 20 | 予約領域 | — |

- **読取**: 02-BRANCH, 08-ACCT (口座開設時)
- **書込**: 02-BRANCH (支店登録・更新)

---

#### 3.1.3 顧客マスタ (`customer.idx`)

- **FD**: `fd-customer.cpy`
- **レコード長**: 359 バイト
- **主キー**: `CR-ID`

| フィールド | PIC | 長さ | 説明 | 制約 / 88-level |
|---|---|---|---|---|
| `CR-ID` | `PIC 9(10)` | 10 | 顧客番号 | PK |
| `CR-KANA` | `PIC X(50)` | 50 | カナ氏名 | — |
| `CR-KANJI` | `PIC X(60)` | 60 | 漢字氏名 | — |
| `CR-PHONE` | `PIC X(15)` | 15 | 電話番号 | — |
| `CR-ADDRESS` | `PIC X(200)` | 200 | 住所 | — |
| `CR-OPENED-DATE` | `PIC 9(8)` | 8 | 開設日 | — |
| `CR-STATUS` | `PIC X(1)` | 1 | 顧客ステータス | — |
| `CR-CREATED-TS` | `PIC 9(14)` | 14 | 作成タイムスタンプ (YYYYMMDDHHNNSS) | — |
| `CR-UPDATED-TS` | `PIC 9(14)` | 14 | 更新タイムスタンプ | — |
| `CR-TIER` | `PIC X(1)` | 1 | 顧客ティア | — |
| `CR-FILLER` | `PIC X(19)` | 19 | 予約領域 | — |

- **読取**: 03-CUSTOMER, 08-ACCT, 18-INQUIRY
- **書込**: 03-CUSTOMER (開設・ステータス変更)

---

#### 3.1.4 商品マスタ (`product.idx`)

- **FD**: `fd-product.cpy`
- **レコード長**: 110 バイト
- **主キー**: `PRD-REC-CODE`

| フィールド | PIC | 長さ | 説明 | 制約 / 88-level |
|---|---|---|---|---|
| `PRD-REC-CODE` | `PIC X(3)` | 3 | 商品コード | PK |
| `PRD-REC-NAME-KANJI` | `PIC X(40)` | 40 | 商品名 (漢字) | — |
| `PRD-REC-NAME-KANA` | `PIC X(40)` | 40 | 商品名 (カナ) | — |
| `PRD-REC-TYPE` | `PIC X(1)` | 1 | 商品タイプ | `"S"` 普通預金 / `"C"` 当座 / `"T"` 定期 |
| `PRD-REC-INTEREST` | `PIC X(1)` | 1 | 利息適用フラグ | — |
| `PRD-REC-OVD` | `PIC X(1)` | 1 | 当座貸越許可フラグ | — |
| `PRD-REC-MIN-BAL` | `PIC S9(15) COMP-3` | 8 | 最低残高 (円) | 符号付き |
| `PRD-REC-TERM-DAYS` | `PIC 9(4)` | 4 | 期間 (日) | 定期のみ |
| `PRD-REC-EFF-FROM` | `PIC 9(8)` | 8 | 有効開始日 | — |
| `PRD-REC-EFF-TO` | `PIC 9(8)` | 8 | 有効終了日 | — |
| `PRD-REC-FILLER` | `PIC X(16)` | 16 | 予約領域 | — |

- **読取**: 05-PRODUCT, 08-ACCT, 13-IACR, 14-IPST
- **書込**: 05-PRODUCT

---

#### 3.1.5 金利マスタ (`interestrate.idx`)

- **FD**: `fd-irate.cpy`
- **レコード長**: 38 バイト
- **主キー**: `IR-REC-PRODUCT` + `IR-REC-TIER` + `IR-REC-EFF-FROM`

| フィールド | PIC | 長さ | 説明 | 制約 / 88-level |
|---|---|---|---|---|
| `IR-REC-KEY` | グループ | 13 | 複合キー | — |
| `IR-REC-PRODUCT` | `PIC X(3)` | 3 | 商品コード | PK (第1) |
| `IR-REC-TIER` | `PIC 9(2)` | 2 | ティア番号 | PK (第2) |
| `IR-REC-EFF-FROM` | `PIC 9(8)` | 8 | 適用開始日 | PK (第3) |
| `IR-REC-TIER-MIN` | `PIC S9(15) COMP-3` | 8 | ティア下限額 (円) | — |
| `IR-REC-TIER-MAX` | `PIC S9(15) COMP-3` | 8 | ティア上限額 (円) | — |
| `IR-REC-RATE` | `PIC S9(3)V9(4) COMP-3` | 4 | 年利率 (パック) | 例: `030000` = 3.0000% |
| `IR-REC-EFF-TO` | `PIC 9(8)` | 8 | 適用終了日 | — |
| `IR-REC-FILLER` | `PIC X(8)` | 8 | 予約領域 | — |

- **読取**: 06-IRATE, 13-IACR
- **書込**: 06-IRATE

---

#### 3.1.6 手数料マスタ (`feeschedule.idx`)

- **FD**: `fd-fs.cpy`
- **レコード長**: 41 バイト (`RECORD CONTAINS 41 CHARACTERS`)
- **主キー**: `FS-REC-CATEGORY` + `FS-REC-TIER` + `FS-REC-EFF-FROM`

| フィールド | PIC | 長さ | 説明 | 制約 / 88-level |
|---|---|---|---|---|
| `FS-REC-KEY` | グループ | 12 | 複合キー | — |
| `FS-REC-CATEGORY` | `PIC 9(2)` | 2 | 手数料カテゴリ | PK (第1)。`10` 入金 / `20` 出金 / `30` 振込 / `40` 電信 |
| `FS-REC-TIER` | `PIC 9(2)` | 2 | ティア番号 | PK (第2) |
| `FS-REC-EFF-FROM` | `PIC 9(8)` | 8 | 適用開始日 | PK (第3) |
| `FS-REC-TIER-MIN` | `PIC S9(15) COMP-3` | 8 | ティア下限額 (円) | — |
| `FS-REC-TIER-MAX` | `PIC S9(15) COMP-3` | 8 | ティア上限額 (円) | — |
| `FS-REC-AMOUNT` | `PIC S9(9) COMP-3` | 5 | 手数料額 (円) | — |
| `FS-REC-EFF-TO` | `PIC 9(8)` | 8 | 適用終了日 | — |

- **読取**: 07-FS, 16-FEE
- **書込**: 07-FS

---

#### 3.1.7 口座マスタ (`account.idx`)

- **FD**: `fd-account.cpy`
- **レコード長**: 76 バイト
- **主キー**: `ACCT-REC-NUMBER`

| フィールド | PIC | 長さ | 説明 | 制約 / 88-level |
|---|---|---|---|---|
| `ACCT-REC-NUMBER` | `PIC 9(13)` | 13 | 口座番号 | PK |
| `ACCT-REC-CUST-ID` | `PIC 9(10)` | 10 | 顧客番号 | FK → customer |
| `ACCT-REC-PRODUCT-CODE` | `PIC 9(3)` | 3 | 商品コード | FK → product |
| `ACCT-REC-BRANCH-CODE` | `PIC 9(3)` | 3 | 支店コード | FK → branch |
| `ACCT-REC-OPENED-DATE` | `PIC 9(8)` | 8 | 開設日 | — |
| `ACCT-REC-CLOSED-DATE` | `PIC 9(8)` | 8 | 解約日 | 解約時のみ |
| `ACCT-REC-STATUS` | `PIC X(1)` | 1 | 口座ステータス | `"P"` 申請中 / `"A"` 有効 / `"D"` ドーマンシー / `"S"` 停止 / `"C"` 解約 / `"R"` 再開 |
| `ACCT-REC-OVERDRAFT` | `PIC S9(15) COMP-3` | 8 | 当座貸越限度額 (円) | — |
| `ACCT-REC-TERM-DAYS` | `PIC 9(4)` | 4 | 期間 (日) | 定期のみ |
| `ACCT-REC-DORMANCY-DATE` | `PIC 9(8)` | 8 | ドーマンシー移行日 | — |
| `ACCT-REC-CREATED-TS` | `PIC 9(14)` | 14 | 作成タイムスタンプ | — |
| `ACCT-REC-UPDATED-TS` | `PIC 9(14)` | 14 | 更新タイムスタンプ | — |
| `ACCT-REC-FILLER` | `PIC X(16)` | 16 | 予約領域 | — |

- **読取**: 08-ACCT, 09-ALC, 10-TXVAL, 12-TXNPOST, 13-IACR, 14-IPST, 15-AD, 16-FEE, 17-STMT, 18-INQUIRY
- **書込**: 08-ACCT (開設), 09-ALC (ステータス変更・ドーマンシー更新)

---

### 3.2 PostgreSQL テーブル (マスタ系)

#### 3.2.1 `accounts` (口座)

| カラム | 型 | PK | FK | NOT NULL | デフォルト | インデックス | 説明 |
|---|---|---|---|---|---|---|---|
| `acct_number` | `CHAR(13)` | ✓ | | ✓ | — | PK | 口座番号 |
| `acct_name` | `VARCHAR(60)` | | | ✓ | — | — | 口座名義 |
| `branch_code` | `CHAR(3)` | | branches | ✓ | — | `idx_accounts_branch` | 支店コード |
| `product_code` | `CHAR(3)` | | products | ✓ | — | — | 商品コード |
| `acct_status` | `CHAR(1)` | | | ✓ | — | `idx_accounts_status` | 口座ステータス |
| `cust_id` | `CHAR(10)` | | customers | ✓ | — | `idx_accounts_cust` | 顧客番号 |
| `opened_date` | `DATE` | | | ✓ | `CURRENT_DATE` | — | 開設日 |
| `dormancy_date` | `DATE` | | | | — | — | ドーマンシー日 |
| `created_at` | `TIMESTAMP(0)` | | | ✓ | `NOW()` | — | 作成日時 |
| `updated_at` | `TIMESTAMP(0)` | | | ✓ | `NOW()` | — | 更新日時 |

---

#### 3.2.2 `customers` (顧客)

| カラム | 型 | PK | FK | NOT NULL | デフォルト | インデックス | 説明 |
|---|---|---|---|---|---|---|---|
| `cust_id` | `CHAR(10)` | ✓ | | ✓ | — | PK | 顧客番号 |
| `cust_name` | `VARCHAR(60)` | | | ✓ | — | — | 漢字氏名 |
| `cust_name_kana` | `VARCHAR(80)` | | | ✓ | — | `idx_customers_name_kana` | カナ氏名 |
| `cust_status` | `CHAR(1)` | | | ✓ | — | `idx_customers_status` | 顧客ステータス |
| `tier` | `CHAR(1)` | | | ✓ | `'B'` | — | 顧客ティア |
| `phone` | `VARCHAR(20)` | | | | — | `idx_customers_phone` | 電話番号 |
| `address` | `VARCHAR(120)` | | | | — | — | 住所 |
| `created_at` | `TIMESTAMP(0)` | | | ✓ | `NOW()` | — | 作成日時 |
| `updated_at` | `TIMESTAMP(0)` | | | ✓ | `NOW()` | — | 更新日時 |

---

#### 3.2.3 `branches` (支店)

| カラム | 型 | PK | FK | NOT NULL | デフォルト | インデックス | 説明 |
|---|---|---|---|---|---|---|---|
| `branch_code` | `CHAR(3)` | ✓ | | ✓ | — | PK | 支店コード |
| `branch_name` | `VARCHAR(60)` | | | ✓ | — | — | 支店名 (漢字) |
| `branch_name_kana` | `VARCHAR(80)` | | | ✓ | — | — | 支店名 (カナ) |
| `branch_type` | `CHAR(1)` | | | ✓ | — | — | 支店タイプ |
| `address` | `VARCHAR(120)` | | | | — | — | 住所 |
| `phone` | `VARCHAR(20)` | | | | — | — | 電話番号 |
| `created_at` | `TIMESTAMP(0)` | | | ✓ | `NOW()` | — | 作成日時 |
| `updated_at` | `TIMESTAMP(0)` | | | ✓ | `NOW()` | — | 更新日時 |

---

#### 3.2.4 `products` (商品)

| カラム | 型 | PK | FK | NOT NULL | デフォルト | インデックス | 説明 |
|---|---|---|---|---|---|---|---|
| `product_code` | `CHAR(3)` | ✓ | | ✓ | — | PK | 商品コード |
| `product_name` | `VARCHAR(60)` | | | ✓ | — | — | 商品名 |
| `product_type` | `CHAR(1)` | | | ✓ | — | — | 商品タイプ |
| `interest_eligible` | `CHAR(1)` | | | ✓ | `'Y'` | — | 利息適用可否 |
| `fee_eligible` | `CHAR(1)` | | | ✓ | `'Y'` | — | 手数料適用可否 |
| `min_balance_jpy` | `BIGINT` | | | ✓ | `0` | — | 最低残高 |
| `created_at` | `TIMESTAMP(0)` | | | ✓ | `NOW()` | — | 作成日時 |
| `updated_at` | `TIMESTAMP(0)` | | | ✓ | `NOW()` | — | 更新日時 |

---

#### 3.2.5 `calendar` (カレンダー)

| カラム | 型 | PK | FK | NOT NULL | デフォルト | インデックス | 説明 |
|---|---|---|---|---|---|---|---|
| `cal_date` | `DATE` | ✓ | | ✓ | — | PK | 日付 |
| `day_type` | `CHAR(1)` | | | ✓ | — | `idx_calendar_day_type` | 日タイプ |
| `holiday_name` | `VARCHAR(60)` | | | | — | — | 祝日名 |
| `fiscal_year` | `INTEGER` | | | ✓ | — | — | 会計年度 |
| `created_at` | `TIMESTAMP(0)` | | | ✓ | `NOW()` | — | 作成日時 |

---

#### 3.2.6 `interest_rates` (金利)

| カラム | 型 | PK | FK | NOT NULL | デフォルト | インデックス | 説明 |
|---|---|---|---|---|---|---|---|
| `product_code` | `CHAR(3)` | ✓ (第1) | products | ✓ | — | PK | 商品コード |
| `effective_date` | `DATE` | ✓ (第2) | | ✓ | — | PK | 適用開始日 |
| `annual_rate` | `NUMERIC(7,6)` | | | ✓ | — | — | 年利率 |
| `tier_threshold_jpy` | `BIGINT` | | | | — | — | ティア閾値 |
| `created_at` | `TIMESTAMP(0)` | | | ✓ | `NOW()` | — | 作成日時 |

---

#### 3.2.7 `fee_schedules` (手数料)

| カラム | 型 | PK | FK | NOT NULL | デフォルト | インデックス | 説明 |
|---|---|---|---|---|---|---|---|
| `category` | `CHAR(2)` | ✓ (第1) | | ✓ | — | PK | 手数料カテゴリ |
| `tier` | `CHAR(1)` | ✓ (第2) | | ✓ | — | PK | ティア |
| `effective_date` | `DATE` | ✓ (第3) | | ✓ | — | PK | 適用開始日 |
| `fee_jpy` | `BIGINT` | | | ✓ | — | — | 手数料額 (円) |
| `created_at` | `TIMESTAMP(0)` | | | ✓ | `NOW()` | — | 作成日時 |

---

## 4. トランザクションデータ定義

### 4.1 PostgreSQL テーブル (トランザクション系)

#### 4.1.1 `transactions` (取引)

| カラム | 型 | PK | FK | NOT NULL | CHECK | インデックス | 説明 |
|---|---|---|---|---|---|---|---|
| `txn_id` | `CHAR(18)` | ✓ | | ✓ | — | PK | 取引ID |
| `business_date` | `DATE` | | | ✓ | — | `idx_txn_bd_acct` | 営業日 |
| `system_ts` | `TIMESTAMP(0)` | | | ✓ | — | — | システムタイムスタンプ |
| `category` | `CHAR(2)` | | | ✓ | — | — | 取引カテゴリ |
| `account_number` | `CHAR(13)` | | accounts | ✓ | — | `idx_txn_bd_acct`, `idx_txn_acct_bd` | 口座番号 |
| `counter_account_number` | `CHAR(13)` | | | | — | — | 相手口座番号 |
| `amount_jpy` | `BIGINT` | | | ✓ | `> 0` | — | 金額 (円) |
| `currency` | `CHAR(3)` | | | ✓ | `= 'JPY'` | — | 通貨コード |
| `description` | `VARCHAR(120)` | | | | — | — | 摘要 |
| `source_system` | `VARCHAR(20)` | | | ✓ | — | — | 元システム |
| `source_batch_id` | `CHAR(14)` | | | ✓ | — | `uq_txn_source_batch_seq` | 元バッチID |
| `source_seq` | `INTEGER` | | | ✓ | — | `uq_txn_source_batch_seq` | 元シーケンス |
| `status` | `CHAR(2)` | | | ✓ | `IN ('PT','SE','RV')` | — | 取引ステータス |
| `reversal_of` | `CHAR(18)` | | transactions | | — | `uq_txn_reversal_of_when_rv` | 取消元取引ID |
| `created_by` | `VARCHAR(20)` | | | ✓ | — | — | 作成者 |
| `created_ts` | `TIMESTAMP(0)` | | | ✓ | `DEFAULT NOW()` | — | 作成日時 |

- **外部キー**: `reversal_of` → `transactions(txn_id)` (自己参照)
- **UQ**: `(source_batch_id, source_seq)` — 重複防止

---

#### 4.1.2 `postings` (仕訳)

| カラム | 型 | PK | FK | NOT NULL | CHECK | インデックス | 説明 |
|---|---|---|---|---|---|---|---|
| `posting_id` | `CHAR(20)` | ✓ | | ✓ | — | PK | 仕訳ID |
| `txn_id` | `CHAR(18)` | | transactions | ✓ | — | `idx_pst_txn`, `uq_pst_txn_line` | 取引ID |
| `line_no` | `SMALLINT` | | | ✓ | — | `uq_pst_txn_line` | 仕訳行番号 |
| `account_number` | `CHAR(13)` | | accounts | ✓ | — | `idx_pst_acct_bd` | 口座番号 |
| `debit_jpy` | `BIGINT` | | | ✓ | `>= 0` | — | 借方金額 |
| `credit_jpy` | `BIGINT` | | | ✓ | `>= 0` | — | 貸方金額 |
| `posting_role` | `CHAR(2)` | | | ✓ | `IN ('DR','CR')` | — | 借貸区分 |
| `business_date` | `DATE` | | | ✓ | — | `idx_pst_acct_bd` | 営業日 |
| `created_ts` | `TIMESTAMP(0)` | | | ✓ | `DEFAULT NOW()` | — | 作成日時 |

- **CK**: `pst_dr_xor_cr` — 借方・貸方は排他 (一方のみ非ゼロ)
- **UQ**: `(txn_id, line_no)` — 取引内で行番号一意

---

#### 4.1.3 `balances` (残高)

| カラム | 型 | PK | FK | NOT NULL | CHECK | インデックス | 説明 |
|---|---|---|---|---|---|---|---|
| `account_number` | `CHAR(13)` | ✓ | accounts | ✓ | — | PK | 口座番号 |
| `balance_jpy` | `BIGINT` | | | ✓ | — | — | 現在残高 (円) |
| `available_jpy` | `BIGINT` | | | ✓ | — | — | 利用可能残高 |
| `hold_jpy` | `BIGINT` | | | ✓ | `>= 0` | — | 保留額 |
| `last_txn_id` | `CHAR(18)` | | transactions | | — | — | 最終取引ID |
| `last_business_date` | `DATE` | | | | — | — | 最終営業日 |
| `updated_ts` | `TIMESTAMP(0)` | | | ✓ | `DEFAULT NOW()` | — | 更新日時 |

---

#### 4.1.4 `interest_accruals` (利息計算)

| カラム | 型 | PK | FK | NOT NULL | CHECK | インデックス | 説明 |
|---|---|---|---|---|---|---|---|
| `accrual_id` | `BIGSERIAL` | ✓ | | ✓ | — | PK | 計算ID |
| `business_date` | `DATE` | | | ✓ | — | `uq_iac_bd_acct`, `idx_iac_status_bd` | 営業日 |
| `account_number` | `CHAR(13)` | | accounts | ✓ | — | `uq_iac_bd_acct` | 口座番号 |
| `product_code` | `CHAR(3)` | | products | ✓ | — | — | 商品コード |
| `principal_jpy` | `BIGINT` | | | ✓ | — | — | 元本 (円) |
| `rate` | `NUMERIC(5,4)` | | | ✓ | — | — | 適用利率 |
| `days` | `SMALLINT` | | | ✓ | — | — | 日数 |
| `accrued_jpy` | `BIGINT` | | | ✓ | — | — | 発生利息 (円) |
| `status` | `CHAR(2)` | | | ✓ | `IN ('AC','PT','CN')` | `idx_iac_status_bd` | 利息ステータス |
| `posted_txn_id` | `CHAR(18)` | | transactions | | — | — | 起票取引ID |
| `created_ts` | `TIMESTAMP(0)` | | | ✓ | `DEFAULT NOW()` | — | 作成日時 |

- **UQ**: `(business_date, account_number)` — 1 口座 1 日 1 レコード

---

#### 4.1.5 `autodebit_schedules` (自動引落)

| カラム | 型 | PK | FK | NOT NULL | CHECK | インデックス | 説明 |
|---|---|---|---|---|---|---|---|
| `instruction_id` | `CHAR(20)` | ✓ | | ✓ | — | PK | 引落指示ID |
| `payer_account` | `CHAR(13)` | | accounts | ✓ | — | `idx_ad_payer` | 支払人口座 |
| `payee_name` | `VARCHAR(80)` | | | ✓ | — | — | 受取人名 |
| `amount_jpy` | `BIGINT` | | | ✓ | — | — | 引落額 (円) |
| `frequency` | `CHAR(1)` | | | ✓ | `IN ('M','W','D')` | — | 頻度 |
| `next_due_date` | `DATE` | | | ✓ | — | `idx_ad_status_due` | 次回引落日 |
| `status` | `CHAR(2)` | | | ✓ | `IN ('AC','SP','TM')` | `idx_ad_status_due` | 引落ステータス |
| `last_attempt_date` | `DATE` | | | | — | — | 最終試行日 |
| `last_attempt_result` | `CHAR(2)` | | | | — | — | 最終試行結果 |
| `consecutive_failures` | `SMALLINT` | | | ✓ | `DEFAULT 0` | — | 連続失敗回数 |
| `created_ts` | `TIMESTAMP(0)` | | | ✓ | `DEFAULT NOW()` | — | 作成日時 |
| `updated_ts` | `TIMESTAMP(0)` | | | ✓ | `DEFAULT NOW()` | — | 更新日時 |

---

#### 4.1.6 `batch_run` (バッチ実行)

| カラム | 型 | PK | FK | NOT NULL | CHECK | インデックス | 説明 |
|---|---|---|---|---|---|---|---|
| `batch_id` | `CHAR(14)` | ✓ | | ✓ | — | PK | バッチID |
| `business_date` | `DATE` | | | ✓ | — | `idx_br_bd` | 営業日 |
| `started_ts` | `TIMESTAMP(0)` | | | ✓ | — | — | 開始日時 |
| `completed_ts` | `TIMESTAMP(0)` | | | | — | — | 完了日時 |
| `status` | `CHAR(2)` | | | ✓ | `IN ('RN','OK','FL','AB')` | `idx_br_status` | バッチステータス |
| `current_step` | `VARCHAR(20)` | | | | — | — | 現在ステップ |
| `last_failed_step` | `VARCHAR(20)` | | | | — | — | 最終失敗ステップ |
| `txns_posted` | `INTEGER` | | | | — | — | 起票件数 |
| `interest_accounts` | `INTEGER` | | | | — | — | 利息対象件数 |
| `errors_count` | `INTEGER` | | | ✓ | `DEFAULT 0` | — | エラー件数 |
| `notes` | `TEXT` | | | | — | — | 備考 |

---

#### 4.1.7 `audit_log` (監査ログ)

- **パーティション**: `PARTITION BY RANGE (business_date)` — 月次パーティション
- **初期パーティション**: `audit_log_202606` (2026-06-01 ～ 2026-07-01), `audit_log_202607` (2026-07-01 ～ 2026-08-01)
- **シーケンス**: `audit_id_seq`

| カラム | 型 | PK | FK | NOT NULL | CHECK | インデックス | 説明 |
|---|---|---|---|---|---|---|---|
| `audit_id` | `BIGINT` | ✓ (第2) | | ✓ | `DEFAULT nextval('audit_id_seq')` | PK | 監査ID |
| `business_date` | `DATE` | ✓ (第1) | | ✓ | — | PK | 営業日 (パーティションキー) |
| `system_ts` | `TIMESTAMP(0)` | | | ✓ | `DEFAULT NOW()` | — | システムタイムスタンプ |
| `subsystem` | `VARCHAR(30)` | | | ✓ | — | `idx_audit_log_subsystem` | サブシステム名 |
| `action` | `VARCHAR(50)` | | | ✓ | — | `idx_audit_log_action` | アクション |
| `actor` | `VARCHAR(30)` | | | ✓ | — | — | 操作者 |
| `target_type` | `VARCHAR(20)` | | | ✓ | — | `idx_audit_log_target` | 対象種別 |
| `target_id` | `VARCHAR(20)` | | | ✓ | — | `idx_audit_log_target` | 対象ID |
| `payload_json` | `JSONB` | | | | — | `idx_audit_log_payload_acct` | ペイロード (JSON) |
| `severity` | `CHAR(1)` | | | ✓ | `IN ('I','W','E','C')` | — | 重大度 |
| `schema_version` | `VARCHAR(10)` | | | ✓ | `DEFAULT '1.0'` | — | スキーマバージョン |

---

### 4.2 ISAM トランザクションファイル

#### 4.2.1 取引-ready ファイル (`txn-ready.dat`)

- **FD**: `fd-txn-ready.cpy`
- **レコード長**: 600 バイト (可変長レコードの最大長)
- **構造**: `TXN-READY-REC PIC X(600)` — フラット。実行時に再解析
- **書込**: 11-TXSM (ソート済み出力)
- **読取**: 12-TXNPOST

#### 4.2.2 取引-ready-d-temp ファイル (`txn-ready-d-temp.dat`)

- **FD**: `fd-txn-ready-d-temp.cpy`
- **レコード長**: 600 バイト
- **書込**: 11-TXSM (ソート前一時)
- **読取**: 11-TXSM

#### 4.2.3 取引-sorted ファイル (`txn-sorted.dat`)

- **FD**: `fd-txn-sorted.cpy`
- **レコード長**: 600 バイト
- **書込**: 11-TXSM
- **読取**: 11-TXSM (マージ入力)

#### 4.2.4 取引-valid ファイル (`txn-valid.dat`)

- **FD**: `fd-txn-valid.cpy`
- **レコード長**: 600 バイト
- **書込**: 10-TXVAL
- **読取**: 11-TXSM

#### 4.2.5 取引-error ファイル (`txn-error.dat`)

- **FD**: `fd-txn-error.cpy`
- **レコード長**: 694 バイト

| フィールド | PIC | 長さ | 説明 |
|---|---|---|---|
| `TEF-ORIG-SEQ` | `PIC 9(10)` | 10 | 元シーケンス番号 |
| `TEF-REASON-CODE` | `PIC X(4)` | 4 | エラーコード |
| `TEF-REASON-TEXT` | `PIC X(80)` | 80 | エラー文言 |
| `TEF-ORIG-REC` | `PIC X(600)` | 600 | 元レコード |

- **書込**: 10-TXVAL, 11-TXSM
- **読取**: 22-OPS (エラー集計)

#### 4.2.6 取引デコードファイル (`txn-decoded.dat`)

- **FD**: `fd-txn-decoded.cpy`
- **レコード長**: 600 バイト
- **書込**: 19-INTI (外部取引入力)
- **読取**: 10-TXVAL

#### 4.2.7 リコン前日ファイル (`txn-recon-prev.dat`)

- **FD**: `fd-txn-recon-prev.cpy`
- **レコード長**: 600 バイト
- **書込**: 11-TXSM (前日実行時)
- **読取**: 11-TXSM (照合)

#### 4.2.8 リコン保留ファイル (`txn-recon-defer.dat`)

- **FD**: `fd-txn-recon-defer.cpy`
- **レコード長**: 600 バイト
- **書込**: 12-TXNPOST
- **読取**: 12-TXNPOST (翌日再処理)

#### 4.2.9 チェックポイントファイル (`txn-checkpoint.dat`)

- **FD**: `fd-txn-checkpoint.cpy`
- **レコード長**: 20 バイト

| フィールド | PIC | 長さ | 説明 |
|---|---|---|---|
| `TC-LAST-SEQ` | `PIC 9(10)` | 10 | 最終処理シーケンス |
| `TC-CHECKSUM` | `PIC X(8)` | 8 | チェックサム |
| `TC-SENTINEL` | `PIC X(2)` | 2 | センチネル |

- **書込/読取**: 10-TXVAL, 11-TXSM, 12-TXNPOST, 13-IACR, 14-IPST, 15-AD

#### 4.2.10 ドーマンシー修復ファイル (`dormancy-repair.dat`)

- **FD**: `fd-dormancy-repair.cpy`
- **レコード長**: 133 バイト

| フィールド | PIC | 長さ | 説明 |
|---|---|---|---|
| `DRMR-ACCT-NUMBER` | `PIC 9(13)` | 13 | 口座番号 |
| `DRMR-BUSINESS-DATE` | `PIC 9(8)` | 8 | 営業日 |
| `DRMR-ATTEMPT-COUNT` | `PIC 9(2)` | 2 | 試行回数 |
| `DRMR-REASON-TEXT` | `PIC X(80)` | 80 | 理由 |
| `DRMR-SOURCE-BATCH-ID` | `PIC X(14)` | 14 | 元バッチID |
| `DRMR-CREATED-TS` | `PIC X(19)` | 19 | 作成タイムスタンプ |

- **書込**: 12-TXNPOST
- **読取**: 22-OPS

#### 4.2.11 利息サマリファイル (`iacr-summary.dat`)

- **FD**: `fd-iacr-summary.cpy`
- **レコード長**: 80 バイト

| フィールド | PIC | 長さ | 説明 | 88-level |
|---|---|---|---|---|
| `IS-ROW-TYPE` | `PIC X(1)` | 1 | 行種別 | `"P"` 商品別 / `"G"` 合計 / `"C"` カウンタ |
| `IS-PRODUCT-CODE` | `PIC X(3)` | 3 | 商品コード | — |
| `IS-COUNT` | `PIC 9(7)` | 7 | 件数 | — |
| `IS-TOTAL-JPY` | `PIC 9(15)` | 15 | 合計金額 | — |
| `IS-AVG-RATE-MICRO` | `PIC 9(7)` | 7 | 平均利率 (マイクロ) | — |
| `IS-FILLER` | `PIC X(47)` | 47 | 予約 | — |

- **書込**: 13-IACR
- **読取**: 14-IPST, 22-OPS


## 5. インターフェースデータ定義 (copybook)

> 以下、各サブシステムの API copybook に入力/出力フィールドを列挙。
> フィールド名・PIC・88-level 条件名・意味を記載。
> 読み書きプログラムは各サブシステムのバッチ/オンライン処理。

### 5.01 カレンダー (01-CALENDAR) — `cal-api.cpy`

**入力** `CAL-INPUT`

| フィールド | PIC | 説明 |
|---|---|---|
| `CAL-INPUT-DATE` | `PIC 9(8)` | 照会日付 (YYYYMMDD) |

**出力** `CAL-OUTPUT`

| フィールド | PIC | 説明 | 88-level |
|---|---|---|---|
| `CAL-STATUS` | `PIC 9(2)` | 処理ステータス | `00` OK / `04` NOT-FOUND / `08` INVALID-DATE / `12` CACHE-FAIL / `16` FATAL |
| `CAL-OUTPUT-DAY-TYPE` | `PIC X(1)` | 日タイプ | `"B"` 営業日 / `"H"` 祝日 / `"W"` 週末 |
| `CAL-OUTPUT-HOLIDAY-NAME` | `PIC X(40)` | 祝日名 | — |
| `CAL-OUTPUT-NEXT-DATE` | `PIC 9(8)` | 翌営業日 | — |

---

### 5.02 支店 (02-BRANCH) — `br-api.cpy`

**入力** `BR-INPUT`

| フィールド | PIC | 説明 | 88-level |
|---|---|---|---|
| `BR-IN-CODE` | `PIC X(3)` | 支店コード | — |
| `BR-IN-REGION` | `PIC X(20)` | 地域名 (一覧用) | — |
| `BR-IN-OP` | `PIC X(1)` | 操作種別 | `"L"` 照会 / `"R"` 地域一覧 / `"A"` 全件 |

**出力** `BR-OUTPUT`

| フィールド | PIC | 説明 | 88-level |
|---|---|---|---|
| `BR-OUT-STATUS` | `PIC 9(2)` | 処理ステータス | `00` OK / `04` NOT-FOUND / `08` INVALID / `10` EOF / `16` FATAL |
| `BR-OUT-CODE` | `PIC X(3)` | 支店コード | — |
| `BR-OUT-NAME-KANJI` | `PIC X(40)` | 支店名 (漢字) | — |
| `BR-OUT-NAME-KANA` | `PIC X(40)` | 支店名 (カナ) | — |
| `BR-OUT-REGION` | `PIC X(20)` | 地域名 | — |
| `BR-OUT-STATUS-CODE` | `PIC X(1)` | 支店ステータス | — |

---

### 5.03 顧客 (03-CUSTOMER) — `cust-api.cpy`

**入力** `CUST-INPUT`

| フィールド | PIC | 説明 | 88-level |
|---|---|---|---|
| `CUST-IN-ID` | `PIC 9(10)` | 顧客番号 | — |
| `CUST-IN-KANA` | `PIC X(50)` | カナ氏名 (検索用) | — |
| `CUST-IN-PHONE` | `PIC X(15)` | 電話番号 (検索用) | — |
| `CUST-IN-OP` | `PIC X(1)` | 操作種別 | `"L"` 照会 / `"K"` カナ検索 / `"P"` 電話検索 / `"A"` 全件 / `" "` 次件 |

**出力** `CUST-OUTPUT`

| フィールド | PIC | 説明 | 88-level |
|---|---|---|---|
| `CUST-OUT-STATUS` | `PIC 9(2)` | 処理ステータス | `00` OK / `04` NOT-FOUND / `08` INVALID / `10` EOF / `16` FATAL |
| `CUST-OUT-ID` | `PIC 9(10)` | 顧客番号 | — |
| `CUST-OUT-KANA` | `PIC X(50)` | カナ氏名 | — |
| `CUST-OUT-KANJI` | `PIC X(60)` | 漢字氏名 | — |
| `CUST-OUT-PHONE` | `PIC X(15)` | 電話番号 | — |
| `CUST-OUT-ADDRESS` | `PIC X(200)` | 住所 | — |
| `CUST-OUT-OPENED` | `PIC 9(8)` | 開設日 | — |
| `CUST-OUT-STATUS-CODE` | `PIC X(1)` | 顧客ステータス | — |

**開設入力** `CUST-OPEN-INPUT`

| フィールド | PIC | 説明 |
|---|---|---|
| `COI-KANA` | `PIC X(50)` | カナ氏名 |
| `COI-KANJI` | `PIC X(60)` | 漢字氏名 |
| `COI-PHONE` | `PIC X(15)` | 電話番号 |
| `COI-ADDRESS` | `PIC X(200)` | 住所 |
| `COI-BUSINESS-DATE` | `PIC 9(8)` | 営業日 |

**ステータス変更入力** `CUST-STATUS-CHANGE-INPUT`

| フィールド | PIC | 説明 |
|---|---|---|
| `CSI-ID` | `PIC 9(10)` | 顧客番号 |
| `CSI-NEW-STATUS` | `PIC X(1)` | 新ステータス |
| `CSI-BUSINESS-DATE` | `PIC 9(8)` | 営業日 |

---

### 5.05 商品 (05-PRODUCT) — `prod-api.cpy`

**入力** `PROD-INPUT`

| フィールド | PIC | 説明 |
|---|---|---|
| `PRD-IN-CODE` | `PIC X(3)` | 商品コード |

**出力** `PROD-OUTPUT`

| フィールド | PIC | 説明 | 88-level |
|---|---|---|---|
| `PRD-OUT-STATUS` | `PIC 9(2)` | 処理ステータス | `00` OK / `04` NOT-FOUND / `16` FATAL |
| `PRD-OUT-CODE` | `PIC X(3)` | 商品コード | — |
| `PRD-OUT-NAME` | `PIC X(40)` | 商品名 | — |
| `PRD-OUT-TYPE` | `PIC X(1)` | 商品タイプ | `"S"` 普通 / `"C"` 当座 / `"T"` 定期 |
| `PRD-OUT-INTEREST-TYPE` | `PIC X(1)` | 利息タイプ | — |
| `PRD-OUT-ALLOW-OVD` | `PIC X(1)` | 当座貸越許可 | — |
| `PRD-OUT-TERM-DAYS` | `PIC 9(4)` | 期間 (日) | — |
| `PRD-OUT-EFF-FROM` | `PIC 9(8)` | 有効開始日 | — |
| `PRD-OUT-EFF-TO` | `PIC 9(8)` | 有効終了日 | — |

---

### 5.06 金利 (06-INTERESTRATE) — `irate-api.cpy`

**入力** `IRATE-INPUT`

| フィールド | PIC | 説明 |
|---|---|---|
| `IR-IN-PRODUCT` | `PIC X(3)` | 商品コード |
| `IR-IN-TIER` | `PIC 9(2)` | ティア番号 |
| `IR-IN-EFFECTIVE` | `PIC 9(8)` | 照会日 (適用開始日) |

**出力** `IRATE-OUTPUT`

| フィールド | PIC | 説明 | 88-level |
|---|---|---|---|
| `IR-OUT-STATUS` | `PIC 9(2)` | 処理ステータス | `00` OK / `04` NOT-FOUND / `16` FATAL |
| `IR-OUT-RATE-MICRO` | `PIC 9(7)` | 利率 (マイクロ単位) | — |
| `IR-OUT-EFF-FROM` | `PIC 9(8)` | 適用開始日 | — |
| `IR-OUT-EFF-TO` | `PIC 9(8)` | 適用終了日 | — |

---

### 5.07 手数料 (07-FEESCHEDULE) — `fs-api.cpy`

**入力** `FS-INPUT`

| フィールド | PIC | 説明 | 88-level |
|---|---|---|---|
| `FS-IN-CATEGORY` | `PIC 9(2)` | 手数料カテゴリ | `10` 入金 / `20` 出金 / `30` 振込 / `40` 電信 |
| `FS-IN-TIER` | `PIC 9(2)` | ティア番号 | — |
| `FS-IN-EFFECTIVE` | `PIC 9(8)` | 照会日 | — |

**出力** `FS-OUTPUT`

| フィールド | PIC | 説明 | 88-level |
|---|---|---|---|
| `FS-OUT-STATUS` | `PIC 9(2)` | 処理ステータス | `00` OK / `04` NOT-FOUND / `16` FATAL |
| `FS-OUT-FEE-JPY` | `PIC S9(9)` | 手数料額 (円) | — |
| `FS-OUT-EFF-TO` | `PIC 9(8)` | 適用終了日 | — |

---

### 5.08 口座 (08-ACCOUNT) — `acct-api.cpy`

**照会入力** `ACCT-LOOKUP-INPUT`

| フィールド | PIC | 説明 |
|---|---|---|
| `ACCT-LOOKUP-NUMBER` | `PIC 9(13)` | 口座番号 |

**照会出力** `ACCT-LOOKUP-OUTPUT`

| フィールド | PIC | 説明 | 88-level |
|---|---|---|---|
| `ACCT-LO-NUMBER` | `PIC 9(13)` | 口座番号 | — |
| `ACCT-LO-CUST-ID` | `PIC 9(10)` | 顧客番号 | — |
| `ACCT-LO-PRODUCT-CODE` | `PIC 9(3)` | 商品コード | — |
| `ACCT-LO-BRANCH-CODE` | `PIC 9(3)` | 支店コード | — |
| `ACCT-LO-OPENED-DATE` | `PIC 9(8)` | 開設日 | — |
| `ACCT-LO-CLOSED-DATE` | `PIC 9(8)` | 解約日 | — |
| `ACCT-LO-STATUS` | `PIC X(1)` | 口座ステータス | `"P"` 申請 / `"A"` 有効 / `"D"` ドーマン / `"S"` 停止 / `"C"` 解約 / `"R"` 再開 |
| `ACCT-LO-OVERDRAFT-LIMIT` | `PIC S9(15) COMP-3` | 貸越限度額 | — |
| `ACCT-LO-TERM-DAYS` | `PIC 9(4)` | 期間 (日) | — |
| `ACCT-LO-DORMANCY-DATE` | `PIC 9(8)` | ドーマンシー日 | — |
| `ACCT-LO-CREATED-TS` | `PIC 9(14)` | 作成TS | — |
| `ACCT-LO-UPDATED-TS` | `PIC 9(14)` | 更新TS | — |

**照会ステータス** `ACCT-LOOKUP-STATUS PIC X(2)`

| 88-level | 値 | 意味 |
|---|---|---|
| `ACCT-LOOKUP-OK` | `"00"` | 正常 |
| `ACCT-LOOKUP-NOT-FOUND` | `"04"` | 未検出 |
| `ACCT-LOOKUP-INVALID-INPUT` | `"08"` | 入力不正 |
| `ACCT-LOOKUP-IO-FAIL` | `"12"` | I/O 失敗 |
| `ACCT-LOOKUP-FATAL` | `"16"` | 致命的 |

**存在確認** `ACCT-EXISTS-INPUT` / `ACCT-EXISTS-OUTPUT`

| フィールド | PIC | 説明 | 88-level |
|---|---|---|---|
| `ACCT-EXISTS-NUMBER` | `PIC 9(13)` | 口座番号 (入力) | — |
| `ACCT-EXISTS-FOUND` | `PIC X(1)` | 存在フラグ | `"Y"` あり / `"N"` なし |
| `ACCT-EXISTS-STATUS-CODE` | `PIC X(1)` | 口座ステータス | — |
| `ACCT-EXISTS-PRODUCT-CODE` | `PIC 9(3)` | 商品コード | — |
| `ACCT-EXISTS-ACTIVE-FLAG` | `PIC X(1)` | 有効フラグ | `"Y"` 有効 / `"N"` 無効 |

**顧客別照会** `ACCT-LOOKUP-BY-CUST-INPUT` / `OUTPUT`

| フィールド | PIC | 説明 |
|---|---|
| `LOOKUP-BY-CUST-CUST-ID` | `PIC 9(10)` | 顧客番号 |
| `LOOKUP-BY-CUST-MAX` | `PIC 9(2) COMP-3` | 最大取得件数 |
| `LOOKUP-BY-CUST-START-AFTER` | `PIC 9(13)` | カーソル (ページング) |
| `LOOKUP-BY-CUST-COUNT` | `PIC 9(2) COMP-3` | 返却件数 |
| `LOOKUP-BY-CUST-MORE` | `PIC X(1)` | 続きフラグ |
| `LOOKUP-BY-CUST-LAST-ACCT` | `PIC 9(13)` | 最終口座番号 |
| `LOOKUP-BY-CUST-RECORDS` | `PIC X(100) OCCURS 20` | レコード配列 |

**ドーマンシー更新** `ACCT-UPDATE-DORMANCY-INPUT` / `OUTPUT`

| フィールド | PIC | 説明 | 88-level |
|---|---|---|---|
| `UPDATE-DORMANCY-ACCT-NUMBER` | `PIC 9(13)` | 口座番号 | — |
| `UPDATE-DORMANCY-NEW-DATE` | `PIC 9(8)` | 新ドーマンシー日 | — |
| `UPDATE-DORMANCY-PREV-DATE` | `PIC 9(8)` | 旧ドーマンシー日 | — |
| `UPDATE-DORMANCY-WAS-NOOP` | `PIC X(1)` | 無操作フラグ | `"Y"` 無操作 / `"N"` 更新済 |

---

### 5.09 口座ライフサイクル (09-ACCOUNTLIFECYCLE) — `alc-api.cpy`

**開設** `ALC-OPEN-INPUT` / `ALC-OPEN-OUTPUT`

| フィールド | PIC | 方向 | 説明 |
|---|---|---|---|
| `ALC-OPEN-CUST-ID` | `PIC 9(10)` | IN | 顧客番号 |
| `ALC-OPEN-PRODUCT-CODE` | `PIC 9(3)` | IN | 商品コード |
| `ALC-OPEN-BRANCH-CODE` | `PIC 9(3)` | IN | 支店コード |
| `ALC-OPEN-OPENED-DATE` | `PIC 9(8)` | IN | 開設日 |
| `ALC-OPEN-OVERDRAFT-LIMIT` | `PIC S9(15) COMP-3` | IN | 貸越限度額 |
| `ALC-OPEN-TERM-DAYS` | `PIC 9(4)` | IN | 期間 (日) |
| `ALC-OPEN-ACCT-NUMBER` | `PIC 9(13)` | OUT | 新規口座番号 |

**ステータス変更** `ALC-CHANGE-INPUT`

| フィールド | PIC | 説明 | 88-level |
|---|---|---|---|
| `ALC-CHANGE-ACCT-NUMBER` | `PIC 9(13)` | 口座番号 | — |
| `ALC-CHANGE-ACTION-CODE` | `PIC X(2)` | アクション | `"AC"` 有効化 / `"CN"` 取消 / `"SU"` 停止 / `"LS"` 解除 / `"CL"` 解約 / `"FC"` 強制解約 |
| `ALC-CHANGE-REASON-TEXT` | `PIC X(80)` | 理由 | — |
| `ALC-CHANGE-BUSINESS-DATE` | `PIC 9(8)` | 営業日 | — |

**ドーマンシー/再開スキャン**

| フィールド | PIC | 説明 |
|---|---|
| `ALC-DORMANCY-BUSINESS-DATE` | `PIC 9(8)` | 営業日 |
| `ALC-DORMANCY-TRANSITIONED` | `PIC 9(6)` | 移行件数 |
| `ALC-DORMANCY-SKIPPED` | `PIC 9(6)` | スキップ件数 |
| `ALC-REACT-BUSINESS-DATE` | `PIC 9(8)` | 営業日 |
| `ALC-REACT-TRANSITIONED` | `PIC 9(6)` | 移行件数 |
| `ALC-REACT-SKIPPED` | `PIC 9(6)` | スキップ件数 |

---


### 5.10 取引検証 (10-TXNVALIDATE) — `tx-val-api.cpy`

**バッチ入力** `TXVAL-BATCH-INPUT`

| フィールド | PIC | 説明 |
|---|---|---|
| `TXVAL-IN-BATCH-ID` | `PIC X(14)` | バッチID |
| `TXVAL-IN-BUSINESS-DATE` | `PIC 9(8)` | 営業日 |
| `TXVAL-IN-INPUT-FILENAME` | `PIC X(80)` | 入力ファイル |
| `TXVAL-IN-VALID-FILENAME` | `PIC X(80)` | 有効ファイル |
| `TXVAL-IN-ERROR-FILENAME` | `PIC X(80)` | エラーファイル |
| `TXVAL-IN-CHECKPOINT-FILENAME` | `PIC X(80)` | チェックポイント |

**バッチ出力** `TXVAL-BATCH-OUTPUT`

| フィールド | PIC | 説明 | 88-level |
|---|---|---|---|
| `TXVAL-BATCH-STATUS` | `PIC X(2)` | 処理ステータス | `00` OK / `04` PARTIAL-REJECT / `08` INVALID / `12` IO-FAIL / `16` FATAL |
| `TXVAL-OUT-PROCESSED` | `PIC 9(7)` | 処理件数 | — |
| `TXVAL-OUT-VALIDATED` | `PIC 9(7)` | 検証OK件数 | — |
| `TXVAL-OUT-REJECTED` | `PIC 9(7)` | 却下件数 | — |
| `TXVAL-OUT-PRI-E001` ～ `E019`, `E099` | `PIC 9(7)` | 一次エラー別件数 | — |
| `TXVAL-OUT-OCC-E001` ～ `E019`, `E099` | `PIC 9(7)` | 二次エラー別件数 | — |

**チェックポイント復旧** `TXVAL-CKPT-RECOVER-INPUT` / `OUTPUT`

| フィールド | PIC | 説明 | 88-level |
|---|---|---|---|
| `TXVAL-CR-IN-FILENAME` | `PIC X(80)` | チェックポイントファイル | — |
| `TXVAL-CR-STATUS` | `PIC X(2)` | 復旧ステータス | `00` FOUND / `04` NO-CHECKPOINT / `12` CORRUPT / `16` FATAL |
| `TXVAL-CR-OUT-LAST-SEQ` | `PIC 9(10)` | 最終シーケンス | — |

---

### 5.11 取引ソート/マージ (11-TXNSORTMERGE) — `tx-sm-api.cpy`

**ソート** `TXSM-SORT-INPUT` / `TXSM-SORT-OUTPUT`

| フィールド | PIC | 方向 | 説明 | 88-level |
|---|---|---|---|---|
| `TXSM-SI-BATCH-ID` | `PIC X(14)` | IN | バッチID | — |
| `TXSM-SI-BUSINESS-DATE` | `PIC 9(8)` | IN | 営業日 | — |
| `TXSM-SI-INPUT-FILENAME` | `PIC X(80)` | IN | 入力ファイル | — |
| `TXSM-SI-OUTPUT-FILENAME` | `PIC X(80)` | IN | 出力ファイル | — |
| `TXSM-SI-CHECKPOINT-FILENAME` | `PIC X(80)` | IN | チェックポイント | — |
| `TXSM-SO-STATUS` | `PIC X(2)` | OUT | 処理ステータス | `00` OK / `04` PARTIAL / `08` INVALID / `12` IO-FAIL / `16` FATAL |
| `TXSM-SO-RECORDS-PROCESSED` | `PIC 9(7)` | OUT | 処理件数 | — |
| `TXSM-SO-RECORDS-SORTED` | `PIC 9(7)` | OUT | ソート件数 | — |
| `TXSM-SO-CTRL-TOTAL-MATCH` | `PIC X(1)` | OUT | 制御合計一致 | — |
| `TXSM-SO-AMOUNT-SUM` | `PIC 9(20)` | OUT | 金額合計 | — |

**マージ** `TXSM-MERGE-INPUT` / `TXSM-MERGE-OUTPUT`

| フィールド | PIC | 方向 | 説明 | 88-level |
|---|---|---|---|---|
| `TXSM-MI-BATCH-ID` | `PIC X(14)` | IN | バッチID | — |
| `TXSM-MI-BUSINESS-DATE` | `PIC 9(8)` | IN | 営業日 | — |
| `TXSM-MI-SORTED-FILENAME` | `PIC X(80)` | IN | ソート済ファイル | — |
| `TXSM-MI-RECON-PREV-FILENAME` | `PIC X(80)` | IN | 前日リコンファイル | — |
| `TXSM-MI-READY-FILENAME` | `PIC X(80)` | IN | ready ファイル | — |
| `TXSM-MI-ERROR-FILENAME` | `PIC X(80)` | IN | エラーファイル | — |
| `TXSM-MI-CHECKPOINT-FILENAME` | `PIC X(80)` | IN | チェックポイント | — |
| `TXSM-MI-TEMP-FILENAME` | `PIC X(80)` | IN | 一時ファイル | — |
| `TXSM-MO-STATUS` | `PIC X(2)` | OUT | 処理ステータス | `00` OK / `04` PARTIAL / `08` INVALID / `12` IO-FAIL / `16` FATAL |
| `TXSM-MO-RECORDS-SORTED-IN` | `PIC 9(7)` | OUT | ソート済入力件数 | — |
| `TXSM-MO-RECORDS-RECON-IN` | `PIC 9(7)` | OUT | リコン入力件数 | — |
| `TXSM-MO-RECORDS-MERGED-OUT` | `PIC 9(7)` | OUT | マージ出力件数 | — |
| `TXSM-MO-DUPLICATE-RECORDS` | `PIC 9(5)` | OUT | 重複件数 | — |
| `TXSM-MO-DUPLICATE-PAIRS` | `PIC 9(5)` | OUT | 重複ペア数 | — |
| `TXSM-MO-SORT-VIOLATIONS` | `PIC 9(5)` | OUT | ソート違反件数 | — |
| `TXSM-MO-RECON-PRESENT-FLAG` | `PIC X(1)` | OUT | リコン有無 | — |
| `TXSM-MO-AMOUNT-SUM` | `PIC 9(20)` | OUT | 金額合計 | — |

---

### 5.12 取引起票 (12-TXNPOST) — `tx-post-api.cpy`

**実行** `TXPOST-RUN-INPUT` / `TXPOST-RUN-OUTPUT`

| フィールド | PIC | 方向 | 説明 | 88-level |
|---|---|---|---|---|
| `TXPR-IN-BATCH-ID` | `PIC X(14)` | IN | バッチID | — |
| `TXPR-IN-BUSINESS-DATE` | `PIC 9(8)` | IN | 営業日 | — |
| `TXPR-IN-READY-FILENAME` | `PIC X(80)` | IN | ready ファイル | — |
| `TXPR-IN-ERROR-FILENAME` | `PIC X(80)` | IN | エラーファイル | — |
| `TXPR-IN-RECON-DEFER-FILENAME` | `PIC X(80)` | IN | リコン保留ファイル | — |
| `TXPR-IN-CHECKPOINT-FILENAME` | `PIC X(80)` | IN | チェックポイント | — |
| `TXPR-IN-DORMANCY-FILENAME` | `PIC X(80)` | IN | ドーマンシー修復ファイル | — |
| `TXPR-STATUS` | `PIC X(2)` | OUT | 処理ステータス | `00` OK / `04` PARTIAL-RECON / `08` INVALID / `12` IO-FAIL / `16` FATAL |
| `TXPR-RECORDS-READ` | `PIC 9(7)` | OUT | 読取件数 | — |
| `TXPR-RECORDS-ATTEMPTED` | `PIC 9(7)` | OUT | 起票試行件数 | — |
| `TXPR-RECORDS-POSTED` | `PIC 9(7)` | OUT | 起票成功件数 | — |
| `TXPR-ALREADY-POSTED-SKIPPED` | `PIC 9(7)` | OUT | 重複スキップ件数 | — |
| `TXPR-HARD-REJECTED` | `PIC 9(7)` | OUT | ハード却下件数 | — |
| `TXPR-RECON-DEFERRED` | `PIC 9(7)` | OUT | リコン保留件数 | — |
| `TXPR-IN-DOUBT-RESOLVED` | `PIC 9(7)` | OUT | 疑義解決件数 | — |
| `TXPR-DORMANCY-DEFERRED` | `PIC 9(7)` | OUT | ドーマンシー保留件数 | — |
| `TXPR-DURATION-SEC` | `PIC 9(5)` | OUT | 処理時間 (秒) | — |

**取消** `TXPOST-REVERSE-INPUT` / `TXPOST-REVERSE-OUTPUT`

| フィールド | PIC | 方向 | 説明 | 88-level |
|---|---|---|---|---|
| `TXPV-ORIG-TXN-ID` | `PIC X(18)` | IN | 元取引ID | — |
| `TXPV-REVERSAL-REASON` | `PIC X(80)` | IN | 取消理由 | — |
| `TXPV-OPERATOR-ID` | `PIC X(20)` | IN | オペレータID | — |
| `TXPV-STATUS` | `PIC X(2)` | OUT | 処理ステータス | `00` OK / `04` ORIG-NOT-FOUND / `08` INVALID / `12` IO-FAIL / `16` FATAL |
| `TXPV-NEW-RV-TXN-ID` | `PIC X(18)` | OUT | 取消取引ID | — |

---

### 5.13 利息計算 (13-INTERESTACCRUAL) — `iacr-api.cpy`

**実行** `IACR-RUN-INPUT` / `IACR-RUN-OUTPUT`

| フィールド | PIC | 方向 | 説明 | 88-level |
|---|---|---|---|---|
| `IACR-RUN-BATCH-ID` | `PIC X(14)` | IN | バッチID | — |
| `IACR-RUN-BUSINESS-DATE` | `PIC 9(8)` | IN | 営業日 | — |
| `IACR-RUN-SUMMARY-FILENAME` | `PIC X(80)` | IN | サマリファイル | — |
| `IACR-RUN-CHECKPOINT-FILENAME` | `PIC X(80)` | IN | チェックポイント | — |
| `IACR-RUN-STATUS` | `PIC X(2)` | OUT | 処理ステータス | `00` OK / `04` PARTIAL / `08` INVALID / `12` IO-FAIL / `16` FATAL |
| `IACR-OUT-ACCOUNTS-SCANNED` | `PIC 9(7)` | OUT | スキャン件数 | — |
| `IACR-OUT-ACCRUALS-INSERTED` | `PIC 9(7)` | OUT | 登録件数 | — |
| `IACR-OUT-INELIGIBLE-STATE` | `PIC 9(7)` | OUT | 状態不適格件数 | — |
| `IACR-OUT-INELIGIBLE-PROD` | `PIC 9(7)` | OUT | 商品不適格件数 | — |
| `IACR-OUT-INELIGIBLE-BALANCE` | `PIC 9(7)` | OUT | 残高不適格件数 | — |
| `IACR-OUT-INELIGIBLE-RATE` | `PIC 9(7)` | OUT | 金利不適格件数 | — |
| `IACR-OUT-ALREADY-ACCRUED` | `PIC 9(7)` | OUT | 計算済件数 | — |
| `IACR-OUT-SYSTEM-SKIPPED` | `PIC 9(7)` | OUT | システムスキップ件数 | — |
| `IACR-OUT-DURATION-SEC` | `PIC 9(5)` | OUT | 処理時間 (秒) | — |

**レポート** `IACR-REPORT-INPUT` / `IACR-REPORT-OUTPUT`

| フィールド | PIC | 方向 | 説明 | 88-level |
|---|---|---|---|---|
| `IACR-RPT-BUSINESS-DATE` | `PIC 9(8)` | IN | 営業日 | — |
| `IACR-RPT-SUMMARY-FILENAME` | `PIC X(80)` | IN | サマリファイル | — |
| `IACR-RPT-REPORT-FILENAME` | `PIC X(80)` | IN | レポートファイル | — |
| `IACR-RPT-STATUS` | `PIC X(2)` | OUT | 処理ステータス | `00` OK / `04` CONSERVATION-WARN / `08` INVALID / `12` IO-FAIL / `16` FATAL |
| `IACR-RPT-PRODUCTS-REPORTED` | `PIC 9(2)` | OUT | 商品数 | — |
| `IACR-RPT-TOTAL-ACCRUALS` | `PIC 9(7)` | OUT | 合計計算件数 | — |
| `IACR-RPT-TOTAL-ACCRUED-JPY` | `PIC S9(15) COMP-3` | OUT | 合計利息額 | — |
| `IACR-RPT-AC-COUNT` | `PIC 9(7)` | OUT | AC 件数 | — |
| `IACR-RPT-PT-COUNT` | `PIC 9(7)` | OUT | PT 件数 | — |
| `IACR-RPT-GRAND-TOTAL` | `PIC 9(7)` | OUT | 総件数 | — |
| `IACR-RPT-CONSERVATION-PASS` | `PIC X(1)` | OUT | 保存則PASS | — |
| `IACR-RPT-DURATION-SEC` | `PIC 9(5)` | OUT | 処理時間 (秒) | — |

---

### 5.14 利息起票 (14-INTERESTPOST) — `ipst-api.cpy`

**実行** `IPST-RUN-INPUT` / `IPST-RUN-OUTPUT`

| フィールド | PIC | 方向 | 説明 | 88-level |
|---|---|---|---|---|
| `IPST-RUN-BATCH-ID` | `PIC X(14)` | IN | バッチID | — |
| `IPST-RUN-BUSINESS-DATE` | `PIC 9(8)` | IN | 営業日 | — |
| `IPST-RUN-SUMMARY-FILENAME` | `PIC X(80)` | IN | サマリファイル | — |
| `IPST-RUN-CHECKPOINT-FILENAME` | `PIC X(80)` | IN | チェックポイント | — |
| `IPST-RUN-STATUS` | `PIC X(2)` | OUT | 処理ステータス | `00` OK / `04` PARTIAL / `08` INVALID / `12` IO-FAIL / `16` FATAL |
| `IPST-OUT-ACCOUNTS-AGGREGATED` | `PIC 9(7)` | OUT | 集計件数 | — |
| `IPST-OUT-ACCOUNTS-POSTED` | `PIC 9(7)` | OUT | 起票件数 | — |
| `IPST-OUT-SKIPPED-CLOSED` | `PIC 9(7)` | OUT | 解約スキップ件数 | — |
| `IPST-OUT-SKIPPED-PRODUCT` | `PIC 9(7)` | OUT | 商品スキップ件数 | — |
| `IPST-OUT-SKIPPED-ALREADY` | `PIC 9(7)` | OUT | 起票済スキップ件数 | — |
| `IPST-OUT-SKIPPED-HELPER` | `PIC 9(7)` | OUT | ヘルパースキップ件数 | — |
| `IPST-OUT-AC-ROWS-CONSUMED` | `PIC 9(8)` | OUT | AC行消費件数 | — |
| `IPST-OUT-TOTAL-POSTED-JPY` | `PIC S9(15) COMP-3` | OUT | 起票合計額 | — |
| `IPST-OUT-DURATION-SEC` | `PIC 9(5)` | OUT | 処理時間 (秒) | — |

**レポート** `IPST-REPORT-INPUT` / `IPST-REPORT-OUTPUT`

| フィールド | PIC | 方向 | 説明 | 88-level |
|---|---|---|---|---|
| `IPST-RPT-BUSINESS-DATE` | `PIC 9(8)` | IN | 営業日 | — |
| `IPST-RPT-BATCH-ID` | `PIC X(14)` | IN | バッチID | — |
| `IPST-RPT-SUMMARY-FILENAME` | `PIC X(80)` | IN | サマリファイル | — |
| `IPST-RPT-REPORT-FILENAME` | `PIC X(80)` | IN | レポートファイル | — |
| `IPST-RPT-STATUS` | `PIC X(2)` | OUT | 処理ステータス | `00` OK / `04` CONSERVATION-WARN / `08` INVALID / `12` IO-FAIL / `16` FATAL |
| `IPST-RPT-PRODUCTS-REPORTED` | `PIC 9(2)` | OUT | 商品数 | — |
| `IPST-RPT-TOTAL-POSTED` | `PIC 9(7)` | OUT | 起票件数 | — |
| `IPST-RPT-TOTAL-POSTED-JPY` | `PIC S9(15) COMP-3` | OUT | 起票合計額 | — |
| `IPST-RPT-PT-ROW-COUNT` | `PIC 9(7)` | OUT | PT行件数 | — |
| `IPST-RPT-AC-REMAINING` | `PIC 9(7)` | OUT | AC残件数 | — |
| `IPST-RPT-CONSERVATION-PASS` | `PIC X(1)` | OUT | 保存則PASS | — |
| `IPST-RPT-DURATION-SEC` | `PIC 9(5)` | OUT | 処理時間 (秒) | — |

---

### 5.15 自動引落 (15-AUTODEBIT) — `ad-api.cpy`

**実行** `AD-RUN-INPUT` / `AD-RUN-OUTPUT`

| フィールド | PIC | 方向 | 説明 | 88-level |
|---|---|---|---|---|
| `AD-RUN-BATCH-ID` | `PIC X(14)` | IN | バッチID | — |
| `AD-RUN-BUSINESS-DATE` | `PIC 9(8)` | IN | 営業日 | — |
| `AD-RUN-FAILED-FILENAME` | `PIC X(80)` | IN | 失敗ファイル | — |
| `AD-RUN-CHECKPOINT-FILENAME` | `PIC X(80)` | IN | チェックポイント | — |
| `AD-RUN-SUMMARY-FILENAME` | `PIC X(80)` | IN | サマリファイル | — |
| `AD-RUN-STATUS` | `PIC X(2)` | OUT | 処理ステータス | `00` OK / `04` PARTIAL / `08` INVALID / `12` IO-FAIL / `16` FATAL |
| `AD-OUT-INSTRUCTIONS-DUE` | `PIC 9(7)` | OUT | 今回対象件数 | — |
| `AD-OUT-INSTRUCTIONS-POSTED` | `PIC 9(7)` | OUT | 引落成功件数 | — |
| `AD-OUT-FAILED-NF` | `PIC 9(7)` | OUT | 失敗 (資金不足) 件数 | — |
| `AD-OUT-FAILED-CL` | `PIC 9(7)` | OUT | 失敗 (解約) 件数 | — |
| `AD-OUT-FAILED-SU` | `PIC 9(7)` | OUT | 失敗 (停止) 件数 | — |
| `AD-OUT-SKIPPED-ALREADY` | `PIC 9(7)` | OUT | スキップ (済) 件数 | — |
| `AD-OUT-SKIPPED-HELPER` | `PIC 9(7)` | OUT | スキップ (ヘルパー) 件数 | — |
| `AD-OUT-AUTO-SUSPENDED` | `PIC 9(7)` | OUT | 自動停止件数 | — |
| `AD-OUT-AUTO-TERMINATED` | `PIC 9(7)` | OUT | 自動解約件数 | — |
| `AD-OUT-TOTAL-DEBITED-JPY` | `PIC S9(15) COMP-3` | OUT | 引落合計額 | — |
| `AD-OUT-DURATION-SEC` | `PIC 9(5)` | OUT | 処理時間 (秒) | — |

**レポート** `AD-REPORT-INPUT` / `AD-REPORT-OUTPUT`

| フィールド | PIC | 方向 | 説明 | 88-level |
|---|---|---|---|---|
| `AD-RPT-BUSINESS-DATE` | `PIC 9(8)` | IN | 営業日 | — |
| `AD-RPT-BATCH-ID` | `PIC X(14)` | IN | バッチID | — |
| `AD-RPT-SUMMARY-FILENAME` | `PIC X(80)` | IN | サマリファイル | — |
| `AD-RPT-REPORT-FILENAME` | `PIC X(80)` | IN | レポートファイル | — |
| `AD-RPT-FAILED-FILENAME` | `PIC X(80)` | IN | 失敗ファイル | — |
| `AD-RPT-STATUS` | `PIC X(2)` | OUT | 処理ステータス | `00` OK / `04` CONSERVATION-WARN / `08` INVALID / `12` IO-FAIL / `16` FATAL |
| `AD-RPT-TOTAL-INSTRUCTIONS` | `PIC 9(7)` | OUT | 指示総数 | — |
| `AD-RPT-TOTAL-OK-JPY` | `PIC S9(15) COMP-3` | OUT | 成功合計額 | — |
| `AD-RPT-TOTAL-FAILED-COUNT` | `PIC 9(7)` | OUT | 失敗件数 | — |
| `AD-RPT-SUSPENDED-COUNT` | `PIC 9(7)` | OUT | 停止件数 | — |
| `AD-RPT-CONSERVATION-PASS` | `PIC X(1)` | OUT | 保存則PASS | — |
| `AD-RPT-DURATION-SEC` | `PIC 9(5)` | OUT | 処理時間 (秒) | — |

---

### 5.16 手数料 (16-FEE) — `fee-api.cpy`

**課金** `FEE-CHARGE-INPUT` / `FEE-CHARGE-OUTPUT`

| フィールド | PIC | 方向 | 説明 | 88-level |
|---|---|---|---|---|
| `FEE-CHARGE-BATCH-ID` | `PIC X(14)` | IN | バッチID | — |
| `FEE-CHARGE-BUSINESS-DATE` | `PIC 9(8)` | IN | 営業日 | — |
| `FEE-CHARGE-SUMMARY-FILENAME` | `PIC X(80)` | IN | サマリファイル | — |
| `FEE-CHARGE-STATUS` | `PIC X(2)` | OUT | 処理ステータス | `00` OK / `04` PARTIAL / `08` INVALID / `12` IO-FAIL / `16` FATAL |
| `FEE-OUT-TXNS-SCANNED` | `PIC 9(7)` | OUT | スキャン件数 | — |
| `FEE-OUT-CHARGES-POSTED` | `PIC 9(7)` | OUT | 課金件数 | — |
| `FEE-OUT-SKIPPED-NO-FEE` | `PIC 9(7)` | OUT | 手数料なしスキップ | — |
| `FEE-OUT-SKIPPED-CLOSED` | `PIC 9(7)` | OUT | 解約スキップ | — |
| `FEE-OUT-SKIPPED-NSF` | `PIC 9(7)` | OUT | 残高不足スキップ | — |
| `FEE-OUT-SKIPPED-ALREADY` | `PIC 9(7)` | OUT | 課金済スキップ | — |
| `FEE-OUT-SKIPPED-HELPER` | `PIC 9(7)` | OUT | ヘルパースキップ | — |
| `FEE-OUT-TOTAL-FEE-JPY` | `PIC S9(15) COMP-3` | OUT | 手数料合計額 | — |
| `FEE-OUT-DURATION-SEC` | `PIC 9(5)` | OUT | 処理時間 (秒) | — |

**レポート** `FEE-REPORT-INPUT` / `FEE-REPORT-OUTPUT`

| フィールド | PIC | 方向 | 説明 | 88-level |
|---|---|---|---|---|
| `FEE-RPT-BUSINESS-DATE` | `PIC 9(8)` | IN | 営業日 | — |
| `FEE-RPT-BATCH-ID` | `PIC X(14)` | IN | バッチID | — |
| `FEE-RPT-SUMMARY-FILENAME` | `PIC X(80)` | IN | サマリファイル | — |
| `FEE-RPT-REPORT-FILENAME` | `PIC X(80)` | IN | レポートファイル | — |
| `FEE-RPT-STATUS` | `PIC X(2)` | OUT | 処理ステータス | `00` OK / `04` CONSERVATION-WARN / `08` INVALID / `12` IO-FAIL / `16` FATAL |
| `FEE-RPT-TOTAL-CHARGES` | `PIC 9(7)` | OUT | 課金件数 | — |
| `FEE-RPT-TOTAL-FEE-JPY` | `PIC S9(15) COMP-3` | OUT | 手数料合計額 | — |
| `FEE-RPT-FEE-REVENUE-BAL` | `PIC S9(15) COMP-3` | OUT | 手数料収入残高 | — |
| `FEE-RPT-CONSERVATION-PASS` | `PIC X(1)` | OUT | 保存則PASS | — |
| `FEE-RPT-DURATION-SEC` | `PIC 9(5)` | OUT | 処理時間 (秒) | — |

---

### 5.17 明細 (17-STATEMENT) — `stmt-api.cpy`

**入力** `STMT-INPUT`

| フィールド | PIC | 説明 | 88-level |
|---|---|---|---|
| `STMT-BATCH-ID` | `PIC X(14)` | バッチID | — |
| `STMT-BUSINESS-DATE` | `PIC 9(8)` | 営業日 | — |
| `STMT-MODE` | `PIC X(1)` | モード | `"D"` 日次 / `"M"` 月次 |
| `STMT-OUTPUT-FILENAME` | `PIC X(80)` | 出力ファイル | — |
| `STMT-SUMMARY-FILENAME` | `PIC X(80)` | サマリファイル | — |
| `STMT-SKIP-INACTIVE` | `PIC X(1)` | 非活動スキップ | `"Y"` スキップ / `"N"` 含む |

**出力** `STMT-OUTPUT`

| フィールド | PIC | 説明 | 88-level |
|---|---|---|---|
| `STMT-STATUS` | `PIC X(2)` | 処理ステータス | `00` OK / `04` PARTIAL / `08` INVALID / `12` IO-FAIL / `16` FATAL |
| `STMT-OUT-ACCOUNTS-PROCESSED` | `PIC 9(7)` | 処理件数 | — |
| `STMT-OUT-ACCOUNTS-EMPTY` | `PIC 9(7)` | 空明細件数 | — |
| `STMT-OUT-ACCOUNTS-SKIPPED` | `PIC 9(7)` | スキップ件数 | — |
| `STMT-OUT-LINES-WRITTEN` | `PIC 9(10)` | 出力行数 | — |
| `STMT-OUT-PAGES-WRITTEN` | `PIC 9(7)` | 出力ページ数 | — |
| `STMT-OUT-BYTES-WRITTEN` | `PIC 9(12)` | 出力バイト数 | — |
| `STMT-OUT-DURATION-SEC` | `PIC 9(5)` | 処理時間 (秒) | — |

---

### 5.18 照会 (18-INQUIRY) — `inq-api.cpy`

**入力** `INQ-INPUT`

| フィールド | PIC | 説明 | 88-level |
|---|---|---|---|
| `INQ-MODE` | `PIC X(1)` | モード | `"S"` 画面 / `"N"` 非画面 |
| `INQ-OPERATOR-USER` | `PIC X(32)` | オペレータ | — |
| `INQ-INITIAL-ACTION` | `PIC X(1)` | 初期アクション | — |
| `INQ-INITIAL-PARAM` | `PIC X(50)` | 初期パラメータ | — |

**出力** `INQ-OUTPUT`

| フィールド | PIC | 説明 | 88-level |
|---|---|---|---|
| `INQ-STATUS` | `PIC X(2)` | 処理ステータス | `00` OK / `08` INVALID / `12` IO-FAIL / `16` FATAL |
| `INQ-SESSION-DURATION-SEC` | `PIC 9(7)` | セッション時間 (秒) | — |
| `INQ-QUERIES-EXECUTED` | `PIC 9(5)` | クエリ実行回数 | — |

---

### 5.19 外部取引入力 (19-INTEGRATIONIN) — `inti-api.cpy`

**入力** `INTI-INPUT`

| フィールド | PIC | 説明 | 88-level |
|---|---|---|---|
| `INTI-BATCH-ID` | `PIC X(14)` | バッチID | — |
| `INTI-BUSINESS-DATE` | `PIC 9(8)` | 営業日 | — |
| `INTI-INPUT-FILENAME` | `PIC X(120)` | 入力ファイル | — |
| `INTI-OUTPUT-FILENAME` | `PIC X(120)` | 出力ファイル | — |
| `INTI-REJECT-FILENAME` | `PIC X(120)` | 却下ファイル | — |
| `INTI-SENTINEL-FILENAME` | `PIC X(120)` | センチネルファイル | — |
| `INTI-REJECT-THRESHOLD-PCT` | `PIC 9(3)` | 却下閾値 (%) | — |
| `INTI-REQUIRE-SENTINEL` | `PIC X(1)` | センチネル要求 | `"Y"` 要 / `"N"` 不要 |

**出力** `INTI-OUTPUT`

| フィールド | PIC | 説明 | 88-level |
|---|---|---|---|
| `INTI-STATUS` | `PIC X(2)` | 処理ステータス | `00` OK / `01` NO-INPUT-READY / `04` PARTIAL / `08` INVALID / `12` IO-FAIL / `16` FATAL |
| `INTI-OUT-RECORDS-READ` | `PIC 9(10)` | 読取件数 | — |
| `INTI-OUT-DETAILS-DECODED` | `PIC 9(10)` | デコード件数 | — |
| `INTI-OUT-DETAILS-REJECTED` | `PIC 9(10)` | 却下件数 | — |
| `INTI-OUT-REJECT-PCT` | `PIC 9(3)` | 却下率 (%) | — |
| `INTI-OUT-CHECKSUM-MATCH` | `PIC X(1)` | チェックサム一致 | `"Y"` 一致 / `"N"` 不一致 |
| `INTI-OUT-DURATION-SEC` | `PIC 9(5)` | 処理時間 (秒) | — |

---

### 5.20 外部連携 (20-INTEGRATIONOUT) — `into-api.cpy`

**イベント発行** `INTO-INPUT` / `INTO-OUTPUT`

| フィールド | PIC | 方向 | 説明 | 88-level |
|---|---|---|---|---|
| `INTO-EVENT-TYPE` | `PIC X(20)` | IN | イベントタイプ | `"txn.posted"` / `"interest.posted"` / `"autodebit.failed"` / `"batch.completed"` / `"statement.generated"` |
| `INTO-BUSINESS-DATE` | `PIC 9(8)` | IN | 営業日 | — |
| `INTO-BATCH-ID` | `PIC X(14)` | IN | バッチID | — |
| `INTO-TXN-ID` | `PIC X(18)` | IN | 取引ID | — |
| `INTO-ACCOUNT` | `PIC X(13)` | IN | 口座番号 | — |
| `INTO-AMOUNT-JPY` | `PIC S9(15) COMP-3` | IN | 金額 (円) | — |
| `INTO-CATEGORY` | `PIC X(2)` | IN | カテゴリ | — |
| `INTO-REASON` | `PIC X(10)` | IN | 理由 | — |
| `INTO-COUNT` | `PIC 9(10)` | IN | 件数 | — |
| `INTO-MODE` | `PIC X(1)` | IN | モード | `"R"` 本番 / `"M"` モック |
| `INTO-STATUS` | `PIC X(2)` | OUT | 処理ステータス | `00` OK / `04` RETRY-EXHAUSTED / `08` INVALID / `12` BROKER-FAIL / `16` FATAL |
| `INTO-EVENT-ID` | `PIC X(36)` | OUT | イベントID (UUID) | — |
| `INTO-DURATION-MS` | `PIC 9(7)` | OUT | 処理時間 (ms) | — |
| `INTO-RETRY-COUNT` | `PIC 9(1)` | OUT | リトライ回数 | — |

**出力排出** `INTD-INPUT` / `INTD-OUTPUT`

| フィールド | PIC | 方向 | 説明 | 88-level |
|---|---|---|---|---|
| `INTD-SOURCE-FILENAME` | `PIC X(120)` | IN | 元ファイル | — |
| `INTD-MAX-RECORDS` | `PIC 9(7)` | IN | 最大件数 | — |
| `INTD-MODE` | `PIC X(1)` | IN | モード | `"R"` 本番 / `"M"` モック |
| `INTD-STATUS` | `PIC X(2)` | OUT | 処理ステータス | `00` OK / `04` PARTIAL / `08` INVALID / `12` IO-FAIL / `16` FATAL |
| `INTD-OUT-DRAINED-COUNT` | `PIC 9(7)` | OUT | 排出件数 | — |
| `INTD-OUT-FAILED-COUNT` | `PIC 9(7)` | OUT | 失敗件数 | — |
| `INTD-OUT-DURATION-MS` | `PIC 9(7)` | OUT | 処理時間 (ms) | — |

---

### 5.21 監査 (21-AUDIT) — `audit-api.cpy`

**照会** `AQF-INPUT` / `AQF-OUTPUT`

| フィールド | PIC | 方向 | 説明 | 88-level |
|---|---|---|---|---|
| `AQF-DATE-START` | `PIC 9(8)` | IN | 開始日 | — |
| `AQF-DATE-END` | `PIC 9(8)` | IN | 終了日 | — |
| `AQF-SUBSYSTEM` | `PIC X(30)` | IN | サブシステム | — |
| `AQF-ACTION` | `PIC X(50)` | IN | アクション | — |
| `AQF-SEVERITY` | `PIC X(1)` | IN | 重大度 | `" "` 任意 / `"I"` INFO / `"W"` WARN / `"E"` ERROR / `"C"` CRITICAL |
| `AQF-ACCOUNT-FILTER` | `PIC X(13)` | IN | 口座フィルタ | — |
| `AQF-MAX-ROWS` | `PIC 9(5)` | IN | 最大行数 | — |
| `AQF-OUTPUT-FORMAT` | `PIC X(4)` | IN | 出力形式 | `"TEXT"` / `"CSV "` / `"JSON"` |
| `AQF-OUTPUT-FILENAME` | `PIC X(120)` | IN | 出力ファイル | — |
| `AQF-OPERATOR-USER` | `PIC X(30)` | IN | オペレータ | — |
| `AQF-STATUS` | `PIC X(2)` | OUT | 処理ステータス | `00` OK / `08` INVALID / `12` IO-FAIL / `16` FATAL |
| `AQF-OUT-ROW-COUNT` | `PIC 9(7)` | OUT | 返却行数 | — |
| `AQF-OUT-QUERY-ID` | `PIC X(36)` | OUT | クエリID | — |
| `AQF-OUT-DURATION-MS` | `PIC 9(7)` | OUT | 処理時間 (ms) | — |

**パーティション管理** `APR-INPUT` / `APR-OUTPUT`

| フィールド | PIC | 方向 | 説明 | 88-level |
|---|---|---|---|---|
| `APR-OPERATOR-USER` | `PIC X(30)` | IN | オペレータ | — |
| `APR-RETENTION-DAYS` | `PIC 9(5)` | IN | 保持日数 | — |
| `APR-DRY-RUN` | `PIC X(1)` | IN | ドライラン | `"Y"` 実施 / `"N"` 本番 |
| `APR-ENABLE-DETACH` | `PIC X(1)` | IN | デタッチ許可 | `"Y"` 許可 / `"N"` 禁止 |
| `APR-STATUS` | `PIC X(2)` | OUT | 処理ステータス | `00` OK / `08` INVALID / `16` FATAL |
| `APR-OUT-CREATED-COUNT` | `PIC 9(3)` | OUT | 作成パーティション数 | — |
| `APR-OUT-DETACHED-COUNT` | `PIC 9(3)` | OUT | デタッチ件数 | — |
| `APR-OUT-NEXT-PARTITION` | `PIC X(20)` | OUT | 次パーティション | — |

**集計レポート** `ASR-INPUT` / `ASR-OUTPUT`

| フィールド | PIC | 方向 | 説明 | 88-level |
|---|---|---|---|---|
| `ASR-DATE-START` | `PIC 9(8)` | IN | 開始日 | — |
| `ASR-DATE-END` | `PIC 9(8)` | IN | 終了日 | — |
| `ASR-MODE` | `PIC X(1)` | IN | モード | `"D"` 日別 / `"S"` サブシステム別 |
| `ASR-OUTPUT-FILENAME` | `PIC X(120)` | IN | 出力ファイル | — |
| `ASR-STATUS` | `PIC X(2)` | OUT | 処理ステータス | `00` OK / `08` INVALID / `12` IO-FAIL / `16` FATAL |
| `ASR-OUT-GROUP-COUNT` | `PIC 9(7)` | OUT | グループ数 | — |
| `ASR-OUT-TOTAL-ROWS` | `PIC 9(10)` | OUT | 総行数 | — |

---

### 5.22 運営 (22-OPERATIONS) — `ops-api.cpy`

**バッチ開始** `OPB-INPUT` / `OPB-OUTPUT`

| フィールド | PIC | 方向 | 説明 | 88-level |
|---|---|---|---|---|
| `OPB-BATCH-ID` | `PIC X(14)` | IN | バッチID | — |
| `OPB-BUSINESS-DATE` | `PIC 9(8)` | IN | 営業日 | — |
| `OPB-DRY-RUN` | `PIC X(1)` | IN | ドライラン | `"Y"` 実施 / `"N"` 本番 |
| `OPB-STATUS` | `PIC X(2)` | OUT | 処理ステータス | `00` OK / `04` HALTED / `08` INVALID / `02` FLOCK-CONFLICT / `16` FATAL |
| `OPB-OUT-LAST-STEP` | `PIC X(20)` | OUT | 最終ステップ | — |
| `OPB-OUT-STEPS-RUN` | `PIC 9(2)` | OUT | 実行ステップ数 | — |
| `OPB-OUT-FINALIZED-COUNT` | `PIC 9(7)` | OUT | 確定件数 | — |
| `OPB-OUT-DURATION-SEC` | `PIC 9(5)` | OUT | 処理時間 (秒) | — |

**確定処理** `OPF-INPUT` / `OPF-OUTPUT`

| フィールド | PIC | 方向 | 説明 | 88-level |
|---|---|---|---|---|
| `OPF-BATCH-ID` | `PIC X(14)` | IN | バッチID | — |
| `OPF-BUSINESS-DATE` | `PIC 9(8)` | IN | 営業日 | — |
| `OPF-CHUNK-SIZE` | `PIC 9(7)` | IN | チャンクサイズ | — |
| `OPF-STATUS` | `PIC X(2)` | OUT | 処理ステータス | `00` OK / `08` INVALID / `12` IO-FAIL / `16` FATAL |
| `OPF-OUT-FINALIZED-COUNT` | `PIC 9(7)` | OUT | 確定件数 | — |
| `OPF-OUT-CHUNKS-RUN` | `PIC 9(4)` | OUT | チャンク数 | — |

**パーティション管理** `OPR-INPUT` / `OPR-OUTPUT`

| フィールド | PIC | 方向 | 説明 | 88-level |
|---|---|---|---|---|
| `OPR-OPERATOR-USER` | `PIC X(30)` | IN | オペレータ | — |
| `OPR-RETENTION-DAYS` | `PIC 9(5)` | IN | 保持日数 | — |
| `OPR-DRY-RUN` | `PIC X(1)` | IN | ドライラン | — |
| `OPR-ENABLE-DETACH` | `PIC X(1)` | IN | デタッチ許可 | — |
| `OPR-STATUS` | `PIC X(2)` | OUT | 処理ステータス | `00` OK / `16` FATAL |
| `OPR-OUT-CREATED-COUNT` | `PIC 9(3)` | OUT | 作成件数 | — |
| `OPR-OUT-DETACHED-COUNT` | `PIC 9(3)` | OUT | デタッチ件数 | — |
| `OPR-OUT-NEXT-PARTITION` | `PIC X(20)` | OUT | 次パーティション | — |

**出力排出** `OPD-INPUT` / `OPD-OUTPUT`

| フィールド | PIC | 方向 | 説明 | 88-level |
|---|---|---|---|---|
| `OPD-SOURCE-FILENAME` | `PIC X(120)` | IN | 元ファイル | — |
| `OPD-MAX-RECORDS` | `PIC 9(7)` | IN | 最大件数 | — |
| `OPD-MODE` | `PIC X(1)` | IN | モード | `"M"` モック / `"R"` 本番 |
| `OPD-STATUS` | `PIC X(2)` | OUT | 処理ステータス | `00` OK / `04` PARTIAL / `16` FATAL |
| `OPD-OUT-DRAINED-COUNT` | `PIC 9(7)` | OUT | 排出件数 | — |
| `OPD-OUT-FAILED-COUNT` | `PIC 9(7)` | OUT | 失敗件数 | — |

---

### 5.S 共通 copybook

#### 5.S.1 監査書込 — `aud-write-api.cpy`

**レコード** `WS-AUD-ROW`

| フィールド | PIC | 長さ | 説明 |
|---|---|---|---|
| `WS-AUD-SUBSYSTEM` | `PIC X(30)` | 30 | サブシステム名 |
| `WS-AUD-ACTION` | `PIC X(50)` | 50 | アクション |
| `WS-AUD-ACTOR` | `PIC X(30)` | 30 | 操作者 |
| `WS-AUD-TARGET-TYPE` | `PIC X(20)` | 20 | 対象種別 |
| `WS-AUD-TARGET-ID` | `PIC X(20)` | 20 | 対象ID |
| `WS-AUD-PAYLOAD-JSON` | `PIC X(2000)` | 2000 | ペイロード (JSON) |
| `WS-AUD-SEVERITY` | `PIC X(1)` | 1 | 重大度 |
| `WS-AUD-BUSINESS-DATE` | `PIC 9(8)` | 8 | 営業日 |
| `WS-AUD-EVENT-KEY` | `PIC X(80)` | 80 | イベントキー |

**リターンコード** `WS-AUD-RC PIC 9(2)`

#### 5.S.2 共通ログ — `shared-log-api.cpy`

**レコード** `WS-LOG-MSG`

| フィールド | PIC | 長さ | 説明 |
|---|---|---|---|
| `WS-LOG-SUBSYSTEM` | `PIC X(30)` | 30 | サブシステム名 |
| `WS-LOG-LEVEL` | `PIC X(5)` | 5 | ログレベル |
| `WS-LOG-MESSAGE` | `PIC X(500)` | 500 | メッセージ |

**リターンコード** `WS-LOG-RC PIC 9(2)`

#### 5.S.3 共通コード — `ws-codes.cpy`

**API リターンコード** `WS-RC-CODES`

| フィールド | 値 | 意味 |
|---|---|---|
| `WS-RC-OK` | `0` | 正常 |
| `WS-RC-WARN` | `4` | 警告 |
| `WS-RC-RECOVERABLE` | `8` | 復旧可能 |
| `WS-RC-OPERATOR` | `12` | オペレータ操作 |
| `WS-RC-FATAL` | `16` | 致命的 |

**ファイルステータス** `WS-FS-CODES`

| フィールド | 値 | 意味 |
|---|---|---|
| `WS-FS-OK` | `"00"` | 正常 |
| `WS-FS-OK-DUP-ALT` | `"02"` | 正常 (代替) |
| `WS-FS-WRONG-LENGTH` | `"04"` | レコード長誤り |
| `WS-FS-EOF` | `"10"` | EOF |
| `WS-FS-KEY-OOR` | `"14"` | キー範囲外 |
| `WS-FS-SEQ-ERR` | `"21"` | 順例外 |
| `WS-FS-DUP-KEY` | `"22"` | キー重複 |
| `WS-FS-NOT-FOUND` | `"23"` | 未検出 |
| `WS-FS-DISK-FULL` | `"24"` | ディスク満杯 |
| `WS-FS-PERM-IO-30` | `"30"` | I/O 許可 |
| `WS-FS-FILE-NOT-EXIST` | `"35"` | ファイル不存在 |
| `WS-FS-ATTR-CONFLICT` | `"39"` | 属性競合 |

**ログレベル** `WS-LOG-LEVELS`

| フィールド | 値 |
|---|---|
| `WS-LOG-LEVEL-DEBUG` | `"DEBUG"` |
| `WS-LOG-LEVEL-INFO` | `"INFO "` |
| `WS-LOG-LEVEL-WARN` | `"WARN "` |
| `WS-LOG-LEVEL-ERROR` | `"ERROR"` |

#### 5.S.4 複式補助 — `double-entry-helper.cpy`

**入力** `DEH-IN`

| フィールド | PIC | 説明 |
|---|---|---|
| `DEH-CAT` | `PIC 9(2)` | 取引カテゴリ |
| `DEH-AMOUNT-JPY` | `PIC S9(15)` | 金額 |
| `DEH-DR-ACCT` | `PIC X(13)` | 借方口座 |
| `DEH-CR-ACCT` | `PIC X(13)` | 貸方口座 |

**出力** `DEH-OUT`

| フィールド | PIC | 説明 |
|---|---|---|
| `DEH-RC` | `PIC 9(2)` | リターンコード |
| `DEH-MSG` | `PIC X(80)` | メッセージ |


## 6. ファイルライフサイクル

### 6.1 一時ファイル一覧

| # | 論理名 | 物理名 | 生成元 | 消費先 | ライフサイクル |
|---|---|---|---|---|---|
| 1 | 取引-ready | `txn-ready.dat` | 11-TXSM | 12-TXNPOST | 日次生成 → 日次削除 |
| 2 | 取引-sorted | `txn-sorted.dat` | 11-TXSM | 11-TXSM | 日次生成 → 日次削除 |
| 3 | 取引-valid | `txn-valid.dat` | 10-TXVAL | 11-TXSM | 日次生成 → 日次削除 |
| 4 | 取引-error | `txn-error.dat` | 10-TXVAL, 11-TXSM | 22-OPS | 日次生成 → 保存 → アーカイブ |
| 5 | 取引-decoded | `txn-decoded.dat` | 19-INTI | 10-TXVAL | 日次生成 → 日次削除 |
| 6 | 取引-ready-d-temp | `txn-ready-d-temp.dat` | 11-TXSM | 11-TXSM | 日次生成 → 日次削除 |
| 7 | リコン前日 | `txn-recon-prev.dat` | 11-TXSM | 11-TXSM | 日次生成 → 翌日削除 |
| 8 | リコン保留 | `txn-recon-defer.dat` | 12-TXNPOST | 12-TXNPOST | 日次生成 → 翌日再処理 → 削除 |
| 9 | チェックポイント | `txn-checkpoint.dat` | 10-TXVAL, 11-TXSM, 12-TXNPOST, 13-IACR, 14-IPST, 15-AD | 同上 (再開時) | ステップ毎更新 → バッチ完了時削除 |
| 10 | ドーマンシー修復 | `dormancy-repair.dat` | 12-TXNPOST | 22-OPS | 日次生成 → 保存 → アーカイブ |
| 11 | 利息サマリ | `iacr-summary.dat` | 13-IACR | 14-IPST, 22-OPS | 日次生成 → 日次削除 |
| 12 | 自動引落失敗 | `autodebit-failed.dat` | 15-AUTODEBIT | 20-INTO | 日次生成 → 保存 → アーカイブ |

### 6.2 ライフサイクルフロー

```
外部ファイル (19-INTI)
    │
    ▼
txn-decoded.dat ──────→ 10-TXVAL ──────→ txn-valid.dat
                              │                  │
                              ▼                  ▼
                        txn-error.dat      11-TXSM ──────→ txn-ready.dat
                              │                  │                  │
                              ▼                  ▼                  ▼
                        アーカイブ         txn-sorted.dat     12-TXNPOST ──→ 仕訳/残高更新
                                                 │                  │
                                                 ▼                  ▼
                                          txn-recon-prev.dat   txn-recon-defer.dat
                                                                        │
                                                                        ▼
                                                                  翌日 12-TXNPOST
```

### 6.3 アーカイブ方針

| ファイル | 保持期間 | アーカイブ先 |
|---|---|---|
| `txn-error.dat` | 90 日 | 外部ストレージ |
| `dormancy-repair.dat` | 1 年 | 外部ストレージ |
| `autodebit-failed.dat` | 1 年 | 外部ストレージ |
| `audit_log` (PG) | `retention_days` で設定 (デフォルト 365 日) | パーティション detach |

---

## 7. コード値一覧

> 以下、システム全体で使用される列挙値を一覧化。
> COBOL 88-level 条件名と PostgreSQL CHECK 制約の両方に適用。

### 7.1 口座ステータス (`acct_status`)

| コード | 意味 | COBOL 88-level | 説明 |
|---|---|---|---|
| `P` | 申請中 | `ACCT-ST-APPLICATION` | 開設申請受付済、未承認 |
| `A` | 有効 | `ACCT-ST-ACTIVE` | 取引可能な状態 |
| `D` | ドーマンシー | `ACCT-ST-DORMANT` | 長期間取引なし |
| `S` | 停止 | `ACCT-ST-SUSPENDED` | 凍結・停止 |
| `C` | 解約 | `ACCT-ST-CLOSED` | 解約済 |
| `R` | 再開 | `ACCT-ST-REACTIVATING` | ドーマンシー→有効 移行中 |

### 7.2 取引カテゴリ (`category`)

| コード | 意味 | COBOL 88-level | 説明 |
|---|---|---|---|
| `10` | 入金 | `FS-CAT-DEPOSIT` | 現金入金・振込入金 |
| `20` | 出金 | `FS-CAT-WITHDRAW` | 現金出金 |
| `30` | 振込 | `FS-CAT-TRANSFER` | 口座間振替 |
| `40` | 電信 | `FS-CAT-WIRE` | 電信送金 |
| `50` | 手数料 | — | 手数料賦課 |

### 7.3 取引ステータス (`status` in `transactions`)

| コード | 意味 | 説明 |
|---|---|---|
| `PT` | 起票済 | 仕訳済、確定 |
| `SE` | 照会済 | 照会済 (取消可能) |
| `RV` | 取消済 | 取消取引 (reversal_of に元取引) |

### 7.4 利息ステータス (`status` in `interest_accruals`)

| コード | 意味 | COBOL 88-level | 説明 |
|---|---|---|---|
| `AC` | 計算済 | — | 利息計算済、未起票 |
| `PT` | 起票済 | — | 利息起票済 |
| `CN` | 取消 | — | 計算取消 |

### 7.5 自動引落ステータス (`status` in `autodebit_schedules`)

| コード | 意味 | COBOL 88-level | 説明 |
|---|---|---|---|
| `AC` | 有効 | — | 引落実行中 |
| `SP` | 停止 | — | 一時停止 |
| `TM` | 解約 | — | 自動引落終了 |

### 7.6 バッチステータス (`status` in `batch_run`)

| コード | 意味 | 説明 |
|---|---|---|
| `RN` | 実行中 | バッチ処理中 |
| `OK` | 正常終了 | 全ステップ成功 |
| `FL` | 失敗 | 一部ステップ失敗 |
| `AB` | 異常中断 | 致命的エラーで中断 |

### 7.7 監査重大度 (`severity` in `audit_log`)

| コード | 意味 | COBOL 88-level | WS 定数 | 説明 |
|---|---|---|---|---|
| `I` | 情報 | `AQF-SEV-INFO` | `WS-AUD-SEV-INFO` | 通常の操作記録 |
| `W` | 警告 | `AQF-SEV-WARN` | `WS-AUD-SEV-WARN` | 注意が必要 |
| `E` | エラー | `AQF-SEV-ERROR` | `WS-AUD-SEV-ERROR` | 処理失敗 |
| `C` | 致命的 | `AQF-SEV-CRITICAL` | `WS-AUD-SEV-CRITICAL` | システム障害 |

### 7.8 商品タイプ (`product_type`)

| コード | 意味 | COBOL 88-level | 説明 |
|---|---|---|---|
| `S` | 普通預金 | `PRD-TYPE-SAVINGS` | 普通預金 |
| `C` | 当座預金 | `PRD-TYPE-CHECKING` | 当座貸越可能 |
| `T` | 定期預金 | `PRD-TYPE-TIME-DEPOSIT` | 期間固定 |

### 7.9 日タイプ (`day_type` in `calendar`)

| コード | 意味 | COBOL 88-level | 説明 |
|---|---|---|---|
| `B` | 営業日 | `CAL-DAY-BUSINESS` | 営業日 |
| `H` | 祝日 | `CAL-DAY-HOLIDAY` | 祝日 |
| `W` | 週末 | `CAL-DAY-WEEKEND` | 土日 |

### 7.10 自動引落頻度 (`frequency` in `autodebit_schedules`)

| コード | 意味 | 説明 |
|---|---|---|
| `M` | 月次 | 毎月 |
| `W` | 週次 | 毎週 |
| `D` | 日次 | 毎日 |

### 7.11 支店タイプ (`branch_type`)

| コード | 意味 | 説明 |
|---|---|---|
| `H` | 本店 | 本店 |
| `B` | 支店 | 一般支店 |
| `S` | 出張所 | 小型拠点 |

### 7.12 顧客ティア (`tier`)

| コード | 意味 | 説明 |
|---|---|---|
| `A` | 上位 | 高残高顧客 |
| `B` | 標準 | 一般顧客 (デフォルト) |
| `C` | 基本 | 低残高顧客 |

### 7.13 監査アクション (主要)

| アクション | 説明 |
|---|---|
| `account.open` | 口座開設 |
| `account.close` | 口座解約 |
| `account.status_change` | 口座ステータス変更 |
| `account.dormancy_update` | ドーマンシー更新 |
| `txn.post` | 取引起票 |
| `txn.reverse` | 取引取消 |
| `interest.accrue` | 利息計算 |
| `interest.post` | 利息起票 |
| `autodebit.execute` | 自動引落実行 |
| `fee.charge` | 手数料課金 |
| `batch.start` | バッチ開始 |
| `batch.complete` | バッチ完了 |
| `batch.fail` | バッチ失敗 |

### 7.14 外部イベントタイプ

| イベントタイプ | 説明 |
|---|---|
| `txn.posted` | 取引確定 |
| `interest.posted` | 利息入金 |
| `autodebit.failed` | 自動引落失敗 |
| `batch.completed` | バッチ完了 |
| `statement.generated` | 明細生成 |

---

## 付録A: フィールド名 → テーブル/ファイル 逆引き索引

| フィールドプレフィクス | 該当データストア | 該当サブシステム |
|---|---|---|
| `ACCT-LO-*` / `ACCT-REC-*` | `account.idx` / `accounts` | 08-ACCT, 09-ALC |
| `BR-REC-*` / `BR-OUT-*` | `branch.idx` / `branches` | 02-BRANCH |
| `CR-*` / `CUST-OUT-*` | `customer.idx` / `customers` | 03-CUSTOMER |
| `PRD-REC-*` / `PRD-OUT-*` | `product.idx` / `products` | 05-PRODUCT |
| `IR-REC-*` / `IR-OUT-*` | `interestrate.idx` / `interest_rates` | 06-IRATE |
| `FS-REC-*` / `FS-OUT-*` | `feeschedule.idx` / `fee_schedules` | 07-FS |
| `CAL-REC-*` / `CAL-OUT-*` | `calendar.idx` / `calendar` | 01-CALENDAR |
| `TXN-READY-*` / `TXN-SORTED-*` | `txn-ready.dat` / `txn-sorted.dat` | 11-TXSM |
| `TXVAL-OUT-*` | `txn-valid.dat` | 10-TXVAL |
| `TEF-*` | `txn-error.dat` | 10-TXVAL, 11-TXSM |
| `IACR-OUT-*` | `interest_accruals` | 13-IACR |
| `IPST-OUT-*` | `interest_accruals` | 14-IPST |
| `AD-OUT-*` | `autodebit_schedules` | 15-AUTODEBIT |
| `FEE-OUT-*` | `transactions` (手数料) | 16-FEE |
| `STMT-OUT-*` | 明細ファイル | 17-STMT |
| `INTI-OUT-*` | `txn-decoded.dat` | 19-INTI |
| `INTO-OUT-*` | 外部ブローカー | 20-INTO |
| `AQF-OUT-*` | `audit_log` | 21-AUDIT |
| `WS-AUD-*` | `audit_log` | shared |
| `WS-LOG-*` | ログストア | shared |
| `DEH-*` | 仕訳補助 (メモリ) | shared |

---

## 付録B: データマッピング (ISAM → PostgreSQL)

| ISAM フィールド | PostgreSQL カラム | 変換ルール |
|---|---|---|
| `ACCT-REC-NUMBER` (PIC 9(13)) | `accounts.acct_number` (CHAR(13)) | 数値→文字列 (ゼロ埋め) |
| `ACCT-REC-OPENED-DATE` (PIC 9(8)) | `accounts.opened_date` (DATE) | YYYYMMDD → DATE |
| `ACCT-REC-STATUS` (PIC X(1)) | `accounts.acct_status` (CHAR(1)) | 直接マッピング |
| `ACCT-REC-OVERDRAFT` (COMP-3) | `accounts.min_balance_jpy` (BIGINT) | パック→整数 |
| `CR-ID` (PIC 9(10)) | `customers.cust_id` (CHAR(10)) | 数値→文字列 |
| `CR-KANJI` (PIC X(60)) | `customers.cust_name` (VARCHAR(60)) | 直接 |
| `CR-KANA` (PIC X(50)) | `customers.cust_name_kana` (VARCHAR(80)) | 直接 (長さ拡張) |
| `BR-REC-CODE` (PIC X(3)) | `branches.branch_code` (CHAR(3)) | 直接 |
| `PRD-REC-CODE` (PIC X(3)) | `products.product_code` (CHAR(3)) | 直接 |
| `IR-REC-RATE` (S9(3)V9(4) COMP-3) | `interest_rates.annual_rate` (NUMERIC(7,6)) | パック→NUMERIC |
| `FS-REC-AMOUNT` (S9(9) COMP-3) | `fee_schedules.fee_jpy` (BIGINT) | パック→整数 |
| `CAL-REC-DATE` (PIC 9(8)) | `calendar.cal_date` (DATE) | YYYYMMDD → DATE |
| `CAL-REC-DAY-TYPE` (PIC X(1)) | `calendar.day_type` (CHAR(1)) | 直接 |

---

> **改訂履歴**
> - 2026-07-06: 初版作成 (DDL V1/V2/V3 + 全 copybook 反映)
