package com.practicebank.batch.fee.config;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FEE-CHARGE のスキップカウンタ.
 *
 * <p>キー=skipReason (NF/CL/NS/AL/HE), 値=スキップ回数.
 * {@link FeeChargeStepListener} が afterStep で参照する.</p>
 */
@Component
public class FeeSkipCounters {

    private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();

    public void increment(String reason) {
        counts.computeIfAbsent(reason, k -> new AtomicInteger()).incrementAndGet();
    }

    public int get(String reason) {
        AtomicInteger ai = counts.get(reason);
        return ai != null ? ai.get() : 0;
    }

    public void reset() {
        counts.clear();
    }
}
