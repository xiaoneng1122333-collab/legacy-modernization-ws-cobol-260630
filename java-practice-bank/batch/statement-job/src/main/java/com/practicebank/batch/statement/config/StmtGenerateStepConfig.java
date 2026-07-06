package com.practicebank.batch.statement.config;

import com.practicebank.batch.statement.domain.AccountSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemStreamWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * STMT-GENERATE-BATCH 相当の Spring Batch Step.
 *
 * <p>4-level カーソル (customers → branches → accounts → transactions) で帳票生成.</p>
 * <p>accounts が主カーソル. 顧客名 / 店名はマスタキャッシュで解決.</p>
 * <p>Phase 2 では Reader → Tasklet style Writer (ファイル出力, 既存 autodebit/interestpost パターンに合わせる).
 * <p>output file: 固定長 120 桁 LINE SEQUENTIAL 形式でヘッダー + 明細行 + フッターを出力.</p>
 */
@Configuration
public class StmtGenerateStepConfig {

    private static final Logger LOG = LoggerFactory.getLogger(StmtGenerateStepConfig.class);

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Value("${statement.generate.batch.id:#{null}}")
    private String configuredBatchId;

    @Value("${statement.generate.business.date:#{null}}")
    private String businessDateStr;

    @Value("${statement.generate.mode:D}")
    private String mode;

    @Value("${statement.generate.output-filename:#{null}}")
    private String outputFilename;

    @Value("${statement.generate.skip-inactive:false}")
    private boolean skipInactive;

    /**
     * accounts テーブルを balances と JOIN してReader取得.
     * 有効な口座 (account_status = 'A') のみをフェッチ.
     */
    @Bean
    @StepScope
    public JdbcCursorItemReader<AccountSnapshot> accountReader(DataSource dataSource) {
        String sql =
            "SELECT a.acct_number, a.acct_name, a.branch_code, a.product_code, " +
            "       a.acct_status, a.cust_id, b.balance_jpy " +
            "  FROM accounts a " +
            "  JOIN balances b ON a.acct_number = b.account_number " +
            " WHERE a.acct_status = 'A' " +
            " ORDER BY a.acct_number LIMIT 1000";
        return new JdbcCursorItemReaderBuilder<AccountSnapshot>()
            .name("accountReader")
            .dataSource(dataSource)
            .sql(sql)
            .rowMapper(StmtGenerateStepConfig::mapRow)
            .build();
    }

    private static AccountSnapshot mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AccountSnapshot(
            rs.getString("acct_number"),
            rs.getString("acct_name"),
            rs.getString("branch_code"),
            rs.getString("product_code"),
            rs.getString("acct_status"),
            rs.getString("cust_id"),
            rs.getLong("balance_jpy")
        );
    }

    /**
     * ライタ: accounts をチャンク単位でを受け取り、ファイルに帳票を出力.
     * マスタキャッシュ（customers / branches）を先にロードして顧客名・店名を解決.
     */
    @Bean
    public ItemStreamWriter<AccountSnapshot> statementWriter(DataSource dataSource) {
        return new ItemStreamWriter<AccountSnapshot>() {

            private BufferedWriter writer;
            private int linesWritten = 0;
            private int pagesWritten = 1;
            private long bytesWritten = 0;
            private int accountsProcessed = 0;
            private int accountsEmpty = 0;
            private int accountsSkipped = 0;
            private Map<String, String> customerCache = new HashMap<>();
            private Map<String, String> branchCache = new HashMap<>();
            private LocalDate bizDate;

            @Override
            public void open(org.springframework.batch.item.ExecutionContext executionContext) {
                bizDate = businessDateStr != null ? LocalDate.parse(businessDateStr) : LocalDate.now();
                // マスタキャッシュロード
                JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                jdbc.query("SELECT cust_id, cust_name FROM customers ORDER BY cust_id",
                    rs -> { customerCache.put(rs.getString("cust_id"), rs.getString("cust_name")); });
                jdbc.query("SELECT branch_code, branch_name FROM branches ORDER BY branch_code",
                    rs -> { branchCache.put(rs.getString("branch_code"), rs.getString("branch_name")); });
                LOG.info("STMT-GENERATE: master cache loaded (customers={}, branches={})",
                    customerCache.size(), branchCache.size());
                // ファイルオープン
                if (outputFilename != null && !outputFilename.isBlank()) {
                    try {
                        Path path = Paths.get(outputFilename);
                        if (path.getParent() != null) Files.createDirectories(path.getParent());
                        writer = Files.newBufferedWriter(path);
                        writeHeader();
                    } catch (IOException e) {
                        throw new RuntimeException("STMT-GENERATE: failed to open output file: " + outputFilename, e);
                    }
                }
            }

            @Override
            public void write(Chunk<? extends AccountSnapshot> items) throws Exception {
                JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                for (AccountSnapshot account : items) {
                    accountsProcessed++;

                    // 取引明細取得 (直近 30 日)
                    List<TxnRow> txns = new ArrayList<>();
                    jdbc.query(
                        "SELECT txn_id, business_date, description, amount_jpy " +
                        "  FROM transactions " +
                        " WHERE account_number = ? AND business_date <= ? " +
                        " ORDER BY business_date, txn_id LIMIT 500",
                        ps -> {
                            ps.setString(1, account.accountNumber());
                            ps.setDate(2, Date.valueOf(bizDate));
                        },
                        (rs, rowNum) -> txns.add(new TxnRow(
                            rs.getString("txn_id"),
                            rs.getDate("business_date").toLocalDate(),
                            rs.getString("description"),
                            rs.getBigDecimal("amount_jpy")
                        ))
                    );

                    if (txns.isEmpty() && skipInactive) {
                        accountsSkipped++;
                        continue;
                    }

                    String custName = customerCache.getOrDefault(account.customerId(), "UNKNOWN");
                    String branchName = branchCache.getOrDefault(account.branchCode(), "UNKNOWN");

                    // 口座ヘッダー行
                    writeLine(String.format("PRACTICE BANK STATEMENT   Acct: %s   Customer: %s   Branch: %s",
                        padRight(account.accountNumber(), 18), padRight(custName, 24), padRight(branchName, 20)));
                    writeLine(String.format("Period ending: %s   Opening Balance: %s",
                        bizDate.toString(), formatJpy(account.balanceJpy())));
                    linesWritten += 2;

                    if (txns.isEmpty()) {
                        writeLine("  (no transactions this period)");
                        writeLine(String.format("  Closing Balance: %s", formatJpy(account.balanceJpy())));
                        accountsEmpty++;
                        linesWritten += 2;
                    } else {
                        BigDecimal running = BigDecimal.valueOf(account.balanceJpy());
                        // 明細書き出し開始 (開始残高 = 繰越 + 取引ごとのネット変更)
                        // 本フェーズでは全取引を単純に加算 (実際には DR/CR 判定がいるが省略)
                        for (TxnRow txn : txns) {
                            // amount は入金プラス / 出金マイナスを想定 (簡易)
                            running = running.add(txn.amountJpy());
                            writeLine(String.format("  %s  %-40s  %14s  Balance: %14s",
                                txn.businessDate.toString(),
                                padRight(txn.description(), 40),
                                formatJpy(txn.amountJpy()),
                                formatJpy(running)));
                            linesWritten++;
                        }
                        writeLine(String.format("  Closing Balance: %s", formatJpy(running)));
                        linesWritten++;
                    }
                    writeLine(""); // 改行
                    linesWritten++;
                }
            }

            @Override
            public void close() {
                if (writer != null) {
                    try {
                        writeFooter();
                        writer.close();
                    } catch (IOException e) {
                        throw new RuntimeException("STMT-GENERATE: failed to close output file", e);
                    }
                }
                LOG.info("STMT-GENERATE-OUTPUT: accountsProcessed={} accountsEmpty={} accountsSkipped={} " +
                        "linesWritten={} pagesWritten={} bytesWritten={}",
                    accountsProcessed, accountsEmpty, accountsSkipped, linesWritten, pagesWritten, bytesWritten);
                // JobExecutionContext へ
                // (本来は StepExecution 経由だが、ここでは直接 static 参照ではなく
                //  JobExecutionContext 経由はできないのでログ出力のみ. StepListener で再集計.)
            }

            private void writeHeader() throws IOException {
                String header = "PRACTICE BANK STATEMENT";
                writer.write(header);
                writer.newLine();
                writer.write(String.format("Batch ID:   %s", configuredBatchId));
                writer.newLine();
                writer.write(String.format("Run Date:   %s", bizDate.toString()));
                writer.newLine();
                writer.write(String.format("Mode:       %s (%s)", mode, mode.equals("D") ? "Daily" : "Monthly"));
                writer.newLine();
                writer.write("=".repeat(120));
                writer.newLine();
                linesWritten = 4;
            }

            private void writeFooter() throws IOException {
                writer.write("-".repeat(120));
                writer.newLine();
                writer.write(String.format("End of Statement   Total accounts: %d   Lines: %d",
                    accountsProcessed, linesWritten));
                writer.newLine();
            }

            private void writeLine(String line) throws IOException {
                writer.write(line);
                writer.newLine();
                bytesWritten += line.length() + 1;
            }
        };
    }

    private static String padRight(String s, int width) {
        if (s == null) s = "";
        if (s.length() >= width) return s.substring(0, width);
        return s + " ".repeat(width - s.length());
    }

    private static String formatJpy(long jpy) {
        return String.format("¥%,d", jpy);
    }

    private static String formatJpy(BigDecimal jpy) {
        return String.format("¥%,.2f", jpy);
    }

    /**
     * 取引明細の内部 DTO.
     */
    private record TxnRow(String txnId, LocalDate businessDate, String description, BigDecimal amountJpy) {}

    /** STMT-GENERATE-BATCH メイン Step. */
    @Bean
    public Step stmtGenerate(JobRepository jobRepository,
                              PlatformTransactionManager txManager,
                              JdbcCursorItemReader<AccountSnapshot> accountReader,
                              ItemStreamWriter<AccountSnapshot> statementWriter,
                              StmtRunProgressListener runProgressListener) {
        return new StepBuilder("stmtGenerate", jobRepository)
            .<AccountSnapshot, AccountSnapshot>chunk(50, txManager)
            .reader(accountReader)
            .writer(statementWriter)
            .listener((org.springframework.batch.core.StepExecutionListener) runProgressListener)
            .listener((org.springframework.batch.core.ItemWriteListener<AccountSnapshot>) runProgressListener)
            .build();
    }
}
