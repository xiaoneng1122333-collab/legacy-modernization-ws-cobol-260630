package com.practicebank.verify.comparator.service;

import com.practicebank.verify.comparator.model.CompareReport;
import com.practicebank.verify.comparator.model.DiffEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * COBOL と Java のスキーマ間で指定営業日の row count を比較する.
 * テーブル一覧は Phase 2 で固定. 将来運用では運用管理テーブルで拡張可能にする.
 */
@Service
public class ComparatorService {

    private static final Logger LOG = LoggerFactory.getLogger(ComparatorService.class);

    /**
     * 比較対象テーブル (COBOL/Java 双方に存在することが前提).
     * スキーマ情報つき. COBOL 側 = public スキーマ (postgres user), Java 側 = practicebank スキーマ.
     * queries では完全修飾 (schema.table) でアクセスする.
     */
    private static final List<TableRef> TARGET_TABLES = List.of(
        new TableRef("transactions",  "public.transactions",    "practicebank.transactions"),
        new TableRef("postings",      "public.postings",        "practicebank.postings"),
        new TableRef("balances",      "public.balances",        "practicebank.balances"),
        new TableRef("customers",     "public.customers",       "practicebank.customers"),
        new TableRef("accounts",      "public.accounts",        "practicebank.accounts"),
        new TableRef("batch_run",     "public.batch_run",       "practicebank.batch_run"),
        new TableRef("audit_log",     "public.audit_log",       "practicebank.audit_log")
    );

    private final JdbcTemplate cobolJdbc;
    private final JdbcTemplate javaJdbc;

    public ComparatorService(JdbcTemplate jdbcTemplate,
                             @Qualifier("javaJdbcTemplate") JdbcTemplate javaJdbc) {
        // jdbcTemplate = primary = COBOL 側
        this.cobolJdbc = jdbcTemplate;
        this.javaJdbc = javaJdbc;
    }

    public CompareReport compare(LocalDate businessDate) {
        String iso = businessDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        return compare(iso);
    }

    public CompareReport compare(String businessDate) {
        List<DiffEntry> entries = new ArrayList<>();
        for (TableRef ref : TARGET_TABLES) {
            Long cobol = safeCount(cobolJdbc, ref.cobolTable(), businessDate);
            Long java = safeCount(javaJdbc, ref.javaTable(), businessDate);
            entries.add(DiffEntry.of(ref.name(), cobol, java));
            LOG.debug("compare table={} cobol={} java={} bdate={}", ref.name(), cobol, java, businessDate);
        }
        return CompareReport.of(businessDate, entries);
    }

    /**
     * business_date カラムが有るか確認してあれば where 句を追加.
     * エラー時は null 返却 (テーブル不在でも 500 にせず ONLY_JAVA / ONLY_COBOL 扱いにする).
     */
    private Long safeCount(JdbcTemplate jdbc, String fullTable, String businessDate) {
        try {
            Boolean hasBd = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.columns " +
                "WHERE table_schema = ? AND table_name = ? AND column_name = 'business_date')",
                Boolean.class,
                schemaOf(fullTable), tableOf(fullTable));
            if (Boolean.TRUE.equals(hasBd)) {
                return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + fullTable + " WHERE business_date = ?::date",
                    Long.class, businessDate);
            }
            return jdbc.queryForObject("SELECT COUNT(*) FROM " + fullTable, Long.class);
        } catch (Exception e) {
            LOG.warn("safeCount failed table={} reason={}", fullTable, e.getMessage());
            return null;
        }
    }

    private String schemaOf(String full) {
        int dot = full.indexOf('.');
        return dot < 0 ? "public" : full.substring(0, dot);
    }

    private String tableOf(String full) {
        int dot = full.indexOf('.');
        return dot < 0 ? full : full.substring(dot + 1);
    }

    private record TableRef(String name, String cobolTable, String javaTable) {}
}
