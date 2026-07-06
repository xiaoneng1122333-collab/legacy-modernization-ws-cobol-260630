// java-practice-bank/batch/isam-to-rds-job/src/main/java/com/practicebank/batch/isamtorrds/IsamFileReader.java
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
 * ISAM インデックスファイルをレコード単位で読み取る ItemReader。
 * COBOL の READ NEXT に対応する。
 * 固定長バイナリ読み取り (COBOL FD のレコード長に依存)。
 *
 * <p>実装は簡略版。実運用では COBOL の COMP-3 / EBCDIC 変換が必要。</p>
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
        // 固定長バイナリ → フィールドマッピング (簡易実装)
        String record = new String(buffer, charset).trim();
        Map<String, Object> fields = new HashMap<>();
        // TODO: COBOL FD のフィールド定義に基づきパース (name, offset, length)
        for (int i = 0; i < fieldNames.length; i++) {
            fields.put(fieldNames[i], record);
        }
        return new IsamRecord(fields);
    }
}
