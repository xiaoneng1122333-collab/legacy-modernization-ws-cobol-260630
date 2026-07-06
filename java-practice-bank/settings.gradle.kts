// java-practice-bank/settings.gradle.kts
rootProject.name = "java-practice-bank"

// buildSrc は Gradle の予約名のため include() 不要 (自動認識される)

// ── common ────────────────────────────────────────────────────────────────
include("common:common-domain")
include("common:common-batch")
include("common:common-mybatis")
include("common:common-test")

// ── masters (Phase 1: active) ────────────────────────────────────────────
include("masters:calendar-service")

// ── masters (Phase 2: 本登録時にコメントアウトを外す) ─────────────────────
include("masters:branch-service")
include("masters:customer-service")
include("masters:customersearch-service")
include("masters:product-service")
include("masters:interestrate-service")
include("masters:feeschedule-service")
include("masters:account-service")

// ── batch (Phase 2: 各 COBOL サブシステム対応) ─────────────────────────
include("batch:isam-to-rds-job")
include("batch:txnvalidate-job")
// include("batch:accounting-daily-job")
// include("batch:accounting-monthly-job")
// include("batch:accrual-job")
// include("batch:card-authorization-job")
// include("batch:card-settlement-job")
// include("batch:fx-revaluation-job")
// include("batch:gl-consolidation-job")
// include("batch:inquiry-online-job")
// include("batch:interface-inbound-job")
// include("batch:interface-outbound-job")
// include("batch:isam-to-rds-job")
// include("batch:loan-interest-job")
// include("batch:master-update-job")
// include("batch:overdue-payment-job")
// include("batch:statement-job")
// include("batch:trade-finance-job")

// ── online (Phase 2) ────────────────────────────────────────────────────
// include("online:inquiry-api")
// include("online:accountlifecycle-api")

// ── verify (Phase 2) ────────────────────────────────────────────────────
// include("verify:comparator-service")
