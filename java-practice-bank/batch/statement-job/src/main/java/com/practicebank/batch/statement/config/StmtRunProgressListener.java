package com.practicebank.batch.statement.config;

import com.practicebank.batch.statement.domain.AccountSnapshot;
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
 * STMT-GENERATE-BATCH の集計フィールドを収集.
 * Step 終了時に JobExecutionContext へ集計を出力.
 */
@Component
public class StmtRunProgressListener
    implements StepExecutionListener, ItemWriteListener<AccountSnapshot> {

    private static final Logger LOG = LoggerFactory.getLogger(StmtRunProgressListener.class);

    private final AtomicInteger processed = new AtomicInteger();
    private final AtomicInteger empty = new AtomicInteger();
    private final AtomicInteger skipped = new AtomicInteger();

    @Override
    public void beforeWrite(Chunk<? extends AccountSnapshot> items) {
        processed.addAndGet(items.size());
    }

    @Override
    public void afterWrite(Chunk<? extends AccountSnapshot> items) { /* no-op */ }

    @Override
    public void onWriteError(Exception exception, Chunk<? extends AccountSnapshot> items) {
        LOG.error("STMT-GENERATE write error: {}", exception.getMessage(), exception);
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        int proc = processed.get();
        LOG.info("STMT-GENERATE-OUTPUT: processed={} empty={} skipped={}", proc, empty.get(), skipped.get());
        stepExecution.getJobExecution().getExecutionContext().putInt("stmt.processed", proc);
        stepExecution.getJobExecution().getExecutionContext().putInt("stmt.empty", empty.get());
        stepExecution.getJobExecution().getExecutionContext().putInt("stmt.skipped", skipped.get());
        return stepExecution.getExitStatus();
    }

    @Override
    public void beforeStep(StepExecution stepExecution) { /* no-op */ }
}
