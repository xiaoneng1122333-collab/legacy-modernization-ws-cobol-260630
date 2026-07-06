package com.practicebank.batch.autodebit;

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
 * AD-REPORT-SUMMARY 相当: Step 終了時に処理/成功/失敗の集計を出力する.
 */
@Component
public class AutodebitStepProgressListener
    implements StepExecutionListener, ItemWriteListener<AutodebitPostResult> {

    private static final Logger LOG = LoggerFactory.getLogger(AutodebitStepProgressListener.class);

    private final AtomicInteger processed = new AtomicInteger();
    private final AtomicInteger posted = new AtomicInteger();
    private final AtomicInteger failedNf = new AtomicInteger();
    private final AtomicInteger failedCl = new AtomicInteger();
    private final AtomicInteger failedSu = new AtomicInteger();

    @Override
    public void beforeWrite(Chunk<? extends AutodebitPostResult> items) {
        for (AutodebitPostResult r : items) {
            processed.incrementAndGet();
            if (r.posted()) {
                posted.incrementAndGet();
            } else if (r.errors().contains("NF")) {
                failedNf.incrementAndGet();
            } else if (r.errors().contains("CL")) {
                failedCl.incrementAndGet();
            } else if (r.errors().contains("SU")) {
                failedSu.incrementAndGet();
            }
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        int p = processed.get();
        int ok = posted.get();
        int nf = failedNf.get();
        int cl = failedCl.get();
        int su = failedSu.get();
        LOG.info("AD-RUN-OUTPUT: processed={} posted={} failedNF={} failedCL={} failedSU={}",
            p, ok, nf, cl, su);
        stepExecution.getJobExecution().getExecutionContext().putInt("ad.processed", p);
        stepExecution.getJobExecution().getExecutionContext().putInt("ad.posted", ok);
        stepExecution.getJobExecution().getExecutionContext().putInt("ad.failedNf", nf);
        stepExecution.getJobExecution().getExecutionContext().putInt("ad.failedCl", cl);
        stepExecution.getJobExecution().getExecutionContext().putInt("ad.failedSu", su);
        return stepExecution.getExitStatus();
    }

    @Override
    public void afterWrite(Chunk<? extends AutodebitPostResult> items) { /* no-op */ }

    @Override
    public void onWriteError(Exception exception, Chunk<? extends AutodebitPostResult> items) {
        LOG.error("Write error: {}", exception.getMessage(), exception);
    }
}
