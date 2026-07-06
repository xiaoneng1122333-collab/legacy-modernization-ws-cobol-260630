package com.practicebank.batch.integrationin;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;
import org.springframework.batch.item.file.ResourceAwareItemReaderItemStream;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * デコード対象テキストファイルを 1 レコード (1 行) ずつ読む ItemReader。
 *
 * <p>COBOL の EBCDIC 800B 固定長バイナリを読む代わりに、Phase 2 では
 * CSV ライクなテストフィクスチャ (1 行 = 1 レコード相当) を読む。
 * 本番化時は {@code IntiInput#inputFilename()} の EBCDIC 800B を読んで
 * デコード (800B → 600B) するよう差し替える。
 *
 * <p>バッチ実行前の入力バリデーション (センチネル / INTI-BATCH-ID 等) は
 * {@link IntiDecodeStepConfig#inputDelegate()} で行っている前提で、
 * reader は純粋に行単位の読み出しを担当する。
 */
public class IntiDecodeReader implements ItemReader<String>, ResourceAwareItemReaderItemStream<String> {

    private final List<String> records;
    private int cursor = 0;

    public IntiDecodeReader(Resource resource, Charset charset) throws Exception {
        this.records = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), charset == null ? StandardCharsets.UTF_8 : charset))) {
            String line;
            while ((line = br.readLine()) != null) {
                // 空行 / コメント行はスキップ
                if (line.isBlank() || line.startsWith("#")) continue;
                records.add(line);
            }
        }
    }

    @Override
    public String read() throws Exception, UnexpectedInputException, NonTransientResourceException, ParseException {
        if (cursor >= records.size()) {
            return null; // EOF — COBOL PROCESS-EBCDIC-LOOP の AT END に相当
        }
        return records.get(cursor++);
    }

    @Override
    public void setResource(@NonNull Resource resource) {
        // コンストラクリ注入済み — 実処理なし
    }

    @Override
    public void open(@NonNull ExecutionContext executionContext) throws ItemStreamException {
        cursor = 0;
    }

    @Override
    public void update(@NonNull ExecutionContext executionContext) throws ItemStreamException {
        executionContext.putInt("inti.cursor", cursor);
    }

    @Override
    public void close() throws ItemStreamException {
        // nothing
    }
}
