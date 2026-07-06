# 基本設計書 — TXPOST-REPORT-SUMMARY

> **サブシステム:** 12-txnpost
> **プログラム ID:** `TXPOST-REPORT-SUMMARY`
> **種別:** バッチ
> **更新日:** 2026-07-06

---

## 1. プログラム概要

| 項目 | 値 |
|------|-----|
| プログラム ID | `TXPOST-REPORT-SUMMARY` |
| ソースファイル | `src/txpost-report-summary.cob` |
| 所属サブシステム | 12-txnpost |
| 種別 | バッチ |
| 概要 | TXPOST-RUN-BATCH が出力したサマリファイルを読み取り、人間可読なレポートファイルを生成する。サマリファイルが存在しない場合は PARTIAL として最小限のレポートを出力する。 |

---

## 2. 機能概要

### 2.1 このプログラムが「すること」
バッチ処理の結果を格納したサマリファイルを入力として受け付け、ヘッダ・生サマリデータ・保存性不変量検証結果・フッタを 1 ファイルにまとめたレポートを出力する。
サマリファイル不在時は PARTIAL ステータスで警告レポートを出力し、ファイル I/O 異常時は IO-FAIL を返す。

### 2.2 呼出元と呼出し先
- **呼出元:** バッチスケジューラまたは TXPOST-RUN-BATCH の後続処理。
- **呼出先:** なし（外部プログラム呼出しは行わない）。

### 2.3 シーケンス図

```mermaid
sequenceDiagram
    participant caller as 呼出元
    participant self as TXPOST-REPORT-SUMMARY
    participant sumfile as サマリファイル
    participant rptfile as レポートファイル

    caller->>self: TXPOST-REPORT-INPUT (batch-id / filenames)
    self->>sumfile: OPEN INPUT
    alt ファイル不在 (fs=35)
        self-->>caller: TXPS-PARTIAL + 警告レポート
    else 読取り可能
        self->>sumfile: SCAN-LOOP (EOF まで読取り)
        self->>rptfile: OPEN OUTPUT
        self->>rptfile: WRITE-FULL-REPORT (ヘッダ + 生データ + フッタ)
        self-->>caller: TXPS-OK / TXPS-PARTIAL
    end
```

---

## 3. 処理フロー

### 3.1 全体フロー（flowchart）

```mermaid
flowchart TD
    START([開始]) --> INIT[INIT-OUTPUT: 出力初期化]
    INIT --> OPEN_IN[OPEN INPUT サマリファイル]
    OPEN_IN --> CHK_FS{FS-IN 判定}
    CHK_FS -->|35 不在| MINIMAL[WRITE-MINIMAL-REPORT: 警告出力]
    MINIMAL --> RET_PARTIAL[TXPS-PARTIAL]
    CHK_FS -->|00| SCAN[SCAN-LOOP: EOF まで読取り]
    CHK_FS -->|OTHER| RET_IO[TXPS-IO-FAIL]
    SCAN --> CLOSE_IN[CLOSE サマリファイル]
    CLOSE_IN --> OPEN_OUT[OPEN OUTPUT レポートファイル]
    OPEN_OUT --> CHK_OUT{FS-OUT 判定}
    CHK_OUT -->|00| FULL[WRITE-FULL-REPORT: ヘッダ + ECHO + フッタ]
    CHK_OUT -->|OTHER| RET_IO
    FULL --> CLOSE_OUT[CLOSE レポートファイル]
    CLOSE_OUT --> CHK_DATA{データ有無}
    CHK_DATA -->|あり| RET_OK[TXPS-OK / conservation-ok = Y]
    CHK_DATA -->|なし| RET_PARTIAL2[TXPS-PARTIAL / conservation-ok = ?]
    RET_OK --> END([終了])
    RET_PARTIAL --> END
    RET_PARTIAL2 --> END
    RET_IO --> END
```

---

## 4. 入出力仕様

### 4.1 入力

| 項目 | 型 | 必須 | 説明 |
|------|-----|:----:|------|
| TXPS-BATCH-ID | PIC X(14) | ✅ | バッチ ID。レポートヘッダに出力される |
| TXPS-SUMMARY-FILENAME | PIC X(80) | ✅ | 入力サマリファイルパス |
| TXPS-REPORT-FILENAME | PIC X(80) | ✅ | 出力レポートファイルパス |

### 4.2 出力

| 項目 | 型 | 説明 |
|------|-----|------|
| TXPS-STATUS | PIC X(2) | 処理結果コード（下記返却コード参照） |
| TXPS-LINES-WRITTEN | PIC 9(5) | レポートに書き込んだ行数 |
| TXPS-CONSERVATION-OK | PIC X(1) | 保存性不変量フラグ（"Y" / "N" / "?"） |

### 4.3 返却コード（概要）

| コード | 意味 |
|--------|------|
| 00 | 正常（レポート生成完了） |
| 04 | PARTIAL（サマリファイル不在、またはデータなし） |
| 12 | IO-FAIL（ファイル OPEN 失敗） |
| 16 | FATAL（予期しない致命的エラー） |

---

## 5. 正常系テストケース

| # | テスト名 | 入力 | 期待出力 | 確認ポイント |
|---|---------|------|---------|------------|
| 1 | サマリファイル存在・データあり | batch-id / 有効なサマリパス / レポートパス | status=00, conservation-ok=Y, lines>0 | ヘッダ・生データ・不変量検証・フッタがすべて出力されること |
| 2 | サマリファイル存在・データ空 | batch-id / 空サマリパス / レポートパス | status=04, conservation-ok=? | データ不在でもレポートは生成され、"?" が返ること |
| 3 | サマリファイル不在 | batch-id / 存在しないパス / レポートパス | status=04, conservation-ok=? | fs=35 で検知し、警告レポートを出力すること |

---

## 6. 異常系テストケース

| # | テスト名 | 入力 | 期待される異常 | 確認ポイント |
|---|---------|------|--------------|------------|
| 1 | サマリファイル OPEN 失敗（権限等） | 読取不可パス | status=12 | fs が 00/35 以外の時に IO-FAIL が返ること |
| 2 | レポートファイル OPEN 失敗 | 書込不可パス | status=12 | 出力ファイルの fs 異常時に IO-FAIL が返ること |

---

## 参考
- ソース: [txpost-report-summary.cob](../src/txpost-report-summary.cob)
- 公開 IF: [tx-post-api.cpy](../copy/api/tx-post-api.cpy)
- その他: [Makefile](../Makefile)
