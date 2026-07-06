package com.practicebank.batch.interestaccrual;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * IACR-RUN-DAILY 相当: Step 終了間に処理/INSERT/スキップの集計を出力する.
 * IACR-RUN-OUTPUT の各カウンタをカテゴリ別にカウントする.
 */
@Component
public class IacrRunProgressListener implements StepExecutionListener, ItemWriteListener<AccrualRecord> {

    private static final Logger LOG = LoggerFactory.getLogger(IacrRunProgressListener.class);

    private final AtomicInteger accountsScanned = new AtomicInteger();
    private final AtomicInteger accrualsInserted = new AtomicInteger();
    private final AtomicInteger ineligibleState = new AtomicInteger();
    private final AtomicInteger ineligibleProd = new AtomicInteger();
    private final AtomicInteger ineligibleBalance = new AtomicInteger();
    private final AtomicInteger ineligibleRate = new AtomicInteger();
    private final AtomicInteger alreadyAccrued = new AtomicInteger();
    private final AtomicInteger systemSkipped = new AtomicInteger();

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        int scanned = accountsScanned.get();
        int inserted = accrualsInserted.get();
        int iState = ineligibleState.get();
        int iProd = ineligibleProd.get();
        int iBal = ineligibleBalance.get();
        int iRate = ineligibleRate.get();
        int already = alreadyAccrued.get();
        int sysSkipped = systemSkipped.get();

        LOG.info("IACR-RUN-OUTPUT: accountsScanned={} inserted={} ineligibleState={} " +
                 "ineligibleProd={} ineligibleBalance={} ineligibleRate={} " +
                 "alreadyAccrued={} systemSkipped={}",
            scanned, inserted, iState, iProd, iBal, iRate, already, sysSkipped);

        stepExecution.getJobExecution().getExecutionContext().putInt("iacr.scanned", scanned);
        stepExecution.getJobExecution().getExecutionContext().putInt("iacr.inserted", inserted);
        stepExecution.getJobExecution().getExecutionContext().putInt("iacr.ineligibleState", iState);
        stepExecution.getJobExecution().getExecutionContext().putInt("iacr.ineligibleProd", iProd);
        stepExecution.getJobExecution().getExecutionContext().putInt("iacr.ineligibleBalance", iBal);
        stepExecution.getJobExecution().getExecutionContext().putInt("iacr.ineligibleRate", iRate);
        stepExecution.getJobExecution().getExecutionContext().putInt("iacr.alreadyAccrued", already);
        stepExecution.getJobExecution().getExecutionContext().putInt("iacr.systemSkipped", sysSkipped);

        return stepExecution.getExitStatus();
    }

    @Override
    public void beforeWrite(Chunk<? extends AccrualRecord> items) {
        accrualsInserted.addAndGet(items.size());
    }

    @Override
    public void afterWrite(Chunk<? extends AccrualRecord> items) { /* no-op */ }

    @Override
    public void onWriteError(Exception exception, Chunk<? extends AccrualRecord> items) {
        LOG.error("Write error: {}", exception.getMessage(), exception);
    }

    /** processor で null 返却によるスキップをカウントするよう外部から呼出す API. */
    public void incrementSkipped(String reason) {
        switch (reason) {
            case "SYSTEM" -> systemSkipped.incrementAndGet();
            case "STATE" -> ineligibleState.incrementAndGet();
            case "PROD" -> ineligibleProd.incrementAndGet();
            case "BALANCE" -> ineligibleBalance.incrementAndGet();
            case "RATE" -> ineligibleRate.incrementAndGet();
            case "ALREADY" -> alreadyAccrued.incrementAndGet();
            default -> ineligibleState.incrementAndGet();
        }
    }

    /** スキャン件数をカウントする. Processor からインクリメントする. */
    public void incrementScanned() {
        accountsScanned.incrementAndGet();
    }
}
