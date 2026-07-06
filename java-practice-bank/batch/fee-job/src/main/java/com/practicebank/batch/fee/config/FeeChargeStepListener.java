package com.practicebank.batch.fee.config;

import com.practicebank.batch.fee.domain.FeePosting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * FEE-CHARGE の集計フィールド (TXNS-SCANNED / CHARGES-POSTED / TOTAL-FEE-JPY) を収集.
 * スキップ内訳は共有 {@link FeeSkipCounters} から取得.
 * Step 終了時に JobExecutionContext へ集計を出力.
 */
@Component
public class FeeChargeStepListener
    implements StepExecutionListener, ItemWriteListener<FeePosting> {

    private static final Logger LOG = LoggerFactory.getLogger(FeeChargeStepListener.class);

    private final FeeSkipCounters skipCounters;
    private final FeeProcessorState processorState;

    private final AtomicInteger posted = new AtomicInteger();
    private final AtomicReference<BigDecimal> totalFeeJpy = new AtomicReference<>(BigDecimal.ZERO);

    public FeeChargeStepListener(FeeSkipCounters skipCounters, FeeProcessorState processorState) {
        this.skipCounters = skipCounters;
        this.processorState = processorState;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        processorState.reset();
    }

    @Override
    public void beforeWrite(Chunk<? extends FeePosting> items) {
        for (FeePosting p : items) {
            posted.incrementAndGet();
            totalFeeJpy.accumulateAndGet(
                p.feeJpy() != null ? p.feeJpy() : BigDecimal.ZERO,
                BigDecimal::add);
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        int post = posted.get();
        BigDecimal total = totalFeeJpy.get();
        int noFee = skipCounters.get("NF");
        int closed = skipCounters.get("CL");
        int nsf = skipCounters.get("NS");
        int already = skipCounters.get("AL");
        int helper = skipCounters.get("HE");
        int scanned = post + noFee + closed + nsf + already + helper;

        LOG.info("FEE-CHARGE-OUTPUT: scanned={} posted={} noFee={} closed={} nsf={} already={} helper={} totalFeeJpy={}",
            scanned, post, noFee, closed, nsf, already, helper, total);

        stepExecution.getJobExecution().getExecutionContext().putInt("fee.scanned", scanned);
        stepExecution.getJobExecution().getExecutionContext().putInt("fee.posted", post);
        stepExecution.getJobExecution().getExecutionContext().putInt("fee.skippedNoFee", noFee);
        stepExecution.getJobExecution().getExecutionContext().putInt("fee.skippedClosed", closed);
        stepExecution.getJobExecution().getExecutionContext().putInt("fee.skippedNsf", nsf);
        stepExecution.getJobExecution().getExecutionContext().putInt("fee.skippedAlready", already);
        stepExecution.getJobExecution().getExecutionContext().putInt("fee.skippedHelper", helper);
        stepExecution.getJobExecution().getExecutionContext().putLong("fee.totalFeeJpyCents",
            total.movePointRight(2).longValueExact());

        skipCounters.reset();
        return stepExecution.getExitStatus();
    }

    @Override
    public void afterWrite(Chunk<? extends FeePosting> items) { /* no-op */ }

    @Override
    public void onWriteError(Exception exception, Chunk<? extends FeePosting> items) {
        LOG.error("FEE-CHARGE write error: {}", exception.getMessage(), exception);
    }
}
