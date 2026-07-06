package com.practicebank.batch.interestpost.config;

import com.practicebank.batch.interestpost.domain.InterestPosting;
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
 * IPST-RUN-MONTHEND の集計フィールド (CTR-POSTED / CTR-CLOSED / CTR-PROD / CTR-ALREADY / CTR-HELPER / TOTAL-JPY) を収集する.
 * Step 終了時に JobExecutionContext へ集計を出力する.
 */
@Component
public class InterestPostStepListener implements StepExecutionListener, ItemWriteListener<InterestPosting> {

    private static final Logger LOG = LoggerFactory.getLogger(InterestPostStepListener.class);

    private final AtomicInteger aggregated = new AtomicInteger();
    private final AtomicInteger posted = new AtomicInteger();
    private final AtomicReference<BigDecimal> totalJpy = new AtomicReference<>(BigDecimal.ZERO);

    @Override
    public void beforeWrite(Chunk<? extends InterestPosting> items) {
        for (InterestPosting p : items) {
            aggregated.incrementAndGet();
            posted.incrementAndGet();
            totalJpy.accumulateAndGet(p.amountJpy() != null ? p.amountJpy() : BigDecimal.ZERO,
                BigDecimal::add);
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        int agg = aggregated.get();
        int post = posted.get();
        BigDecimal total = totalJpy.get();
        LOG.info("IPST-RUN-OUTPUT: aggregated={} posted={} totalJpy={}", agg, post, total);
        stepExecution.getJobExecution().getExecutionContext().putInt("ipst.aggregated", agg);
        stepExecution.getJobExecution().getExecutionContext().putInt("ipst.posted", post);
        stepExecution.getJobExecution().getExecutionContext().putLong("ipst.totalJpyCents",
            total.movePointRight(2).longValueExact());
        return stepExecution.getExitStatus();
    }

    @Override
    public void afterWrite(Chunk<? extends InterestPosting> items) { /* no-op */ }

    @Override
    public void onWriteError(Exception exception, Chunk<? extends InterestPosting> items) {
        LOG.error("IPST-RUN-MONTHEND write error: {}", exception.getMessage(), exception);
    }
}
