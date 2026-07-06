# 基本設計書 — STMT-GENERATE-BATCH

> **サブシステム:** 17-statement
> **プログラム ID:** `STMT-GENERATE-BATCH`
> **種別:** バッチ
> **更新日:** 2026-07-06

---

## 1. プログラム概要

| 項目 | 値 |
|------|-----|
| プログラム ID | `STMT-GENERATE-BATCH` |
| ソースファイル | `src/stmt-generate-batch.sqb` |
| 所属サブシステム | 17-statement |
| 種別 | バッチ |
| 概要 | 営業日時点の口座スナップショットを取得し、期間内取引明細と残高を集計して帳票（STATEMENT-REPORT）を出力する。顧客名・店名はマスタキャッシュで解決し、監査ログを AUD-WRITE に記録する。 |

---

## 2. 機能概要

### 2.1 このプログラムが「すること」
バッチ ID・営業日・出力モード（日次／月次）を入力として受け取り、DB から口座・取引・残高を読み出して帳票ファイルとサマリファイルを出力する。
処理件数・明細行数などの計測値を STMT-OUTPUT に返却し、AUD-WRITE で開始／終了の監査ログを残す。

### 2.2 呼出元と呼出し先
- **呼出元:** テストドライバ `STMT-TEST`。ジョブスケジューラ等からの `CALL "STMT-GENERATE-BATCH"` 呼出しを想定。
- **呼出先:** `AUD-WRITE`（[shared/util/aud-write](../../shared/util/aud-write/design/aud-write-bd.md)）。監査ログの書き出しを委譲する。

### 2.3 シーケンス図

```mermaid
sequenceDiagram
    participant caller as 呼出元
    participant self as STMT-GENERATE-BATCH
    participant db as DB (PostgreSQL)
    participant aud as AUD-WRITE

    caller->>self: STMT-INPUT にてバッチ ID / 営業日 / モード
    self->>db: CONNECT
    self->>db: マスタ読込 (customers, branches)
    self->>db: 口座スナップショット取得 (accounts)
    loop 各口座
        self->>db: 残高取得 (balances)
        self->>db: 取引明細取得 (transactions + postings)
        self->>self: 帳票明細行生成
    end
    self->>aud: CALL AUD-WRITE (START)
    self->>aud: CALL AUD-WRITE (END)
    self->>db: DISCONNECT
    self-->>caller: STMT-OUTPUT (status / 計測値)
```

---

## 3. 処理フロー

### 3.1 全体フロー（flowchart）

```mermaid
flowchart TD
    START([開始]) --> INIT[STMT-OUTPUT 初期化]
    INIT --> VALIDATE{入力妥当性チェック}
    VALIDATE -->|NG| INV[status = 08 で終了]
    VALIDATE -->|OK| DBCONN[DB CONNECT]
    DBCONN --> CONN_OK{接続成功?}
    CONN_OK -->|NG| IOFAIL[status = 12 で終了]
    CONN_OK -->|OK| BPERIOD[期間算出 / 日付フォーマット]
    BPERIOD --> LOAD_MAST[マスタキャッシュ読込]
    LOAD_MAST -->|CUSTCUR| CUST_LOOP[customers を 500 件まで FETCH]
    CUST_LOOP -->|BRCUR| BR_LOOP[branches を 100 件まで FETCH]
    BR_LOOP --> OPEN_RPT[帳票ファイル OPEN]
    OPEN_RPT --> OK_OPEN{OPEN 成功?}
    OK_OPEN -->|NG| IOFAIL2[status = 12 で終了]
    OK_OPEN -->|OK| AUD_START[AUD-WRITE START]
    AUD_START -->|ACCTCUR| ACCT_LOOP[accounts を 1000 件まで FETCH]
    ACCT_LOOP --> LOOP_ACCT{全口座ループ}
    LOOP_ACCT -->|各口座| RESOLVE[顧客名・店名キャッシュ解決]
    RESOLVE --> OP_BAL[opening balance 取得]
    OP_BAL --> CL_BAL[closing balance 取得]
    CL_BAL -->|TXNCUR| TXN_LOOP[transactions+postings を 500 件まで FETCH]
    TXN_LOOP --> EMIT[明細行 EMIT / GENERATE DETAIL-LINE]
    EMIT --> LOOP_ACCT
    LOOP_ACCT -->|完了| TERM[帳票 TERMINATE / CLOSE]
    TERM --> SUMMARY[サマリファイル出力]
    SUMMARY --> AUD_END[AUD-WRITE END]
    AUD_END --> POPOUT[STMT-OUTPUT 設定]
    POPOUT --> CLEANUP[DB DISCONNECT]
    CLEANUP --> END([終了])
    INV --> END
    IOFAIL --> END
    IOFAIL2 --> END
```

---

## 4. 入出力仕様

### 4.1 入力

| 項目 | 型 | 必須 | 説明 |
|------|-----|:----:|------|
| STMT-BATCH-ID | PIC X(14) | ✅ | バッチ実行情報を識別する ID |
| STMT-BUSINESS-DATE | PIC 9(8) | ✅ | 営業日（YYYYMMDD） |
| STMT-MODE | PIC X(1) | ✅ | "D"=日次 / "M"=月次 |
| STMT-OUTPUT-FILENAME | PIC X(80) | ✅ | 帳票ファイルパス |
| STMT-SUMMARY-FILENAME | PIC X(80) |  | サマリファイルパス（省略時は出力しない） |
| STMT-SKIP-INACTIVE | PIC X(1) |  | "Y"=取引なし口座をスキップ、"N"=取引なし明細を出力 |

### 4.2 出力

| 項目 | 型 | 説明 |
|------|-----|------|
| STMT-STATUS | PIC X(2) | 処理結果コード（下記返却コード参照） |
| STMT-OUT-ACCOUNTS-PROCESSED | PIC 9(7) | 明細を出力した口座数 |
| STMT-OUT-ACCOUNTS-EMPTY | PIC 9(7) | 取引なしで空明細を出力した口座数 |
| STMT-OUT-ACCOUNTS-SKIPPED | PIC 9(7) | 取引なしでスキップした口座数 |
| STMT-OUT-LINES-WRITTEN | PIC 9(10) | 出力明細行数 |
| STMT-OUT-PAGES-WRITTEN | PIC 9(7) | 出力ページ数 |
| STMT-OUT-BYTES-WRITTEN | PIC 9(12) | 出力バイト数 |
| STMT-OUT-DURATION-SEC | PIC 9(5) | 処理時間（秒） |

### 4.3 返却コード（概要）

| コード | 意味 |
|--------|------|
| 00 | 正常 |
| 04 | PARTIAL（一部失敗） |
| 08 | INVALID-INPUT（入力不正） |
| 12 | IO-FAIL（DB 接続／ファイル OPEN 失敗） |
| 16 | FATAL（重大エラー） |

---

## 5. 正常系テストケース

| # | テスト名 | 入力 | 期待出力 | 確認ポイント |
|---|---------|------|---------|------------|
| 1 | 日次モード正常系 | business=20260613, mode=D | status=00, accts>=5 | 5 件以上の口座が処理されること |
| 2 | 帳票ファイル出力 | mode=D | .rpt ファイルが存在し、"PRACTICE BANK STATEMENT" を含む | ページ見出しが出力されること |
| 3 | 明細行出力 | mode=D | lines-written >= 10 | 取引明細が 10 行以上出力されること |
| 4 | 開始残高出力 | mode=D | .rpt に "Opening Balance" を含む | 開始残高行が出力されること |
| 5 | 終了残高出力 | mode=D | .rpt に "Closing Balance" を含む | 終了残高行が出力されること |
| 6 | 空口座明細出力 | mode=D, skip=N | 口座 0010010099405 が .rpt に出現 | 取引なし口座のプレースホルダ行が出力されること |
| 7 | 月次モード正常系 | business=20260613, mode=M | status=00 | 月次モードで正常終了すること |
| 8 | 冪等性（再実行） | 同一パラメータを 2 回実行 | 2 回目の .rpt が 1 回目と同一 | 再実行でバイト同一の帳票が得られること |
| 9 | サマリファイル出力 | summary-filename 指定 | summary ファイルが存在し、バッチ ID / 件数を含む | サマリが出力されること |
| 10 | 監査ログ発行 | mode=D | audit_log に 17-statement のレコードが 2 件以上 | START / END の監査ログが出力されること |

---

## 6. 異常系テストケース

| # | テスト名 | 入力 | 期待される異常 | 確認ポイント |
|---|---------|------|--------------|------------|
| 1 | 不正モード | mode="X" | status = 08 | D/M 以外が拒否されること |
| 2 | 必須入力未設定 | batch-id または business-date 未設定 | status = 08 | 入力チェックが優先されること |
| 3 | DB 接続失敗 | DB 停止状態 | status = 12 | 接続不能時に IO-FAIL が返ること |
| 4 | 帳票 OPEN 失敗 | 出力先ディレクトリ不在 | status = 12 | ファイル OPEN 失敗時に後続処理が実行されないこと |

---

## 参考
- ソース: [stmt-generate-batch.sqb](../src/stmt-generate-batch.sqb)
- 公開 IF: [stmt-api.cpy](../copy/api/stmt-api.cpy)
- その他: [Makefile](../Makefile)
