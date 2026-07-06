# COBOL → Java Phase 3 (統合検証 + カットオーバー準備) 実装計画

> **Created:** 2026-07-07
> **Purpose:** Phase 2 で移植した 80 プログラムの統合検証と、AWS カットオーバーの準備を行う

## スコープ

### 対象
1. **統合テスト強化** — 比較対象サービス (Comparator Service) を実際のスキーマ比較に拡張
2. **Terraform モジュール完成** — ECS / Step Functions / S3 / Monitoring モジュール
3. **カットオーバーランブック** — COBOL → Java 切り替え手順書
4. **コードクリーンアップ** — ネストディレクトリ削除、未処理 TODO の追跡

### 対象外
- ❌ COBOL 実環境の停止 (本番作業)
- ❌ AWS 実デプロイ (別プロジェクト)
- ❌ 30 日間モニタリング (本番運用フェーズ)

---

## Task 1: Comparator Service の拡張

**Files:**
- Modify: `java-practice-bank/verify/comparator-service/src/main/java/.../ComparatorService.java`
- Reads: `db/migration/V1__initial_schema.sql`, `V2__master_pg_tables.sql`

**Interfaces:**
- Consumes: `shared` スキーマ (Aurora)
- Produces: JSON diff report

**実装内容:**
- 7 テーブル (customers, accounts, transactions, postings, balances, interest_accruals, audit_log) の行数比較を実装
- business_date パラメータでフィルタリング
- PASS / WARN / FAIL の判定ロジック

**検証:**
```bash
./gradlew :verify:comparator-service:test
```

---

## Task 2: Terraform モジュール完成 (ECS + Step Functions + S3 + Monitoring)

**Files:**
- Create: `infra/modules/ecs/main.tf`
- Create: `infra/modules/storage/main.tf`
- Create: `infra/modules/step-functions/main.tf`
- Create: `infra/modules/monitoring/main.tf`

**実装内容:**

### ECS モジュール
- ECS Cluster (Fargate)
- Task Definition (cpu: 256/512, memory: 512M/1G)
- Service (desired count, auto-scaling)
- ALB (Application Load Balancer)

### Storage モジュール
- S3 Bucket (3つ: input, output, archive)
- Lifecycle policy (90 日経過 → Glacier)
- Event notification → Step Functions トリガー

### Step Functions モジュール
- State Machine (日次パイプライン 15 ステップ)
- EventBridge Rule (cron 23:00)
- IAM Role

### Monitoring モジュール
- CloudWatch Dashboard
- Alarms (バッチ失敗, 実行時間 > 4h)
- SNS Topic (アラート通知)

---

## Task 3: カットオーバーランブック

**Files:**
- Create: `java-practice-bank/docs/runbooks/cutover-runbook.md`

**内容:**
1. カットオーバー前提条件チェックリスト
2. 段階的切り替え手順 (マスタ → バッチ → オンライン)
3. ロールバック手順
4. 検証手順 (Comparator Service による差分行列)
5. 連絡先・エスカレーション

---

## Task 4: コードクリーンアップ

** cleanup items:**
- ネスト `java-practice-bank/java-practice-bank/` がまだ存在しないか確認
- `.superpowers/sdd/` の一時ファイル整理
- 未処理 TODO を `docs/TODO.md` に集約

---

## 成功基準

- [ ] Comparator Service が 7 テーブルの行数比較を実行
- [ ] Terraform `infra/` に全 7 モジュールが存在
- [ ] カットオーバーランブックが commit 済み
- [ ] `./gradlew clean build test` が BUILD SUCCESSFUL
