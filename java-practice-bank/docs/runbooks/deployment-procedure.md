# AWS デプロイ手順書

> **対象:** java-practice-bank (Spring Boot 3 + Spring Batch 5 + MyBatis, Java 21)
> **基盤:** ECS Fargate / Aurora PostgreSQL / Step Functions / ECR / S3
> **IaC:** Terraform (S3 リモートステート)
> **CI/CD:** GitHub Actions (OIDC 認証)
> **リージョン:** `ap-northeast-1`
> **最終更新:** 2026-07-07

---

## 目次

1. [用語・リソース早見表](#1-用語リソース早見表)
2. [前提条件](#2-前提条件)
3. [環境構築 (Terraform)](#3-環境構築-terraform)
4. [CI/CD パイプライン設定](#4-cicd-パイプライン設定)
5. [コンテナイメージのビルド & ECR push](#5-コンテナイメージのビルド--ecr-push)
6. [ECS サービス デプロイ](#6-ecs-サービス-デプロイ)
7. [デプロイ後検証](#7-デプロイ後検証)
8. [日次バッチパイプライン起動](#8-日次バッチパイプライン起動)
9. [ロールバック手順](#9-ロールバック手順)
10. [トラブルシューティング](#10-トラブルシューティング)
11. [付録: 未対応項目と今後の拡張](#11-付録-未対応項目と今後の拡張)

---

## 1. 用語・リソース早見表

### 1.1 固定リソース名 (dev 環境)

| リソース | 名前 / 値 | 定義箇所 |
|---|---|---|
| Terraform ステートバケット | `practice-bank-terraform-state` | `infra/backend.tf` |
| Terraform ステートキー | `infrastructure/terraform.tfstate` | `infra/backend.tf` |
| ECR リポジトリ | `practice-bank-app` | `environments/dev/main.tf` |
| CD の ECR リポジトリ env | `java-practice-bank` (※dev と名称差異あり) | `.github/workflows/cd.yml` |
| ECS クラスタ | `practice-bank-dev-cluster` | `modules/ecs/main.tf` |
| ECS サービス | `practice-bank-dev-service` | `modules/ecs/main.tf` |
| ECS タスク定義 | `practice-bank-dev-app` | `modules/ecs/main.tf` |
| ALB | `practice-bank-dev-alb` | `environments/dev/main.tf` |
| Target Group | `practice-bank-dev-tg` (port 8080) | `environments/dev/main.tf` |
| CloudWatch Log Group | `/ecs/practice-bank-dev` | `environments/dev/main.tf` |
| Step Functions | `practice-bank-dev-daily-batch` | `modules/step-functions/main.tf` |
| EventBridge スケジュール | `cron(0 23 * * ? *)` Asia/Tokyo | `modules/step-functions/main.tf` |
| VPC CIDR | `10.0.0.0/16` | `environments/dev/main.tf` |
| AZ | `ap-northeast-1a`, `ap-northeast-1c` | `environments/dev/main.tf` |
| DB ユーザー | `cobol` | `environments/dev/main.tf` |
| DB 名 | `banking` | `environments/dev/main.tf` |
| Spring Profile | `aws` | `modules/ecs/main.tf` |
| ヘルスチェック | `:8080/actuator/health` | `environments/dev/main.tf` |

### 1.2 変数 (terraform)

| 変数 | 型 | 既定 | 説明 |
|---|---|---|---|
| `aws_region` | string | `ap-northeast-1` | リージョン |
| `db_password` | string (sensitive) | — | Aurora マスターパスワード (必須) |
| `alert_emails` | list(string) | `[]` | CloudWatch アラート通知先 |

### 1.3 デプロイ対象 Java モジュール

| グループ | モジュール | 種別 | Phase |
|---|---|---|---|
| masters | `calendar-service` | オンライン/マスタ | 1 実装済 |
| batch | `isam-to-rds-job` | バッチ | 2 実装済 |
| online | `inquiry-api` | オンライン | 2 実装済 |
| verify | `comparator-service` | 検証 | 2 実装済 |
| 他 (今後) | branch / customer / product / accountlifecycle / interestrate / feeschedule / account / txnvalidate / txnsortmerge / ... | — | 3+ |

---

## 2. 前提条件

### 2-1. ツール (バージョン要件)

| ツール | 最低バージョン | 確認コマンド |
|---|---|---|
| AWS CLI | v2 | `aws --version` |
| Terraform | ≥ 1.5.0 | `terraform version` |
| Docker | 20.x+ | `docker --version` |
| JDK | 21 (Temurin) | `java -version` |
| Gradle | wrapper に従い | `./gradlew --version` |

### 2-2. AWS アカウント

- `ap-northeast-1` で以下のリソース作成権限を持つ IAM ユーザー / ロール:
  - `ec2:*` (VPC/Subnet/SG/ALB)
  - `rds:*` (Aurora)
  - `elasticache:*` (Redis)
  - `ecs:*` (クラスタ/サービス/タスク定義)
  - `ecr:*` (リポジトリ)
  - `s3:*` (バケット/ステート)
  - `iam:*` (ロール)
  - `states:*` (Step Functions)
  - `events:*` (EventBridge)
  - `logs:*` (CloudWatch Logs)
  - `sns:*`
  - `scheduler:*`

### 2-3. リポジトリ clone

```bash
git clone <repo-url>
cd java-practice-bank
```

---

## 3. 環境構築 (Terraform)

### 3-1. リモートステート用 S3 バケットの事前作成

`backend.tf` は S3 ステートを指すため、**初回のみ**バケットを手動作成する。

```bash
aws s3api create-bucket \
  --bucket practice-bank-terraform-state \
  --region ap-northeast-1 \
  --create-bucket-configuration LocationConstraint=ap-northeast-1

aws s3api put-bucket-versioning \
  --bucket practice-bank-terraform-state \
  --versioning-configuration Status=Enabled

aws s3api put-bucket-encryption \
  --bucket practice-bank-terraform-state \
  --server-side-encryption-configuration '{
    "Rules": [{"ApplyServerSideEncryptionByDefault": {"SSEAlgorithm": "AES256"}}]
  }'
```

> **注:** バケット名はグローバルに一意。既に占有されている場合は `practice-bank-terraform-state-<任意のサフィックス>` に変更し、`backend.tf` の `bucket` も更新する。

### 3-2. Terraform 初期化

```bash
cd infra/environments/dev
terraform init
```

成功例:
```
Successfully configured the backend "s3"!
```

### 3-3. プランの確認

```bash
terraform plan \
  -var='db_password=<あなたのパスワード>' \
  -var='alert_emails=["ops@example.com"]'
```

確認ポイント:
- `Plan: X to add, 0 to change, 0 to destroy` (初回は全追加)
- network / database / ecs / storage / step-functions / monitoring の全モジュールが作成対象

### 3-4. 適用

```bash
terraform apply \
  -var='db_password=<あなたのパスワード>' \
  -var='alert_emails=["ops@example.com"]'
```

> プロンプトで `yes` を入力。dev 環境で約 10〜15 分かかる (Aurora 作成が大半)。

### 3-5. 適用後の出力確認

```bash
terraform output
```

次の値を控える:

| 値 | 用途 |
|---|---|
| ECR リポジトリ URL | CD の push 先 |
| ALB DNS 名前 | ヘルスチェック URL |
| Aurora エンドポイント | DB 接続確認 |
| S3 バケット名 | バッチ入出力 |

### 3-6. 作成リソース一覧 (モジュール別)

| モジュール | 作成リソース |
|---|---|
| `network` | VPC, プライベートサブネット (2AZ), アプリ SG, NAT |
| `database` | Aurora PostgreSQL 16.3 (2× db.r6g.large), ElastiCache Redis 7.1 |
| `ecs` | クラスタ (containerInsights ON), タスク定義, サービス (desired=2) |
| `storage` | S3 バケット 3 個 (input/output/archive, 暗号化+Glacier) |
| `step-functions` | 日次バッチステートマシン, EventBridge Scheduler |
| `monitoring` | CloudWatch Dashboard, Alarm, SNS |

---

## 4. CI/CD パイプライン設定

### 4-1. GitHub Actions のワークフロー

| ファイル | トリガー | 処理 |
|---|---|---|
| `.github/workflows/ci.yml` | PR → main | `./gradlew build` (テスト含む) |
| `.github/workflows/cd.yml` | push → main | bootJar → docker build → ECR push |

### 4-2. OIDC 認証の設定

CD は IAM キーではなく **GitHub OIDC** で認証する。

#### 4-2-1. AWS 側: OIDC プロバイダ & IAM ロール作成

```bash
# GitHub OIDC プロバイダ (アカウントに 1 回)
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --thumbprint-list <thumbprint> \
  --client-id-list sts.amazonaws.com

# CD 用 IAM ロール (信頼ポリシーで GitHub リポジトリを制限)
# ロール ARN をメモ → 4-3 で使う
```

信頼ポリシーの例 (リポジトリ `ourolegacy/java-practice-bank` に制限):

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": { "Federated": "arn:aws:iam::<account-id>:oidc-provider/token.actions.githubusercontent.com" },
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": { "token.actions.githubusercontent.com:aud": "sts.amazonaws.com" },
      "StringLike": { "token.actions.githubusercontent.com:sub": "repo:ourolegacy/java-practice-bank:*" }
    }
  }]
}
```

ロールに付与するポリシー: `AmazonEC2ContainerRegistryPowerUser` + `AmazonECS_FullAccess` (デプロイ自動化時)。

### 4-3. GitHub Secrets の設定

リポジトリ **Settings → Secrets and variables → Actions** で以下を登録:

| Secret 名 | 値 |
|---|---|
| `AWS_ROLE_ARN` | `arn:aws:iam::<account-id>:role/<cd-role-name>` |
| `AWS_ACCOUNT_ID` | AWS アカウント ID (12 桁) |

### 4-4. ECR リポジトリ名の差異に注意

| 箇所 | リポジトリ名 |
|---|---|
| Terraform (`environments/dev/main.tf`) | `practice-bank-app` |
| CD ワークフロー (`cd.yml` env) | `java-practice-bank` |

**どちらか一方に統一**する。Terraform で作成した `practice-bank-app` を使う場合は `cd.yml` を以下に修正:

```yaml
env:
  ECR_REPOSITORY: practice-bank-app   # ← Terraform と一致させる
```

または Terraform 側を `java-practice-bank` に変更。

---

## 5. コンテナイメージのビルド & ECR push

### 5-1. 自動 (CI/CD 経由) — 推奨

```bash
git add .
git commit -m "feat: <変更内容>"
git push origin main
```

`cd.yml` が自動実行:

1. JDK 21 セットアップ
2. OIDC で AWS 認証
3. ECR ログイン
4. `./gradlew bootJar --no-daemon`
5. `docker build` (タグ: `<sha>` + `latest`)
6. ECR push

**Actions タブで green を確認。**

### 5-2. 手動 (ローカルから push)

```bash
# 1. bootJar
./gradlew bootJar --no-daemon

# 2. ECR ログイン
aws ecr get-login-password --region ap-northeast-1 \
  | docker login --username AWS --password-stdin <account-id>.dkr.ecr.ap-northeast-1.amazonaws.com

# 3. build
docker build -t java-practice-bank .

# 4. tag
docker tag java-practice-bank:latest <account-id>.dkr.ecr.ap-northeast-1.amazonaws.com/java-practice-bank:latest
docker tag java-practice-bank:latest <account-id>.dkr.ecr.ap-northeast-1.amazonaws.com/java-practice-bank:$(git rev-parse --short HEAD)

# 5. push
docker push <account-id>.dkr.ecr.ap-northeast-1.amazonaws.com/java-practice-bank:latest
docker push <account-id>.dkr.ecr.ap-northeast-1.amazonaws.com/java-practice-bank:$(git rev-parse --short HEAD)
```

### 5-3. Dockerfile の内容

```dockerfile
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

> `build/libs/*.jar` は `bootJar` タスクで生成される。`.dockerignore` で `build/`, `.git/` 等を除外すること。

---

## 6. ECS サービス デプロイ

### 6-1. 現状の制約

`cd.yml` は **ECR push まで**で止まっており、ECS のサービス更新 (ロールアウト) は自動化されていない。
デプロイは **手動** で行うか、CD に deploy step を追加する。

### 6-2. 手動デプロイ (AWS CLI)

#### 6-2-1. 新タスク定義リビジョンの登録

Terraform で管理している場合は、新しいイメージを指すよう `ecs` モジュールの `image` を更新して `terraform apply`:

```hcl
# modules/ecs/main.tf の task definition 内
image = "${var.ecr_repository_url}:<新しいタグ>"
```

```bash
cd infra/environments/dev
terraform apply -var='db_password=<pw>'
```

> タグを `latest` 以外にする場合は `image` の指定を更新。`latest` のまま `force-new-deployment` する場合は 6-2-2 のみでよい (ただし再現性のため明示タグ推奨)。

#### 6-2-2. サービス更新 (ロールアウト)

```bash
aws ecs update-service \
  --cluster practice-bank-dev-cluster \
  --service practice-bank-dev-service \
  --force-new-deployment \
  --region ap-northeast-1
```

#### 6-2-3. デプロイ完了待ち

```bash
aws ecs wait services-stable \
  --cluster practice-bank-dev-cluster \
  --services practice-bank-dev-service \
  --region ap-northeast-1
```

### 6-3. 推奨: CD パイプラインに deploy step 追加

`cd.yml` の `push` job に以下を追加すると完全自動化できる:

```yaml
      - name: Deploy to ECS
        run: |
          aws ecs update-service \
            --cluster practice-bank-dev-cluster \
            --service practice-bank-dev-service \
            --force-new-deployment \
            --region ${{ env.AWS_REGION }}
```

### 6-4. デプロイ設定リファレンス

| パラメータ | 値 | 変更する場合 |
|---|---|---|
| task CPU | 256 | `environments/dev/main.tf` → `task_cpu` |
| task memory | 512 MiB | `environments/dev/main.tf` → `task_memory` |
| desired count | 2 | `environments/dev/main.tf` → `desired_count` |
| Spring profile | `aws` | `modules/ecs/main.tf` → environment |
| ヘルスチェックパス | `/actuator/health` | `environments/dev/main.tf` |
| ヘルスしきい値 | healthy=2, unhealthy=10 | `environments/dev/main.tf` |

---

## 7. デプロイ後検証

### 7-1. ECS サービス状態

```bash
aws ecs describe-services \
  --cluster practice-bank-dev-cluster \
  --services practice-bank-dev-service \
  --region ap-northeast-1 \
  --query 'services[*].{
    name:serviceName,
    status:status,
    running:runningCount,
    desired:desiredCount,
    pending:pendingCount,
    events[0].message:latestEvent
  }'
```

**期待値:** `runningCount == desiredCount (2)`, `pendingCount == 0`

### 7-2. タスク (コンテナ) 状態

```bash
# タスク ARN 一覧
TASKS=$(aws ecs list-tasks \
  --cluster practice-bank-dev-cluster \
  --service practice-bank-dev-service \
  --region ap-northeast-1 \
  --query taskArns --output text)

# 各タスクの詳細
aws ecs describe-tasks \
  --cluster practice-bank-dev-cluster \
  --tasks $TASKS \
  --region ap-northeast-1 \
  --query 'tasks[*].{
    taskArn:taskArn,
    lastStatus:lastStatus,
    healthStatus:healthStatus,
    containers:containers[*].{name:name,reason:reason,exitCode:exitCode}
  }'
```

**期待値:** `lastStatus = RUNNING`, `healthStatus = HEALTHY`, `exitCode` なし

### 7-3. ヘルスチェック (ALB 経由)

```bash
# ALB DNS 取得
ALB_DNS=$(aws elbv2 describe-load-balancers \
  --names practice-bank-dev-alb \
  --region ap-northeast-1 \
  --query 'LoadBalancers[0].DNSName' --output text)

# ヘルスチェック
curl -s "http://${ALB_DNS}:8080/actuator/health" | jq .
```

**期待値:**
```json
{"status":"UP"}
```

### 7-4. アプリケーションメトリクス確認

```bash
curl -s "http://${ALB_DNS}:8080/actuator/metrics" | jq '.names'
```

### 7-5. CloudWatch ログ確認

```bash
aws logs tail /ecs/practice-bank-dev \
  --follow --region ap-northeast-1
```

エラーがないか確認:
```bash
aws logs filter-log-events \
  --log-group-name /ecs/practice-bank-dev \
  --filter-pattern "ERROR" \
  --region ap-northeast-1 \
  --limit 20
```

### 7-6. Target Group ヘルス

```bash
TG_ARN=$(aws elbv2 describe-target-groups \
  --names practice-bank-dev-tg \
  --region ap-northeast-1 \
  --query 'TargetGroups[0].TargetGroupArn' --output text)

aws elbv2 describe-target-health \
  --target-group-arn $TG_ARN \
  --region ap-northeast-1 \
  --query 'TargetHealthDescriptions[*].{
    id:Target.Id,
    port:Target.Port,
    state:TargetHealth.State,
    reason:TargetHealth.Reason
  }'
```

**期待値:** 全ターゲット `state = healthy`

---

## 8. 日次バッチパイプライン起動

### 8-1. 自動起動 (EventBridge Scheduler)

毎日 **23:00 (Asia/Tokyo)** に Step Functions が自動起動する (Terraform で設定済)。

```bash
# スケジュール確認
aws scheduler get-schedule \
  --name practice-bank-dev-daily-batch \
  --region ap-northeast-1 \
  --query '{Schedule:schedule_expression,Timezone:schedule_expression_timezone,State:state}'
```

### 8-2. 手動起動 (検証用)

```bash
SFN_ARN=$(aws stepfunctions list-state-machines \
  --region ap-northeast-1 \
  --query "stateMachines[?name=='practice-bank-dev-daily-batch'].stateMachineArn" \
  --output text)

EXEC_ARN=$(aws stepfunctions start-execution \
  --state-machine-arn $SFN_ARN \
  --name "manual-$(date +%Y%m%d%H%M%S)" \
  --input '{"triggerSource":"manual"}' \
  --region ap-northeast-1 \
  --query executionArn --output text)

echo "Execution ARN: $EXEC_ARN"
```

### 8-3. 実行監視

```bash
# 状態ポーリング
aws stepfunctions describe-execution \
  --execution-arn $EXEC_ARN \
  --region ap-northeast-1 \
  --query '{status:status,startDate:startDate,stopDate:stopDate}'
```

### 8-4. バッチステップ順序 (Step Functions 定義)

```
MasterLoad → IntegrationIn → TxnValidate → TxnSortMerge → TxnPost
  → InterestAccrual → InterestPost → Autodebit → Fee → Statement
  → IntegrationOut → Audit → Finalize
```

各ステップは **同じ ECS タスク定義** を `--step=<STEP_NAME>` 引数で起動する (Fargate)。
いずれかのステップが失敗すると `Catch` で `Audit` にフォールバックする。

### 8-5. バッチ完了後の検証

- Step Functions 実行ステータスが `SUCCEEDED`
- S3 `output` バケットに帳票が格納
- Comparator Service で COBOL/Java 差分が 0 件

---

## 9. ロールバック手順

### 9-1. ECS サービスを前のリビジョンに戻す

```bash
# 1. 現在のタスク定義リビジョン一覧
aws ecs list-task-definitions \
  --family-prefix practice-bank-dev-app \
  --sort DESC \
  --region ap-northeast-1 \
  --query 'taskDefinitionArns[-3:]'

# 2. 前のリビジョンをサービスに指定
aws ecs update-service \
  --cluster practice-bank-dev-cluster \
  --service practice-bank-dev-service \
  --task-definition practice-bank-dev-app:<前のリビジョン番号> \
  --force-new-deployment \
  --region ap-northeast-1

# 3. 安定待ち
aws ecs wait services-stable \
  --cluster practice-bank-dev-cluster \
  --services practice-bank-dev-service \
  --region ap-northeast-1
```

### 9-2. Terraform ごと戻す場合

```bash
cd infra/environments/dev
terraform plan -var='db_password=<pw>'   # 差分確認
terraform apply -var='db_password=<pw>'   # 前の状態に戻す
```

### 9-3. ロールバック検証

- [ ] ECS サービス `runningCount == desiredCount`
- [ ] `/actuator/health` が `UP`
- [ ] CloudWatch Logs に ERROR がない
- [ ] ヘルスチェックが継続して成功

---

## 10. トラブルシューティング

### 10-1. タスクが起動しない / 即刻停止する

```bash
# タスクの stoppedReason を確認
aws ecs describe-tasks \
  --cluster practice-bank-dev-cluster \
  --tasks <task-arn> \
  --region ap-northeast-1 \
  --query 'tasks[*].{stoppedReason:stoppedReason,containers:containers[*].{reason:reason,exitCode:exitCode,logStream:logConfiguration.options.awslogs-stream-prefix}}'
```

| 症状 | 原因 | 対処 |
|---|---|---|
| `CannotPullContainerErr` | ECR 認証 or イメージタグ不在 | イメージが ECR に存在か確認 |
| `Essential container exited` | app.jar 起動失敗 | CloudWatch Logs で Spring Boot エラー確認 |
| `ResourceInitializationError` | ログドライバ設定不備 | log group `/ecs/practice-bank-dev` が存在か確認 |

### 10-2. ヘルスチェック失敗

- SG で `:8080` が ALB から許可されているか確認
- アプリが `management.endpoint.health` を公開しているか確認 (`application.yml` で `management.endpoints.web.exposure.include=health` を設定)
- `SPRING_PROFILES_ACTIVE=aws` で DB 接続に失敗していないか → CloudWatch Logs で `Connection refused` を検索

### 10-3. DB 接続エラー

Aurora / Redis はプライベートサブネットに配置。ECS タスクも同じ VPC のプライベートサブネットで起動し、SG で以下を許可:

| 接続元 SG | 先ポート | 先 SG |
|---|---|---|
| アプリ SG (`module.network.app_security_group_id`) | 5432 | DB SG |
| アプリ SG | 6379 | Cache SG |

これは Terraform で設定済 (`environments/dev/main.tf` の `aws_security_group.db` / `cache`)。

### 10-4. Step Functions 実行失敗

```bash
# 失敗した実行の詳細
aws stepfunctions describe-execution \
  --execution-arn <arn> \
  --region ap-northeast-1 \
  --query '{status:status,cause:cause,error:error}'

# イベント履歴
aws stepfunctions get-execution-history \
  --execution-arn <arn> \
  --region ap-northeast-1 \
  --query 'events[?type==`TaskFailed`]'
```

### 10-5. Terraform ステートロックで失敗する

```bash
# ロック強制解除 (他に適用中の処理がないことを確認してから)
terraform force-unlock <lock-id>
```

---

## 11. 付録: 未対応項目と今後の拡張

### 11-1. 環境分離 (staging / prod)

現状は `environments/dev/` のみ。staging/prod を追加する場合:

```
infra/environments/
├── dev/          # 既存
├── staging/      # 新規 (dev をコピーし変数を上書き)
└── prod/         # 新規
```

各環境で `project_name` を `practice-bank-staging`, `practice-bank-prod` に変更し、`desired_count` や Aurora インスタンスタイプを調整。

### 11-2. マルチモジュール対応

現状の `ecs` モジュールは **1 タスク定義** のみ。サービス別にデプロイするには:

- モジュール別に `aws_ecs_task_definition` / `aws_ecs_service` を作成
- CD でモジュール別にイメージをビルド (タグにモジュール名を含める)
- `infra/modules/ecs/` をモジュール化して各サービスから呼び出す

### 11-3. HTTPS / カスタムドメイン

- **ACM** (AWS Certificate Manager) で証明書発行
- **Route53** で A レコード (ALB エイリアス)
- ALB リスナーを `:80` → `:443` に変更し、HTTP→HTTPS リダイレクト

### 11-4. シークレット管理

現状 `DB_PASSWORD` が Terraform の変数・ECS 環境変数に平文で存在。改善案:

- **AWS Secrets Manager** にシークレット登録
- ECS タスク定義の `secrets` で参照
- `db_password` 変数を Terraform に直接渡さない

### 11-5. デプロイ自動化の完全化

`cd.yml` に以下を追加:

```yaml
- name: Deploy to ECS
  run: |
    aws ecs update-service \
      --cluster practice-bank-dev-cluster \
      --service practice-bank-dev-service \
      --force-new-deployment \
      --region ${{ env.AWS_REGION }}
    aws ecs wait services-stable \
      --cluster practice-bank-dev-cluster \
      --services practice-bank-dev-service \
      --region ${{ env.AWS_REGION }}
```

### 11-6. デプロイ完了チェックリスト

```
□ 1. S3 ステートバケット作成 (初回のみ)
□ 2. terraform init / plan / apply 成功
□ 3. GitHub Secrets (AWS_ROLE_ARN, AWS_ACCOUNT_ID) 設定
□ 4. ECR リポジトリ名を Terraform と CD で統一
□ 5. git push → cd.yml が green、ECR にイメージ push 確認
□ 6. aws ecs update-service --force-new-deployment
□ 7. aws ecs wait services-stable 成功
□ 8. curl ALB:8080/actuator/health → {"status":"UP"}
□ 9. CloudWatch Logs に ERROR なし
□ 10. Target Group 全ヘルス
□ 11. Step Functions 手動 1 回実行で SUCCEEDED
□ 12. ロールバック手順をチームが確認済み
```

---

## 改訂履歴

| 日付 | 内容 |
|---|---|
| 2026-07-07 | 初版作成 (実コードベース) |