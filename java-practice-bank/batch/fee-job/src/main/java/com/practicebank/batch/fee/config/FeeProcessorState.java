package com.practicebank.batch.fee.config;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * FEE-CHARGE プロセッサのジョブ内状態.
 *
 * <p>{@link FeeChargeStepListener} の beforeStep でリセットされる.
 * Singleton だが Step 実行単位でクリーンアップ.</p>
 */
@Component
public class FeeProcessorState {

    private final Set<String> seenAccounts = new HashSet<>();

    public boolean markSeen(String accountNumber) {
        return seenAccounts.add(accountNumber);
    }

    public void reset() {
        seenAccounts.clear();
    }

    public int size() {
        return seenAccounts.size();
    }
}
