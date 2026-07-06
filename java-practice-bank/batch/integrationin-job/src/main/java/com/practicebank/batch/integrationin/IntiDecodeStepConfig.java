package com.practicebank.batch.integrationin;

import com.practicebank.common.domain.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.ResourceAwareItemReaderItemStream;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * INTI-DECODE-BATCH Spring Batch Step.
 *
 * <p>Phase 2 構成 (reader → processor → writer):</p>
 * <ul>
 *   <li>reader: テストフィクスチャ (テキスト) を 1 レコード (1 行 = H/D/T) ずつ読み、
 *       全行を 1 リストとして返却 (ステップスコープ)</li>
 *   <li>processor: {@link IntiDecodeService#decode} を適用し
 *       {@link IntiDecodeService.DecodeResult} を生成</li>
 *   <li>writer: {@link DecodedTransaction} を {@code transactions} テーブルに書き出し、
 *       {@link IntiOutput} は {@link ExecutionContext} 経由でリスナーに引き継ぐ</li>
 * </ul>
 */
@Configuration
public class IntiDecodeStepConfig {

    private static final Logger LOG = LoggerFactory.getLogger(IntiDecodeStepConfig.class);

    private static final int DEFAULT_REJECT_THRESHOLD_PCT = 20;

    /**
     * フィクスチャファイルを一括読みして 1 リストを返す ItemReader (Phase 2 専用)。
     *
     * <p>Phase 2 ではステップスコープではなく通常の singleton で定義する
     * (実ジョブ起動時は {@code input.file} ジョブパラメータからリソースを解決する
     * {@code StepScope} 版に差し替える)。
     */
    @Bean
    public ItemReader<List<String>> decodeReader(
            @Value("${inti.decode.input.file:#{null}}") String inputFile) {
        if (inputFile == null || inputFile.isBlank()) {
            LOG.info("decodeReader: no input.file configured — returning empty list (Phase 2 context-only)");
            return new ListItemReader<>(List.of());
        }
        try {
            Resource resource = new FileSystemResource(inputFile);
            List<String> lines = readAllLines(resource, StandardCharsets.UTF_8);
            LOG.info("decodeReader loaded {} lines from {}", lines.size(), inputFile);
            return new ListItemReader<>(List.of(lines));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read input file: " + inputFile, e);
        }
    }

    /** {@link IntiDecodeReader} と同じロジックでフィクスチャを全行読む (IOException捕捉用分離). */
    private static List<String> readAllLines(Resource resource, Charset charset) throws Exception {
        ResourceAwareItemReaderItemStream<String> r = new IntiDecodeReader(resource, charset);
        r.open(new ExecutionContext());
        try {
            List<String> out = new ArrayList<>();
            String line;
            while ((line = r.read()) != null) {
                out.add(line);
            }
            return out;
        } finally {
            r.close();
        }
    }

    /**
     * COBOL 版の DECODE + VALIDATE に相当するプロセッサ。
     * {@link ListItemReader} が渡す list=1 に対し、{@link IntiDecodeService#decode} を呼んで
     * {@link IntiDecodeService.DecodeResult} を返す。
     *
     * <p>実ジョブ時はジョブパラメータから値を差し込む ({@code StepScope} + {@code #jobParameters})。
     * Phase 2 は context-only のためプロパティプレースホルダーで注入する
     * (未設定でもコンテキストが落ちないようデフォルト値を与える)。
     */
    @Bean
    public ItemProcessor<List<String>, IntiDecodeService.DecodeResult> decodeProcessor(
            @Value("${inti.decode.batch.id:BATCH0000000001}") String batchId,
            @Value("${inti.decode.business.date:20260706}") long businessDate,
            @Value("${inti.decode.reject.threshold:20}") int threshold) {
        return lines -> IntiDecodeService.decode(batchId, businessDate, lines, threshold);
    }

    /**
     * デコード済み {@link DecodedTransaction} を {@code transactions} テーブルに書き出すライタ。
     * 同一チャンク内では 1 件 (DecodeResult) のみ渡される。
     */
    @Bean
    public ItemWriter<IntiDecodeService.DecodeResult> decodeWriter(DataSource dataSource) {
        JdbcBatchItemWriter<DecodedTransaction> jdbcWriter = new JdbcBatchItemWriterBuilder<DecodedTransaction>()
                .dataSource(dataSource)
                .sql(
                    "INSERT INTO transactions (" +
                    "  txn_id, business_date, system_ts, category, account_number," +
                    "  counter_account_number, amount_jpy, currency, description," +
                    "  source_system, source_batch_id, source_seq, status, reversal_of," +
                    "  created_by, created_ts" +
                    ") VALUES (" +
                    "  ?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, 'INTI', ?, ?, 'PT', NULL," +
                    "  'inti-decode', CURRENT_TIMESTAMP" +
                    ")")
                .itemPreparedStatementSetter((row, ps) -> {
                    ps.setString(1, "INT" + row.batchId() + String.format("%010d", row.seq()));
                    ps.setObject(2, row.businessDate());
                    ps.setString(3, row.category());
                    ps.setString(4, row.payerAccount());
                    ps.setString(5, row.payeeAccount());
                    ps.setLong(6, Money.of(row.amountJpy()).toLong());
                    ps.setString(7, row.currency());
                    ps.setString(8, row.description());
                    ps.setString(9, row.batchId());
                    ps.setLong(10, row.seq());
                })
                .build();

        return chunk -> {
            for (IntiDecodeService.DecodeResult result : chunk.getItems()) {
                List<DecodedTransaction> rows = result.decoded();
                if (!rows.isEmpty()) {
                    jdbcWriter.write(new Chunk<>(rows));
                }
            }
        };
    }

    /**
     * チャンク書き込み完了後に {@link IntiOutput} を JobExecutionContext に格納するリスナー。
     * Job 終了時に {@link IntiDecodeJobListener} がログ出力に使用する。
     *
     * <p>{@link ItemWriteListener#afterWrite} はアイテム書き込み結果を直接取得できる。
     */
    @Bean
    public ItemWriteListener<IntiDecodeService.DecodeResult> intiWriteListener() {
        return new ItemWriteListener<IntiDecodeService.DecodeResult>() {
            @Override
            public void beforeWrite(Chunk<? extends IntiDecodeService.DecodeResult> items) {
                // noop
            }

            @Override
            public void afterWrite(Chunk<? extends IntiDecodeService.DecodeResult> items) {
                for (IntiDecodeService.DecodeResult r : items) {
                    // StepExecution のスレッドローカルから JobExecution へ格納
                    StepExecution stepExecution = StepExecutionHolder.get();
                    if (stepExecution == null) {
                        LOG.warn("StepExecution not available in afterWrite — output not carried");
                        continue;
                    }
                    stepExecution.getJobExecution()
                            .getExecutionContext()
                            .put("inti.output", r.output());
                }
            }

            @Override
            public void onWriteError(Exception exception, Chunk<? extends IntiDecodeService.DecodeResult> items) {
                LOG.error("Write error: {}", exception.getMessage(), exception);
            }
        };
    }

    /**
     * decode Step — reader → processor → writer の chunk 処理 (chunk=1, 全レコードを
     * 1 つの DecodeResult にまとめてライタに渡す)。
     */
    @Bean
    public Step decode(JobRepository jobRepository,
                       PlatformTransactionManager txManager,
                       ItemReader<List<String>> decodeReader,
                       ItemProcessor<List<String>, IntiDecodeService.DecodeResult> decodeProcessor,
                       ItemWriter<IntiDecodeService.DecodeResult> decodeWriter,
                       ItemWriteListener<IntiDecodeService.DecodeResult> intiWriteListener,
                       ChunkListener intiChunkListener) {
        return new StepBuilder("decode", jobRepository)
                .<List<String>, IntiDecodeService.DecodeResult>chunk(1, txManager)
                .reader(decodeReader)
                .processor(decodeProcessor)
                .writer(decodeWriter)
                .listener(intiWriteListener)
                .listener(intiChunkListener)
                .build();
    }

    /**
     * チャンク開始前に StepExecution をスレッドローカルに保持するリスナー。
     * {@link #intiWriteListener()} が afterWrite で JobExecution へアクセスするために使う。
     */
    @Bean
    public ChunkListener intiChunkListener() {
        return new ChunkListener() {
            @Override
            public void beforeChunk(ChunkContext context) {
                StepExecutionHolder.set(context.getStepContext().getStepExecution());
            }

            @Override
            public void afterChunk(ChunkContext context) {
                StepExecutionHolder.clear();
            }

            @Override
            public void afterChunkError(ChunkContext context) {
                StepExecutionHolder.clear();
            }
        };
    }

    /** StepExecution を afterWrite へ引き渡すためのスレッドローカルホルダ. */
    static final class StepExecutionHolder {
        private static final ThreadLocal<StepExecution> HOLDER = new ThreadLocal<>();

        static void set(StepExecution se) { HOLDER.set(se); }
        static StepExecution get() { return HOLDER.get(); }
        static void clear() { HOLDER.remove(); }
    }
}
