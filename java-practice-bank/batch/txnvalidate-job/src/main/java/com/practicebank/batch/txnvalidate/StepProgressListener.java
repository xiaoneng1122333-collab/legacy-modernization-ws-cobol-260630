package com.practicebank.batch.txnvalidate;

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
 * TXVAL-REPORT-SUMMARY 相当: Step 終了時に処理/合格/拒否の集計を出力する.
 */
@Component
public class StepProgressListener implements StepExecutionListener, ItemWriteListener<ValidationResult> {

    private static final Logger LOG = LoggerFactory.getLogger(StepProgressListener.class);

    private final AtomicInteger processed = new AtomicInteger();
    private final AtomicInteger validated = new AtomicInteger();
    private final AtomicInteger rejected = new AtomicInteger();

    @Override
    public void beforeWrite(Chunk<? extends ValidationResult> items) {
        for (ValidationResult r : items) {
            processed.incrementAndGet();
            if (r.valid()) {
                validated.incrementAndGet();
            } else {
                rejected.incrementAndGet();
            }
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        int p = processed.get();
        int v = validated.get();
        int r = rejected.get();
        LOG.info("TXVAL-REPORT-SUMMARY: processed={} validated={} rejected={}", p, v, r);
        stepExecution.getJobExecution().getExecutionContext().putInt("txval.processed", p);
        stepExecution.getJobExecution().getExecutionContext().putInt("txval.validated", v);
        stepExecution.getJobExecution().getExecutionContext().putInt("txval.rejected", r);
        return stepExecution.getExitStatus();
    }

    @Override
    public void afterWrite(Chunk<? extends ValidationResult> items) { /* no-op */ }

    @Override
    public void onWriteError(Exception exception, Chunk<? extends ValidationResult> items) {
        LOG.error("Write error: {}", exception.getMessage(), exception);
    }
}
