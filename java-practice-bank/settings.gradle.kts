// java-practice-bank/settings.gradle.kts
rootProject.name = "java-practice-bank"

// Phase 1 で作成するモジュール
// buildSrc は Gradle の予約名のため include() 不要 (自動認識される)
include("common:common-domain")
include("common:common-batch")
include("common:common-mybatis")
include("common:common-test")
include("batch:isam-to-rds-job")
include("masters:calendar-service")
