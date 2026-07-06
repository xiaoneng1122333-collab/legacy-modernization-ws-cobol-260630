# カットオーバーランブック

> **対象:** COBOL → Java + AWS モダナイズ Phase 3 (カットオーバー)
> **前提:** Phase 1 (基盤構築)・Phase 2 (機能移植) 完了済み
> **最終更新:** 2026-07-06

---

## 1. はじめに

### 1.1 目的

本事例書は、レガシー COBOL システムを Java (Spring Boot + Spring Batch + MyBatis) + AWS (ECS Fargate + Step Functions + Aurora PostgreSQL) へ段階的に切り替える手順を定義する。80 プログラム / 22 サブシステムのカットオーバーを、データ整合性を損なうリスクを最小化しながら実行することが目的。

### 1.2 スコープ

- **対象システム**: 80 COBOL プログラム (オンライン 2 + バッチ 17 + マスタ 8 + 統合 3)
- **基盤":** ECS Fargate / Step Functions / Aurora PostgreSQL / API Gateway / SQS / S3
- **データソース**: ISAM → Aurora PostgreSQL 移行済み (dual-schema: `cobol` / `java` / `shared`)
- **並行検証**: Comparator Service による COBOL/Java 出力の自動差分チェック

### 1.3 前提条件

| # | 前提 | 検証方法 |
|---|------|----------|
| P1 | AWS アカウント・VPC・Subnet が作成済み | `terraform plan` 差分なし |
| P2 | Aurora PostgreSQL / ElastiCache が稼働 | `aws rds describe-db-instances` |
| P3 | Phase 1 チェックリストが PASS | `docs/runbooks/phase1-checklist.md` |
| P4 | Phase 2 の 22 サブシステムがデプロイ済み | ECS サービス `running` |
| P5 | データ移行が完了 (ISAM → Aurora) し row count 一致 | `batch/isam-to-rds-job` 成功 |
| P6 | Comparator Service が稼働 (差分 N 件連続 0) | `/api/comparator/summary` |
| P7 | 運用手順・監視ダッシュボードが整備済み | Grafana / CloudWatch |
| P8 | COBOL 側タイマーが停止可能な状態 | systemd 一覧確認 |

---

## 2. カットオーバーチェックリスト (Go/No-Go)

カットオーバーの **72 時間前** に実施。**全項目 PASS でなければ D-Day を延期**。

| # | 項目 | 基準 | 結果 | 確認者 |
|---|------|------|------|--------|
| G1 | 全単体テスト (JUnit) が GREEN | `./gradlew test` pass rate 100% | ☐ | |
| G2 | 全統合テスト (Testcontainers) が GREEN | `@SpringBootTest` pass rate 100% | ☐ | |
| G3 | Comparator Service 差分が **N 件連続 0** | 72 時間 × 3 回/日 の連続 0 件 | ☐ | |
| G4 | バッチパフォーマンス基準クリア | 日次バッチ < 4 時間 (Step Functions) | ☐ | |
| G5 | データベース整合性チェック | Aurora ↔ ISAM row count 誤差 0 | ☐ | |
| G6 | Rollback 手順のリハーサル実施 | 手順書どおり切り戻し可能 | ☐ | |
| G7 | オンコール体制の構築 | 3 人在籍 (アプリ/インフラ/データ) | ☐ | |
| G8 | 運用チーム承認 | リーダー署名済み | ☐ | |
| G9 | ステークホルダーへの通知 | 72 時間前のメール送信済み | ☐ | |
| G10 | 本番データバックアップ完了 | Aurora スナップショット作成 | ☐ | |

> **判定**: 全 ☐ が ☑ なら **Go**、1 つでも ☐ なら **No-Go (延期)**。

---

## 3. 段階的切り替え手順

### 全体タイムライン

```
D-7        D-3        D-2        D-1        D-Day      D+1        D+7        D+30
 │          │          │          │          │          │          │          │
 ▼          ▼          ▼          ▼          ▼          ▼          ▼          ▼
 Step 1     Step 2                Step 2     Step 2     Step 3     検証       モニタリング
 マスタ     バッチ前半            バッチ停止  COBOL停止  オンライン   完了       完了
 切り替え   (日次バッチ群)         準備       切り替え    切り替え    ゲート      ゲート
```

### Step 1: マスタデータ切り替え (D-7)

**目的**: 8 マスタシステムの参照先を ISAM → Aurora に切り替え。

#### 1.1 切り替え対象

| # | サブシステム | ISAM キー | Aurora テーブル | 検証クエリ |
|---|-------------|-----------|-----------------|------------|
| 01 | calendar-service | CAL-MST | `shared.calendar` | `SELECT COUNT(*) FROM shared.calendar` |
| 02 | branch-service | BRN-MST | `shared.branch` | `SELECT COUNT(*) FROM shared.branch` |
| 03 | customer-service | CUS-MST | `shared.customer` | `SELECT COUNT(*) FROM shared.customer` |
| 05 | product-service | PRD-MST | `shared.product` | `SELECT COUNT(*) FROM shared.product` |
| 06 | interestrate-service | INT-MST | `shared.interest_rate` | `SELECT COUNT(*) FROM shared.interest_rate` |
| 07 | feeschedule-service | FEE-MST | `shared.fee_schedule` | `SELECT COUNT(*) FROM shared.fee_schedule` |
| 08 | account-service | ACT-MST | `shared.account` | `SELECT COUNT(*) FROM shared.account` |

※ 04-currencyservice は D-3 に含めても可 (取引時間外で影響小)。

#### 1.2 実行手順

```bash
# 1. COBOL 側マスタ更新を停止 (ISAM 書込み禁止)
sudo systemctl stop cobol-master-sync.timer

# 2. Aurora 側スキーマで最新データ確認
psql -h $AURORA_ENDPOINT -U $DB_USER -d bankdb \
  -c "SELECT 'calendar' AS tbl, COUNT(*) FROM shared.calendar
      UNION ALL SELECT 'branch', COUNT(*) FROM shared.branch
      UNION ALL SELECT 'customer', COUNT(*) FROM shared.customer
      UNION ALL SELECT 'product', COUNT(*) FROM shared.product
      UNION ALL SELECT 'interest_rate', COUNT(*) FROM shared.interest_rate
      UNION ALL SELECT 'fee_schedule', COUNT(*) FROM shared.fee_schedule
      UNION ALL SELECT 'account', COUNT(*) FROM shared.account;"

# 3. ISAM row count と比較 (誤差 0 を確認)
#    値が一致しない場合はデータ移行ジョブを再実行:
#    aws stepfunctions start-execution --state-machine-arn $ISAM_RDS_SFN --input '{}'

# 4. Spring Boot サービスで Aurora スキーマを読み取る設定を Aurora に変更
#    application.yml: spring.profiles.active=cutover
#    (cobol スキーマ → java スキーマへ)

# 5. ECS サービスを再デプロイ (task definition 更新)
aws ecs update-service --cluster bank-cluster \
  --service master-services \
  --force-new-deployment

# 6. 各サービスヘルスチェック
for svc in calendar branch customer product interestrate feeschedule account; do
  curl -f "https://master-internal.bank.example/actuator/health" && echo "$svc OK"
done

# 7. Comparator Service でマスタ差分確認 (0 件を確認)
curl -s "https://comparator.bank.example/api/comparator/summary?domain=master" \
  | jq '.totalDiffs'   # → 0 であること
```

#### 1.3 検証

- [ ] 全 Aurora テーブルの row count が ISAM と一致
- [ ] 全 Spring Boot サービスが `UP` (actuator)
- [ ] Comparator Service のマスタ差分が 0 件
- [ ] オンライン照会でマスタデータが正しく返却

---

### Step 2: バッチ切り替え (D-3 → D-Day)

**Purpose**: 17 バッチの実行を COBOL → Step Functions に切り替え。依存順に段階実行。

#### 2.1 バッチ依存グラフ

```
19-integrationin (外部入力取り込み)
  │
  ▼
10-txnvalidate (取引検証)
  │
  ▼
11-txnsortmerge (取引ソート/マージ)
  │
  ▼
12-txnpost (取引転記)
  │
  ├──▶ 13-interestaccurual (利息計上)
  │      │
  │      ▼
  │    14-interestpost (利息転記)
  │
  ├──▶ 15-autodebit (自動引落)
  │
  ├──▶ 16-fee (手数料計算)
  │
  ▼
17-statement (明細作成)
  │
  ▼
20-integrationout (外部出力)
  │
  ▼
21-audit (監査証跡)
  │
  ▼
22-operations (運用レポート)
```

#### 2.2 段別実行スケジュール

| 日程 | ステップ | COBOL 停止 | Step Functions 起動 | 検証 |
|------|----------|-----------|---------------------|------|
| D-3 | integrationin → txnvalidate | ✅ | 実行 | Comparator 差分 0 |
| D-2 | txnsortmerge → txnpost | ✅ | 実行 | Comparator 差分 0 |
| D-2 | interestaccrual → interestpost | ✅ | 実行 | Comparator 差分 0 |
| D-1 | autodebit → fee → statement | ✅ | 実行 | Comparator 差分 0 |
| D-Day | integrationout → audit → operations | 完全停止 | 全起動 | Comparator 差分 0 |

#### 2.3 実行手順 (1 バッチ切り替えのテンプレート)

```bash
#!/bin/bash
BATCH_ID="$1"   # e.g. 10-txnvalidate
COBOL_UNIT="cobol-${BATCH_ID}.timer"
SFN_ARN="$BATCH_ID-sfn"

# 1. COBOL 側タイマーを停止
sudo systemctl stop "$COBOL_UNIT"
sudo systemctl disable "$COBOL_UNIT"

# 2. Step Functions を起動
EXEC_ARN=$(aws stepfunctions start-execution \
  --state-machine-arn "$SFN_ARN" \
  --name "cutover-$(date +%Y%m%d%H%M%S)" \
  --query executionArn --output text)

echo "Step Functions execution: $EXEC_ARN"

# 3. 完了待ち (最大 4 時間)
aws stepfunctions describe-execution \
  --execution-arn "$EXEC_ARN" \
  --query status

# 4. 結果検証
while true; do
  STATUS=$(aws stepfunctions describe-execution \
    --execution-arn "$EXEC_ARN" \
    --query status --output text)
  if [ "$STATUS" = "SUCCEEDED" ]; then
    echo "Batch $BATCH_ID: SUCCESS"
    break
  elif [ "$STATUS" = "FAILED" ] || [ "$STATUS" = "TIMED_OUT" ]; then
    echo "Batch $BATCH_ID: $STATUS → Rollback required!"
    exit 1
  fi
  sleep 30
done

# 5. Comparator Service で差分確認 (N=0 連続)
DIFF=$(curl -s "https://comparator.bank.example/api/comparator/diff?batch=$BATCH_ID" \
  | jq '.diffCount')
if [ "$DIFF" -ne 0 ]; then
  echo "Comparator diff = $DIFF → Rollback required!"
  exit 1
fi
echo "Batch $BATCH_ID: diff=0, OK"
```

#### 2.4 バッチ切り替え完了ゲート

- [ ] 全 17 バッチの Step Functions 実行が `SUCCEEDED`
- [ ] Comparator Service 差分が 0 件 (各バッチ)
- [ ] バッチ実行時間 < 4 時間
- [ ] COBOL systemd タイマーが全停止
- [ ] 日次帳票が正常出力 (S3 バケットに格納)

---

### Step 3: オンライン切り替え (D-Day + 1)

**Purpose**: オンライン取引 (照会/ライフサイクル) のルーティングを切り替え。

#### 3.1 切り替え対象

| # | サブシステム | COBOL CICS | Spring Boot サービス |
|---|-------------|-----------|---------------------|
| 18 | inquiry-service | INQPGM | inquiry-service (ECS) |
| 09 | accountlifecycle-service | ACTLIF | accountlifecycle-service (ECS) |

#### 3.2 実行手順

```bash
# 1. COBOL CICS トランザクションをクローズ
#    (CICS 運用ツールで disable)
cdat disable trx(INQPGM)
cdat disable trx(ACTLIF)

# 2. API Gateway ルーティングを Spring Boot に切り替え
aws apigateway update-base-path-mapping \
  --domain-name api.bank.example \
  --base-path v1 \
  --patch-operations op=replace,path=/restApiId,value=$JAVA_REST_API_ID

# 3. ECS サービスを確認
aws ecs describe-services \
  --cluster bank-cluster \
  --services inquiry-service accountlifecycle-service \
  --query 'services[*].{name:serviceName,status:status,running:runningCount,desired:desiredCount}'

# 4. ヘルスチェック
curl -f https://api.bank.example/v1/inquiry/health
curl -f https://api.bank.example/v1/account/health

# 5. スモークテスト (取引)
curl -X POST https://api.bank.example/v1/inquiry/balance \
  -H "Authorization: Bearer $TEST_TOKEN" \
  -d '{"accountId":"TEST-001"}' \
  | jq '.balance'

# 6. Comparator Service でオンライン応答差分確認
curl -s "https://comparator.bank.example/api/comparator/diff?domain=online" \
  | jq '.diffCount'   # → 0 であること
```

#### 3.3 オンライン切り替え完了ゲート

- [ ] API Gateway ルーティングが Spring Boot に切り替わり
- [ ] inquiry-service / accountlifecycle-service が `UP`
- [ ] スモークテスト成功
- [ ] Comparator Service 差分が 0 件
- [ ] COBOL CICS トランザクションが全クローズ

---

## 4. ロールバック手順

### 4.1 検出条件 (Rollback Triggers)

**いずれか 1 つでも該即 → 即時ロールバック開始**:

| # | 条件 | 深刻度 | 検出方法 |
|---|------|--------|----------|
| R1 | Comparator Service 差分 > **10 件** (5 分以内) | CRITICAL | CloudWatch Alarm |
| R2 | バッチ Step Functions が `FAILED` / `TIMED_OUT` | CRITICAL | Step Functions 通知 |
| R3 | オンライン応答エラー率 > **1%** | CRITICAL | API Gateway 5xx |
| R4 | バッチ実行時間 > **4 時間** | HIGH | Step Functions 所要時間 |
| R5 | オンラインレイテンシ P99 > **3 秒** | HIGH | CloudWatch |
| R6 | データベース row count 誤差 > **0** | CRITICAL | DB check ジョブ |

### 4.2 ロールバック手順

ロールバックは **検出から 30 分以内** に完了させる。

```bash
#!/bin/bash
# rollback.sh — COBOL タイマー再有効化

echo "=== ROLLBACK START: $(date) ==="

# 1. COBOL systemd タイマーを再有効化
for unit in cobol-integrationin.timer cobol-txnvalidate.timer \
            cobol-txnsortmerge.timer cobol-txnpost.timer \
            cobol-interestaccrual.timer cobol-interestpost.timer \
            cobol-autodebit.timer cobol-fee.timer cobol-statement.timer \
            cobol-integrationout.timer cobol-audit.timer cobol-operations.timer; do
  sudo systemctl enable "$unit"
  sudo systemctl start "$unit"
  echo "Restored: $unit"
done

# 2. CICS トランザクション再有効化
cdat enable trx(INQPGM)
cdat enable trx(ACTLIF)

# 3. API Gateway を COBOL にルーティング
aws apigateway update-base-path-mapping \
  --domain-name api.bank.example \
  --base-path v1 \
  --patch-operations op=replace,path=/restApiId,value=$COBOL_REST_API_ID

# 4. ECS サービスをスケールダウン (オプション)
aws ecs update-service --cluster bank-cluster \
  --service inquiry-service --desired-count 0
aws ecs update-service --cluster bank-cluster \
  --service accountlifecycle-service --desired-count 0

echo "=== ROLLBACK COMPLETE: $(date) ==="
```

### 4.3 データ整合性確認 (ロールバック後)

```sql
-- 1. Aurora の java スキーマでロールバック中の書き込みを特定
SELECT COUNT(*) FROM java.transaction_log WHERE updated_at > NOW() - INTERVAL '1 hour';

-- 2. COBOL/ISAM 同期を再開
--    master-sync.timer を再起動
sudo systemctl restart cobol-master-sync.timer

-- 3. 再同期ジョブ実行
aws stepfunctions start-execution \
  --state-machine-arn "$RESYNC_SFN_ARN"

-- 4. 整合性チェック (row count)
SELECT 'ISAM' AS source, COUNT(*) FROM cobol.account
UNION ALL
SELECT 'Aurora', COUNT(*) FROM shared.account;
```

### 4.4 ロールバック完了ゲート

- [ ] systemd タイマーが再稼働
- [ ] CICS トランザクションが `ENABLED`
- [ ] API Gateway が COBOL に向いている
- [ ] Comparator Service が正常稼働 (差分モニタリング再開)
- [ ] 整合性チェック完了

---

## 5. 検証手順

### 5.1 Comparator Service による自動差分検証

| フェーズ | 検証対象 | 許容差分 | 頻度 |
|---------|----------|----------|------|
| D-Day ~ D+1 | 全バッチ出力 | 0 件 | 全実行 |
| D+1 ~ D+7 | オンライン応答 | 0 件 | リアルタイム |
| D+7 ~ D+30 | マスタ参照 | 0 件 | 1 時間毎 |

```bash
# 日次検査スクリプト
cutover/verify.sh --date $(date +%Y%m%d) --threshold 0 --alert slack
```

### 5.2 主要 KPI

| KPI | 目標 | 測定方法 | しきい値 (Warning/Critical) |
|-----|------|----------|---------------------------|
| バッチ実行時間 | < 4 時間 | Step Functions | 3h / 4h |
| オンラインエラー率 | < 0.1% | 5xx / 全リクエスト | 0.05% / 0.1% |
| オンラインレイテンシ P99 | < 500ms | CloudWatch | 300ms / 500ms |
| イベント欠損率 | < 0.01% | SQS DLQ / 全イベント | 0.005% / 0.01% |
| Comparator 差分件数 | 0 件 | Comparator API | 0 / >0 |

### 5.3 30 日間モニタリングプロトコル

```
Day  1  (D-Day)     : 全バッジ即時要員 24h 体制。KPI を 15 分毎に目視。
Day  2  (D+1)       : オンライン切り替え + 全バッチ再検証。
Day  3  (D+2)       : 週末スループット試験。
Day  4-7  (D+3~D+7) : 日中 12h 体制。日次レビュー会。
Day 8-14  (D+8~D+14): 日中 8h 体制。隔日レビュー会。
Day 15-30 (D+15~D+30): 通常オンコール。週次レビュー会。
Day 30   (D+30)     : モニタリング終了ゲート判定。
```

**D+30 終了ゲート**:
- 30 日間 Comparator 差分が 0 件を維持
- KPI が 100% 目標達成
- COBOL 運用の完全停止がステークホルダー承認
- → 「モダナイズ完了」宣言

---

## 6. 連絡先・エスケレーション

### 6.1 オンコール体制

| 役割 | 担当者 | 連絡先 | バックアップ |
|------|--------|--------|-------------|
| アプリリード | TEL: xxx-0001 | slack: @app-lead | TEL: xxx-0011 |
| インフラリード | TEL: xxx-0002 | slack: @infra-lead | TEL: xxx-0012 |
| DB/データリード | TEL: xxx-0003 | slack: @db-lead | TEL: xxx-0013 |
| COBOL リード | TEL: xxx-0004 | slack: @cobol-lead | TEL: xxx-0014 |
| PM/ステークホルダー | TEL: xxx-0005 | slack: @pm | TEL: xxx-0015 |
| AWS サポート | — | enterprise support case | — |

### 6.2 エスカレーション基準

| レベル | 条件 | エスカレーション先 | 応答時間 |
|--------|------|-------------------|----------|
| L1 (Warning) | KPI が Warning 閾値到達 | オンコール App/Infra | 30 分 |
| L2 (High) | バッチ失敗 / オンライン 5xx | 全オンコール + PM | 15 分 |
| L3 (CRITICAL) | データ差分 > 10 件 / 長時間停止 | 全オンコール + PM + ステークホルダー | 5 分 |
| L4 (Rollback) | L3 が 30 分以内に復旧しない | 経営層判断で Rollback 実行 | 即時 |

### 6.3 連絡フロー

```
検出 (CloudWatch / Comparator)
  │
  ▼
オンコール対応 (15 分以内初動)
  │
  ├── 復旧可能 → 記録 & 事後レビュー
  │
  └── 復旧困難
        │
        ▼
       L3 エスカレーション
        │
        ├── 30 分以内復旧 → 記録
        │
        └── 30 分以内復旧しない
              │
              ▼
             L4 判断 → Rollback 実行 or 継続対応
```

---

## 付録 A: カットオーバー前チェック (運用側)

- [ ] バックアップ検証完了 (Aurora スナップショット / S3 Cross-Region Replication)
- [ ] CloudWatch ダッシュボードが最新か確認
- [ ] Step Functions IAM Role に十分な権限がある
- [ ] ECS タスク定義が最新イメージ (ECR tag) を参照
- [ ] DNS TTL を 60 秒に短縮済み
- [ ] 全ステークホルダーがカットオーバー日の通知を受信済み

## 付録 B: Cutover 後のクリーンアップ

- [ ] COBOL systemd タイマーを `disable` したまま 30 日間保管
- [ ] Aurora `cobol` スキーマを 30 日後に `DROP SCHEMA` (容量解放)
- [ ] Comparator Service を通常監視モードへ移行
- [ ] 本ランブックを Git