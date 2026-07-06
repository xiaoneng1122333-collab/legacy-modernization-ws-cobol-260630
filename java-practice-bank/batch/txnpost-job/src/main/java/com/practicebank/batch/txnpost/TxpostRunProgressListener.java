package com.practicebank.batch.txnpost;

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
 * TXPOST-RUN-BATCH: Step 終了時に処理/記帳/スキップの集計を出力する.
 * TXPOST-RUN-OUTPUT の各カウンタをカテゴリ別にカウントする.
 */
@Component
public class TxpostRunProgressListener implements StepExecutionListener, ItemWriteListener<TransactionRecord> {

    private static final Logger LOG = LoggerFactory.getLogger(TxpostRunProgressListener.class);

    private final AtomicInteger txnsRead = new AtomicInteger();
    private final AtomicInteger txnsPosted = new AtomicInteger();
    private final AtomicInteger alreadyPostedSkipped = new AtomicInteger();
    private final AtomicInteger hardRejected = new AtomicInteger();
    private final AtomicInteger reconDeferred = new AtomicInteger();
    private final AtomicInteger inDoubtResolved = new AtomicInteger();
    private final AtomicInteger dormancyDeferred = new AtomicInteger();

    @Override
    public void beforeWrite(Chunk<? extends TransactionRecord> items) {
        txnsRead.addAndGet(items.size());
    }

    @Override
    public void afterWrite(Chunk<? extends TransactionRecord> items) {
        txnsPosted.addAndGet(items.size());
    }

    @Override
    public void onWriteError(Exception exception, Chunk<? extends TransactionRecord> items) {
        LOG.error("Write error in TXPOST-RUN-BATCH: {}", exception.getMessage(), exception);
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        int read = txnsRead.get();
        int posted = txnsPosted.get();
        int alreadySkipped = alreadyPostedSkipped.get();
        int rejected = hardRejected.get();
        int deferred = reconDeferred.get();
        int inDoubt = inDoubtResolved.get();
        int dormancy = dormancyDeferred.get();

        LOG.info("TXPOST-RUN-OUTPUT: read={} posted={} alreadySkipped={} hardRejected={} " +
                 "reconDeferred={} inDoubtResolved={} dormancyDeferred={}",
            read, posted, alreadySkipped, rejected, deferred, inDoubt, dormancy);

        stepExecution.getJobExecution().getExecutionContext().putInt("txpost.read", read);
        stepExecution.getJobExecution().getExecutionContext().putInt("txpost.posted", posted);
        stepExecution.getJobExecution().getExecutionContext().putInt("txpost.alreadySkipped", alreadySkipped);
        stepExecution.getJobExecution().getExecutionContext().putInt("txpost.hardRejected", rejected);
        stepExecution.getJobExecution().getExecutionContext().putInt("txpost.reconDeferred", deferred);
        stepExecution.getJobExecution().getExecutionContext().putInt("txpost.inDoubtResolved", inDoubt);
        stepExecution.getJobExecution().getExecutionContext().putInt("txpost.dormancyDeferred", dormancy);

        return stepExecution.getExitStatus();
    }
}
