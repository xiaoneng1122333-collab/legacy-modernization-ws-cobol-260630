# 基本設計書 — BR-LIST-BY-REGION

> **サブシステム:** 02-branch
> **プログラム ID:** `BR-LIST-BY-REGION`
> **種別:** オンライン（反復 CALL 呼出モジュール）
> **更新日:** 2026-07-06

---

## 1. プログラム概要

| 項目 | 値 |
|------|-----|
| プログラム ID | `BR-LIST-BY-REGION` |
| ソースファイル | `src/br-list-by-region.cob` |
| 所属サブシステム | 02-branch |
| 種別 | オンライン（反復 CALL 呼出モジュール） |
| 概要 | 地域コードを指定して同一地域の支店の一括取得を行う呼出モジュール。初回 CALL 時に ISAM ファイルをオープンして代替キー (BR-REC-REGION) による START 位置を定め、以降の CALL で呼出元単位に 1 件ずつ READ NEXT により末尾までフェッチする。末尾到達時は EOF を返す。 |

---

## 2. 機能概要

### 2.1 このプログラムが「すること」
地域コード (BR-IN-REGION) を入力とし、ISAM インデックスファイル (branch.idx) の代替キー (BR-REC-REGION WITH DUPLICATES) をシーケンシャルにスキャンして、同一地域コードに属する支店レコードを 1 件ずつ出力に設定して呼び出し元へ返す。初回到達までは START で先頭検索位置を確定し、以後は READ NEXT で順次取得する。最終レコードまでフェッチした時点で 10 (EOF) を返して反復を終了するよう呼出元へ通知する。

### 2.2 呼出元と呼出し先
- **呼出元:** テストドライバ `BRTEST` の PERFORM ループ。オンライントランザクション、または画面プログラムを想定。
- **呼出先:** なし（独立モジュール。他プログラムを CALL しない）。

### 2.3 シーケンス図

```mermaid
sequenceDiagram
    participant caller as 呼出元（BRTEST 等）
    participant self as BR-LIST-BY-REGION
    participant idx as branch.idx

    caller->>self: 初回 CALL (BR-IN-OP="R", BR-IN-REGION="Tokyo")
    alt 初回呼出でファイル未オープン
        self->>idx: OPEN INPUT
        idx-->>self: WS-FS
        alt WS-FS NOT = "00"
            self-->>caller: 16 FATAL,
        else オープン成功
            self->>self: WS-OPEN-FLAG = "Y"
        end
    end

    self->>self: BR-IN-REGION → BR-REC-REGION
    self->>self: WS-CURRENT-REGION = BR-IN-REGION
    self->>idx: START BRANCH-FILE KEY = BR-REC-REGION
    alt INVALID KEY
        self-->>caller: 10 EOF
    else 成功
        loop READ NEXT → 同一地域間を反復
            self->>idx: READ BRANCH-FILE NEXT
            alt AT END
                self-->>caller: 10 EOF
            else BR-REC-REGION ≠ WS-CURRENT-REGION
                self-->>caller: 10 EOF
            else 同一地域レコード
                caller->>self: 次回 CALL (BR-IN-OP=スペース)
                self-->>caller: 00 + BR-OUT-CODE + 各出力項目
            end
        end
    end
```

---

## 3. 処理フロー

### 3.1 全体フロー（flowchart）

```mermaid
flowchart TD
    START([開始：MAIN-LOGIC]) --> INIT[BR-OUT-STATUS = 00]
    INIT --> OPENCHK{WS-OPEN-FLAG = "N"?}
    OPENCHK -->|Yes| OPEN[BRANCH-FILE OPEN INPUT]
    OPEN --> FSCHK{WS-FS = "00"?}
    FSCHK -->|No| FATAL[16 FATAL]
    FSCHK -->|Yes| FLAG[WS-OPEN-FLAG = "Y"]
    FLAG --> OPCHK
    OPENCHK -->|No| OPCHK{BR-IN-OP = "R"?}
    OPCHK -->|Yes| REGMV[BR-IN-REGION → BR-REC-REGION />WS-CURRENT-REGION]
    REGMV --> START[START KEY = BR-REC-REGION]
    START --> STARTFAIL{INVALID KEY?}
    STARTFAIL -->|Yes| EOF1[10 EOF]
    STARTFAIL -->|No| READ
    OPCHK -->|No| READ[BRANCH-FILE READ NEXT]
    READ --> ATEND{AT END?}
    ATEND -->|Yes| EOF2[10 EOF]
    ATEND -->|No| REGCHK{BR-REC-REGION<br/>≠ WS-CURRENT-REGION?}
    REGCHK -->|Yes| EOF3[10 EOF（次地域境界）]
    REGCHK -->|No| OUT[BR-REC → BR-OUT 転記 / STATUS = 00]
    FATAL --> END([GOBACK])
    EOF1 --> END
    EOF2 --> END
    EOF3 --> END
    OUT --> END
```

---

## 4. 入出力仕様

### 4.1 入力

| 項目 | 型 | 必須 | 説明 |
|------|-----|:----:|------|
| BR-IN-CODE | PIC X(3) | | 支店コード（本モジュールでは不使用） |
| BR-IN-REGION | PIC X(20) | ✅ | 検索対象の地域コード。初回到達時に必ず指定する。2 回目以降の CALL では参照しない。 |
| BR-IN-OP | PIC X(1) | ✅ | 操作コード。"R" を指定した CALL 時に START を行う。2 回目以降は " " を指定する。 |

※ ファイル仕様: `data/branch.idx`（INDEXED, DYNAMIC, 主キー=BR-REC-CODE, 代替キー=BR-REC-REGION WITH DUPLICATES）。

### 4.2 出力

| 項目 | 型 | 説明 |
|------|-----|------|
| BR-OUT-STATUS | PIC 9(2) | 返却コード（00/10/16 等） |
| BR-OUT-CODE | PIC X(3) | 支店コード |
| BR-OUT-NAME-KANJI | PIC X(40) | 支店名（漢字） |
| BR-OUT-NAME-KANA | PIC X(40) | 支店名（カナ） |
| BR-OUT-REGION | PIC X(20) | 地域コード |
| BR-OUT-STATUS-CODE | PIC X(1) | 支店状態コード |

### 4.3 返却コード（概要）

| コード | 意味 |
|--------|------|
| 00 | 正常（同一地域の支店 1 件を返却） |
| 10 | EOF（同一地域の末端に到達または初回で該当地域なし） |
| 16 | FATAL（branch.idx オープン失敗） |

---

## 5. 正常系テストケース

| # | テスト名 | 入力 | 期待出力 | 確認ポイント |
|---|---------|------|---------|------------|
| 1 | 東京の支店を 4 件フェッチ | BR-IN-REGION="Tokyo", BR-IN-OP="R" で開始し、その後 BR-IN-OP=" " で反復 | 4 回の STATUS=00、5 回目で STATUS=10。返却コードはTokyo のレコード数と一致 | START → READ NEXT の反復で同一地域のみ返り、次地域境界で 10 が返ること |
| 2 | "R" を毎回指定した場合 | 毎回 BR-IN-OP="R" で CALL | いずれも該当地域を返却 | "R" は毎呼ごとに再 START を実行し、カーソルが先頭に戻る仕様であること（副作用として二重開始） |
| 3 | "Osaka" 地域コードの開始→反復 | BR-IN-REGION="Osaka" で開始 → 反復 | 取得レコードがすべて BR-OUT-REGION="Osaka" | 地域境界チェックにより東京レコードは返らないこと |

---

## 6. 異常系テストケース

| # | テスト名 | 入力 | 期待される異常 | 確認ポイント |
|---|---------|------|--------------|------------|
| 1 | 存在しない地域コード | BR-IN-REGION="FukuokaX"（未定義）, BR-IN-OP="R" | STATUS=10 (EOF) | START INVALID KEY を検知し、即座に EOF を返すこと |
| 2 | ファイル未生成の branch.idx | branch.idx 不在で "R" 呼出 | STATUS=16 (FATAL) | 初回 OPEN 失敗を検知して即座に FATAL で返ること |
| 3 | AT END（EOF 後の再 READ） | STATUS=10 を返した直後に再度 CALL | STATUS=10 | ISAM の末尾に到達した READ NEXT は EOF を返すこと |

---

## 参考
- ソース: [br-list-by-region.cob](../src/br-list-by-region.cob)
- 公開 IF: [br-api.cpy](../copy/api/br-api.cpy)
- ファイル定義: [fd-branch.cpy](../copy/private/fd-branch.cpy)
- ビルド/テスト定義: [Makefile](../Makefile)
