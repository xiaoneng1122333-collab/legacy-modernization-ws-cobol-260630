# 基本設計書 — CUST-SEARCH-BY-KANA

> **サブシステム:** 03-customer
> **プログラム ID:** `CUST-SEARCH-BY-KANA`
> **種別:** オンライン
> **更新日:** 2026-07-06

---

## 1. プログラム概要

| 項目 | 値 |
|------|-----|
| プログラム ID | `CUST-SEARCH-BY-KANA` |
| ソースファイル | `src/cust-search-by-kana.cob` |
| 所属サブシステム | 03-customer |
| 種別 | オンライン |
| 概要 | 顧客カナ名の前方一致検索を行うカーソル型検索。`customer.idx` の代替キー（CR-KANA）を用いて検索キーの先頭位置を特定し、以降 1 件ずつカナ名が一致するレコードを順次返却する。一致しなくなった時点で EOF ステータスを返す。 |

---

## 2. 機能概要

### 2.1 このプログラムが「すること」
顧客カナ名の前方一致検索を行い、1 回の呼出で 1 件の顧客情報を返却する。
- 初回（`CUST-IN-OP = "K"`）：検索キー（カナ）を代替キーに設定し START KEY >= で先頭位置を特定、先頭レコードを返却する。
- 2 回目以降（`CUST-IN-OP = " "`）：次レコードを READ NEXT で返却する。
- カナ不一致 / EOF 到達時：ステータス 10 (EOF) を返却する。

### 2.2 呼出元と呼出し先
- **呼出元:** オンライントランザクション、またはテストドライバ `CUSTTEST`。
- **呼出先:** なし（外部プログラム呼出なし）。ISAM ファイルに直接アクセスする。

### 2.3 シーケンス図

```mermaid
sequenceDiagram
    participant caller as 呼出元
    participant self as CUST-SEARCH-BY-KANA
    participant idx as customer.idx

    caller->>self: CUST-INPUT (OP="K", KANA="タナカ")
    self->>idx: OPEN INPUT
    self->>idx: START KEY >= CR-KANA
    self->>idx: READ NEXT
    alt 前方一致
        idx-->>self: CUST-REC
        self-->>caller: CUST-OUTPUT (status=00, 一致レコード)
    else 不一致 / EOF
        self-->>caller: CUST-OUTPUT (status=10)
    end

    loop 一致し続ける限り
        caller->>self: CUST-INPUT (OP=" ")
        self->>idx: READ NEXT
        alt 前方一致
            idx-->>self: CUST-REC
            self-->>caller: CUST-OUTPUT (status=00)
        else 不一致 / EOF
            self-->>caller: CUST-OUTPUT (status=10)
        end
    end
```

---

## 3. 処理フロー

### 3.1 全体フロー（flowchart）

```mermaid
flowchart TD
    START([開始]) --> INIT[CUST-OUTPUT 初期化 (status=0)]
    INIT --> OPEN_CHK{OPEN 済?}
    OPEN_CHK -->|No| OPEN[OPEN INPUT customer.idx]
    OPEN --> OPEN_RES{FS = 00?}
    OPEN_RES -->|No| ERR_FATAL[status = 16 で GOBACK]
    OPEN_RES -->|Yes| OP_CHK
    OPEN_CHK -->|Yes| OP_CHK{CUST-IN-OP?}

    OP_CHK -->|K 先頭設定| START[START KEY >= CR-KANA]
    START --> START_RES{INVALID KEY?}
    START_RES -->|Yes| ERR_EOF[status = 10 で GOBACK]
    START_RES -->|No| READ[READ NEXT]

    OP_CHK -->|その他| READ
    READ --> EOF_CHK{AT END?}
    EOF_CHK -->|Yes| RET_EOF[status = 10 で GOBACK]
    EOF_CHK -->|No| KANA_CHK{KANA 前方一致?}
    KANA_CHK -->|Yes| COPY[レコード → CUST-OUTPUT]
    COPY --> OK[status = 00]
    KANA_CHK -->|No| RET_EOF2[status = 10 で GOBACK]
    OK --> END([GOBACK])
    RET_EOF --> END
    RET_EOF2 --> END
    ERR_FATAL --> END
    ERR_EOF --> END
```

---

## 4. 入出力仕様

### 4.1 入力

| 項目 | 型 | 必須 | 説明 |
|------|-----|:----:|------|
| CUST-IN-KANA | PIC X(50) | ✅ (OP="K" 時) | 検索する顧客カナ名（前方一致） |
| CUST-IN-OP | PIC X(1) | ✅ | 操作コード。"K"=先頭位置付け、" "=次レコード読取 |

※ 入力エリア `CUST-INPUT` は `cust-api.cpy` で定義される。

### 4.2 出力

| 項目 | 型 | 説明 |
|------|-----|------|
| CUST-OUT-STATUS | PIC 9(2) | 処理結果コード（下記返却コード参照） |
| CUST-OUT-ID | PIC 9(10) | 顧客 ID |
| CUST-OUT-KANA | PIC X(50) | 顧客カナ名 |
| CUST-OUT-KANJI | PIC X(60) | 顧客漢字名 |
| CUST-OUT-PHONE | PIC X(15) | 電話番号 |
| CUST-OUT-ADDRESS | PIC X(200) | 住所 |
| CUST-OUT-STATUS-CODE | PIC X(1) | 顧客ステータス |

### 4.3 返却コード（概要）

| コード | 意味 |
|--------|------|
| 00 | 正常（前方一致レコード返却） |
| 10 | EOF（スキャン終了 — CASE-EOF・不一致のいずれか） |
| 16 | FATAL（ファイルオープン失敗） |

---

## 5. 正常系テストケース

| # | テスト名 | 入力 | 期待出力 | 確認ポイント |
|---|---------|------|---------|------------|
| 1 | カナ前方一致（多数） | OP="K", KANA="タナカ" | 1 件以上の一致レコード（status=00） | 該当顾客が順次返ること |
| 2 | 次レコードの継続読取 | OP=" " で順次スキャン | カナ名が前方一致する限り status=00、不一致で status=10 | カナ変わりの境界でスキャンが打ち切られること |

---

## 6. 異常系テストケース

| # | テスト名 | 入力 | 期待される異常 | 確認ポイント |
|---|---------|------|--------------|------------|
| 1 | カナ完全不一致 | OP="K", KANA="ン"（存在しない） | status = 10 | START 直後に一致せず EOF が返ること |
| 2 | ファイル未配置 | customer.idx を削除 or リネーム | status = 16 | OPEN INPUT 失敗時に FATAL が返ること |

---

## 参考
- ソース: [cust-search-by-kana.cob](../src/cust-search-by-kana.cob)
- 公開 IF: [cust-api.cpy](../copy/api/cust-api.cpy)
- ファイル定義: [fd-customer.cpy](../copy/private/fd-customer.cpy)
- テスト: [cust-test.cob](../tests/unit/cust-test.cob)
- ビルド/実行定義: [Makefile](../Makefile)
