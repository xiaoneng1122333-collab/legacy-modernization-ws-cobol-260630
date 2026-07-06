package com.practicebank.batch.isamtorrds;

import java.util.Map;

public record IsamRecord(Map<String, Object> fields) {
    @SuppressWarnings("unchecked")
    public <T> T get(String fieldName) { return (T) fields.get(fieldName); }
}
