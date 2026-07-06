// java-practice-bank/batch/isam-to-rds-job/src/main/java/com/practicebank/batch/isamtorrds/IsamRecord.java
package com.practicebank.batch.isamtorrds;

import java.util.Collections;
import java.util.Map;

/**
 * ISAM ファイルの 1 レコードを表す。フィールド名 → 値 のマップ。
 * COBOL の 01 レベルレコードに対応し、フィールドは FD の定義順序で格納される。
 */
public record IsamRecord(Map<String, Object> fields) {

    public IsamRecord {
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }

    public Object get(String fieldName) {
        return fields.get(fieldName);
    }

    public String getString(String fieldName) {
        Object value = fields.get(fieldName);
        return value == null ? null : value.toString();
    }

    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(fields);
    }
}
