package com.practicebank.batch.isamtorrds;

import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * ISAM インデックスファイルをレコード単位で読み取る ItemReader (STUB).
 * TODO: Phase 2 で COBOL FD のパース → COMP-3 / EBCDIC 対応をする.
 */
public class IsamFileReader implements ItemReader<IsamRecord> {

    private final RandomAccessFile raf;
    private final int recordLength;
    private final String[] fieldNames;
    private final Charset charset;

    public IsamFileReader(Path filePath, int recordLength, String[] fieldNames) throws IOException {
        this(filePath, recordLength, fieldNames, Charset.forName("Shift_JIS"));
    }

    public IsamFileReader(Path filePath, int recordLength, String[] fieldNames, Charset charset) throws IOException {
        this.raf = new RandomAccessFile(filePath.toFile(), "r");
        this.recordLength = recordLength;
        this.fieldNames = fieldNames;
        this.charset = charset;
    }

    @Override
    public IsamRecord read() throws Exception {
        byte[] buffer = new byte[recordLength];
        int bytesRead = raf.read(buffer);
        if (bytesRead < recordLength) {
            raf.close();
            return null; // EOF
        }
        String record = new String(buffer, charset).trim();
        Map<String, Object> fields = new HashMap<>();
        // TODO: COBOL FD 定義に基づき offset/length でフィールドを切り出す
        for (int i = 0; i < fieldNames.length; i++) {
            fields.put(fieldNames[i], record);
        }
        return new IsamRecord(fields);
    }
}
