# システム全体設計書 — Legacy COBOL Banking System

> **更新日:** 2026-07-06
> **スコープ:** 22 サブシステム / 82 プログラムの全体像とデータフローを定義する
> **目的:** この設計書を読むことで「システム全体が何を処理し、データがどう流れるか」を一瞥で把握できる

---

## 1. システム概要

本システムは**銀行向けコアバッチ処理システム**である。

- **外部から入ってくる取引データ**（EBCDIC 800B 固定長ファイル）をデコード・妥当性検証・仕訳・記帳する
- **日次パイプライン**で金利計算・自動引き落とし・手数料請求・帳票生成・イベント発行を直列実行する
- **月次パイプライン**で金利入金と監査パーティションの繰り越しを行う
- **マスタデータ**（店舗・顧客・商品・金利・手数料・カレンダー・口座）を ISAM インデックス + PostgreSQL の二重管理で持つ
- **監査証拠**を全処理で `audit_log` テーブルに残し、外部には RabbitMQ 経由でイベントを発行する

---

## 2. システムコンテキスト図 (System Context)

```mermaid
flowchart LR
    subgraph ext [外部システム]
        EBCDIC[EBCDIC 800B<br/>入金ファイル]
        SENTINEL[センティネル<br/>トリガファイル]
        OPS[運用担当者<br/>cron / CLI]
        MQ[RabbitMQ<br/>イベントブロKER]
        DWH[データウェアハウス<br/>イベント消費者]
    end

    subgraph sys [コアバッチシステム]
        SYS[22 サブシステム<br/>82 プログラム]
    end

    EBCDIC -->|固定長 800B| SYS
    SENTINEL -->|処理開始トリガ| SYS
    OPS -->|make / cron| SYS
    SYS -->|txn.posted<br/>interest.posted<br/>autodebit.failed<br/>batch.completed<br/>statement.generated| MQ
    MQ -->|JSON イベント| DWH
    SYS -->|decoded 600B<br/>reject file<br/>statement file| OPS
```

---

## 3. サブシステム構成図

```mermaid
flowchart TB
    subgraph ops [22-operations オーケストレーション]
        DAILY[OPS-BATCH-DAILY<br/>日次パイプライン]
        MONTHLY[OPS-BATCH-MONTHLY<br/>月次パイプライン]
        MASTER[OPS-MASTER-LOAD<br/>マスタロード]
        FINAL[OPS-FINALIZE<br/>PT→SE 一括更新]
    end

    subgraph txn [トランザクション処理パイプライン]
        INTI[19-integrationin<br/>EBCDIC デコード]
        VALIDATE[10-txnvalidate<br/>妥当性検証]
        SORT[11-txnsortmerge<br/>ソート+マージ]
        POST[12-txnpost<br/>複式記帳]
    end

    subgraph daily [日次バッチ処理]
        IACR[13-interestaccrual<br/>金利計算]
        AD[15-autodebit<br/>自動引き落とし]
        FEE[16-fee<br/>手数料請求]
        STMT[17-statement<br/>帳票生成]
        DRAIN[20-integrationout<br/>イベント発行]
    end

    subgraph monthly [月次バッチ処理]
        IPST[14-interestpost<br/>金利入金]
        ROLLOVER[21-audit<br/>パーティション繰越]
    end

    subgraph master [マスタ管理]
        CAL[01-calendar]
        BR[02-branch]
        CUST[03-customer]
        CSRCH[04-customersearch]
        PRD[05-product]
        IRATE[06-interestrate]
        FEESCH[07-feeschedule]
        ACCT[08-account]
        ALC[09-accountlifecycle]
    end

    subgraph shared [共通ユーティリティ]
        AUD[aud-write<br/>監査ログ]
        LOG[shared-log<br/>アプリログ]
        DEH[double-entry-helper<br/>仕訳バリデーション]
    end

    DAILY --> INTI & IACR & AD & FEE & STMT & DRAIN
    MONTHLY --> IPST & ROLLOVER
    MASTER --> CAL & BR & CUST & PRD & IRATE & FEESCH & ACCT

    INTI --> VALIDATE --> SORT --> POST
    POST --> ACCT
    IACR --> IRATE & PRD & ACCT
    AD --> ACCT
    FEE --> FEESCH & ACCT
    STMT --> ACUST & BR & CAL

    VALIDATE --> CAL & BR & PRD
    SORT --> CAL
    POST --> AUD & LOG & DEH
    IACR --> AUD
    AD --> AUD
    FEE --> AUD
    IPST --> AUD
    DRAIN --> AUD
```

---

## 4. マスタデータモデル

```mermaid
erDiagram
    CALENDAR ||--o{ BATCH_RUN : "business_date で駆動"
    BRANCH ||--o{ ACCOUNT : "branch-code で所属"
    CUSTOMER ||--o{ ACCOUNT : "customer-id で保有"
    PRODUCT ||--o{ ACCOUNT : "product-code で分類"
    IRATE ||--o{ INTEREST_ACCRUAL : "product+term tier で金利"
    FEE_SCHEDULE ||--o{ FEE_CHARGE : "category+tier で手数料"

    CALENDAR {
        date cal_rec_date PK
        string cal_rec_day_type "B/H/W"
        string cal_rec_holiday_name
    }
    BRANCH {
        string branch_code PK
        string branch_name_kanji
        string branch_name_kana
        string region
        date opened_date
        string status
    }
    CUSTOMER {
        string customer_id PK
        string name_kanji
        string name_kana
        string phone
        string address
        string tier
        string status
    }
    PRODUCT {
        string product_code PK
        string product_type "S/C/T"
        string interest_type
        decimal min_balance
        int term_months
        date eff_from
        date eff_to
    }
    ACCOUNT {
        string account_no PK
        string customer_id FK
        string branch_code FK
        string product_code FK
        decimal balance_jpy
        date dormancy_date
        string status "A/D/C"
    }
    IRATE {
        string product_code PK
        int term_tier PK
        decimal rate_micro
        date eff_from PK
    }
    FEE_SCHEDULE {
        string category PK
        int tier PK
        decimal fee_jpy
        date eff_from PK
    }
    BATCH_RUN {
        bigint run_id PK
        date business_date
        string status "OK/FL/HALTED"
        string last_step
        timestamp started_at
        timestamp completed_at
    }
```

---

## 5. マスタデータロードフロー

**初回セットアップ** (`make load-idx` / `OPS-MASTER-LOAD`) で ISAM インデックスを構築する。

```mermaid
flowchart TD
    START([make load-idx]) --> CAL_LOAD[CAL-LOAD<br/>calendar-seed.dat → calendar.idx]
    CAL_LOAD --> BR_LOAD[BR-LOAD<br/>branches-mvp.dat → branch.idx]
    BR_LOAD --> CUST_LOAD[CUST-LOAD<br/>customers-mvp.dat → customer.idx]
    CUST_LOAD --> PRD_LOAD[PROD-LOAD<br/>products-mvp.dat → product.idx]
    PRD_LOAD --> IRATE_LOAD[IRATE-LOAD<br/>interestrate-seed.dat → interestrate.idx]
    IRATE_LOAD --> FEESCH_LOAD[FEE-LOAD<br/>feeschedule-seed.dat → feeschedule.idx]
    FEESCH_LOAD --> ACCT_LOAD[ACCT-LOAD<br/>accounts-mvp.dat → account.idx]
    ACCT_LOAD --> SEED_DB[OPS-SEED-SYSTEM-ACCOUNTS<br/>PG accounts 4 件<br/>CASH/CLEARING/INTEREST/FEE]
    SEED_DB --> SEED_AUDIT[OPS-SEED-AUDIT<br/>システム監査レコード初期化]
    SEED_AUDIT --> DONE([完了])
```

---

## 6. トランザクションデータフロー (End-to-End)

```mermaid
flowchart LR
    subgraph inbound [1. 取り込み]
        EBCDIC[EBCDIC 800B<br/>入金ファイル] -->|800B 固定長| INTI[19-INTI<br/>デコード]
        SENT[センティネル] -->|トリガ| INTI
        INTI -->|600B 可変| TXN_FILE[txn-detail file<br/>H/D/T レコード]
        INTI -->|reject| REJECT[reject file]
    end

    subgraph validate [2. 妥当性検証]
        TXN_FILE --> VAL[10-TXNVALIDATE<br/>CAL/BR/PROD マスタ検証]
        VAL -->|OK| VALID_FILE[valid-file]
        VAL -->|NG| ERR_FILE[error-file<br/>E001-E019]
        VAL -->|復旧| CKPT[checkpoint ファイル]
    end

    subgraph sortmerge [3. ソート+マージ]
        VALID_FILE --> SORT[11-SORT<br/>payer-acct↑ / seq↑]
        SORT --> SORTED_FILE[sorted-file]
        SORTED_FILE --> MERGE[11-MERGE<br/>sorted + recon-prev]
        MERGE -->|OK| READY_FILE[txn-ready-file]
        MERGE -->|dup E050| DUP_FILE[txn-error-file]
    end

    subgraph post [4. 記帳]
        READY_FILE --> POST[12-TXNPOST<br/>複式記帳]
        POST -->|double-entry| PG[(PostgreSQL<br/>transactions<br/>postings<br/>balances)]
        POST -->|口座更新| ACCT[08-ACCT<br/>LOOKUP/UPDATE-DORMANCY]
        POST -->|監査| AUD[AUD-WRITE<br/>audit_log]
        POST -->|イベント| EVT[txn.posted]
    end

    subgraph accrual [5. 金利計算]
        PG --> IACR[13-IACR<br/>日次金利計算]
        IACR -->|AC 行| PG
        IACR -->|イベント| IP_EVT[interest.posted]
    end

    subgraph autodebit [6. 自動引き落とし]
        PG --> AD[15-AD<br/>AD-RUN-DAILY]
        AD -->|成功| PG
        AD -->|失敗| FAIL_FILE[autodebit-failed.dat]
    end

    subgraph fee [7. 手数料]
        PG --> FEE[16-FEE<br/>FEE-CHARGE]
        FEE -->|手数料仕訳| PG
        FEE -->|マスタ参照| FEESCH[07-FEESCHEDULE]
        FEE -->|口座確認| ACCT
    end

    subgraph stmt [8. 帳票]
        PG --> STMT[17-STMT<br/>STMT-GENERATE-BATCH]
        STMT -->|帳票ファイル| OUT_FILE[statement file]
        STMT -->|イベント| STMT_EVT[statement.generated]
    end

    subgraph drain [9. イベント発行]
        FAIL_FILE --> DRAIN[20-DRAIN<br/>INTO-DRAIN-QUEUE]
        DRAIN --> PUBLISH[INTO-PUBLISH-EVENT]
        PUBLISH -->|MQ| MQ_OUT[RabbitMQ<br/>autodebit.failed]
    end
```

---

## 7. 日次バッチパイプライン — シーケンス図

```mermaid
sequenceDiagram
    participant cron as cron / 運用
    participant ops as 22-operations<br/>OPS-BATCH-DAILY
    participant flock as OS flock
    participant pg as PostgreSQL
    participant inti as 19-integrationin
    participant iacr as 13-interestaccrual
    participant ad as 15-autodebit
    participant fee as 16-fee
    participant stmt as 17-statement
    participant drain as 20-integrationout
    participant mq as RabbitMQ

    cron->>ops: make batch-daily (business_date)
    ops->>flock: flock -n 9 (排他ロック)
    flock-->>ops: LOCK_OK
    ops->>pg: CONNECT + INSERT batch_run(RN, 'RUNNING')

    ops->>inti: Step 1: INTI-DECODE-BATCH
    inti-->>ops: status (00/04/08/12/16)
    alt status != 00
        ops->>pg: UPDATE batch_run(HALTED)
        ops-->>cron: OPB-HALTED
    end

    ops->>iacr: Step 2: IACR-RUN-DAILY
    iacr->>pg: SELECT accounts / INSERT accruals
    iacr-->>ops: status

    ops->>ad: Step 3: AD-RUN-DAILY
    ad->>pg: SELECT due / UPDATE balances
    alt debit fails
        ad->>ad: WRITE autodebit-failed.dat
    end
    ad-->>ops: status

    ops->>fee: Step 4: FEE-CHARGE
    fee->>pg: INSERT fee postings
    fee-->>ops: status

    ops->>stmt: Step 5: STMT-GENERATE-BATCH
    stmt->>pg: SELECT accounts/txns
    stmt->>stmt: WRITE statement file
    stmt-->>ops: status

    ops->>drain: Step 6: OPS-DRAIN-QUEUES
    drain->>mq: PUBLISH autodebit.failed
    drain-->>ops: status

    ops->>pg: UPDATE batch_run(OK)
    ops->>flock: LOCK_RELEASE
    ops-->>cron: OPB-OK
```

---

## 8. 月次バッチパイプライン — シーケンス図

```mermaid
sequenceDiagram
    participant cron as cron / 運用
    participant ops as 22-operations<br/>OPS-BATCH-MONTHLY
    participant flock as OS flock
    participant pg as PostgreSQL
    participant ipst as 14-interestpost
    participant roll as 21-audit<br/>AUDIT-PARTITION-ROLLOVER
    participant mq as RabbitMQ

    cron->>ops: make batch-monthly (business_date)
    ops->>flock: flock -n 9
    ops->>pg: INSERT batch_run(RN, 'RUNNING')

    ops->>ipst: Step 1: IPST-RUN-MONTHEND
    ipst->>pg: UPDATE balances (accrued → posted)
    ipst->>pg: DELETE accrual rows
    ipst-->>ops: status

    ops->>roll: Step 2: OPS-PARTITION-ROLLOVER
    roll->>pg: ATTACH new audit partition
    roll->>pg: DETACH old audit partition
    roll-->>ops: status

    ops->>pg: UPDATE batch_run(OK)
    ops->>mq: PUBLISH batch.completed
    ops->>flock: LOCK_RELEASE
    ops-->>cron: OPB-OK
```

---

## 9. 外部システム連携

### 9.1 ファイル連携

| 方向 | ファイル | 形式 | 内容 |
|------|---------|------|------|
| **Inbound** | EBCDIC 入金ファイル | 固定長 800B | 送金/入金データ (H/D/T レコード) |
| **Inbound** | センティネルファイル | トリガ | 処理開始の合図 |
| **Outbound** | txn-detail file | 可変長 600B | デコード済み取引データ |
| **Outbound** | reject file | 固定長 | バリデーション拒否レコード |
| **Outbound** | txn-error file | 可変長 | マージ重複 (E050) |
| **Outbound** | autodebit-failed.dat | 固定長 200B | 自動引き落とし失敗キュー |
| **Outbound** | statement file | 可変長 | 帳票 |
| **Outbound** | recon file | 可変長 | 照合用トレーラレコード |

### 9.2 メッセージ連携 (RabbitMQ)

| イベント | 発行タイミング | ペイロード |
|---------|-------------|----------|
| `txn.posted` | 12-TXNPOST 記帳成功時 | account_no, amount, type |
| `interest.posted` | 13-IACR / 14-IPST 入金時 | account_no, amount_jpy |
| `autodebit.failed` | 15-AD 引き落とし失敗時 | account_no, reason |
| `statement.generated` | 17-STMT 帳票生成時 | account_no, period_from/to |
| `batch.completed` | 22-OPS パイプライン完了時 | run_id, business_date, status |

### 9.3 データベース連携 (PostgreSQL)

| テーブル | 用途 | 主なアクセス元 |
|---------|------|-------------|
| `accounts` | 口座マスタ | 08-account, 12-txnpost, 13-iacr, 15-ad, 16-fee |
| `customers` | 顧客マスタ | 03-customer |
| `transactions` | 取引テーブル | 12-txnpost (INSERT) |
| `postings` | 仕訳テーブル | 12-txnpost (INSERT) |
| `balances` | 残高テーブル | 12-txnpost / 13-iacr / 14-ipst (UPDATE) |
| `audit_log` | 監査証拠 | 全サブシステム (INSERT) |
| `batch_run` | パイプライン実行管理 | 22-operations (INSERT/UPDATE) |

---

## 10. サブシステム一覧

| # | サブシステム | 種別 | プログラム数 | 責務 |
|---|------------|------|:----------:|------|
| 01 | calendar | マスタ | 4 | 営業日カレンダー |
| 02 | branch | マスタ | 4 | 店舗マスタ |
| 03 | customer | マスタ | 6 | 顧客マスタ + 検索 |
| 04 | customersearch | マスタ | 3 | 顧客検索 (AND/住所/ページング) |
| 05 | product | マスタ | 2 | 商品マスタ |
| 06 | interestrate | マスタ | 2 | 金利マスタ |
| 07 | feeschedule | マスタ | 2 | 手数料マスタ |
| 08 | account | マスタ | 5 | 口座マスタ + 存在確認 + 休眠更新 |
| 09 | accountlifecycle | マスタ | 4 | 口座開設 + 状態遷移 + 休眠/再活性 |
| 10 | txnvalidate | トランザクション | 3 | 取引妥当性検証 |
| 11 | txnsortmerge | トランザクション | 3 | 取引ソート + マージ |
| 12 | txnpost | トランザクション | 3 | 複式記帳 |
| 13 | interestaccrual | 日次バッチ | 2 | 金利計算 |
| 14 | interestpost | 月次バッチ | 2 | 金利入金 |
| 15 | autodebit | 日次バッチ | 2 | 自動引き落とし |
| 16 | fee | 日次バッチ | 2 | 手数料請求 |
| 17 | statement | 日次バッチ | 1 | 帳票生成 |
| 18 | inquiry | オンライン | 1 | 照会 CUI |
| 19 | integrationin | 取り込み | 1 | EBCDIC デコード |
| 20 | integrationout | 配信 | 2 | イベント発行 |
| 21 | audit | 監査 | 3 | 監査証拠 + パーティション |
| 22 | operations | オーケストレーション | 13 | パイプライン制御 + マスタロード |

---

## 11. 用語集

| 用語 | 意味 |
|------|------|
| ISAM | Indexed Sequential Access Method — GnuCOBOL のインデックスファイル |
| OCESQL | GnuCOBOL の埋め込み SQL プリプロセッサ (PostgreSQL 接続) |
| EBCDIC | メインフレーム文字コード (本システムの外部取引ファイル形式) |
| センチネル | 外部ファイル到着を知らせるトリガファイル |
| 複式記帳 | 借方/貸方の 2 エントリで 1 取引を表現する会計原則 |
| batch_run | バッチパイプラインの 1 回の実行を管理する DB レコード |
| flock | OS レベルファイルロック (バッチの排他制御) |
| SERIALIZABLE | PostgreSQL の最強分離レベル (悲観ロック) |
| DEH | Double-Entry Helper — 仕訳の借貸一致を検証する共有コピーブック |

---

## 12. 参考

- 各サブシステム設計書: `subsystems/NN-name/design/`
- 各プログラム設計書: `subsystems/NN-name/design/<program>.md`
- テンプレート: `subsystems/01-calendar/design/_template.md`
- 旧詳細設計書 (v1): `subsystems/01-calendar/design/v1-detailed/`
- 仕様: `docs/superpowers/specs/2026-07-06-subsystem-design-docs-design.md`
- プラン: `docs/superpowers/plans/2026-07-06-subsystem-design-docs-phase1.md`
