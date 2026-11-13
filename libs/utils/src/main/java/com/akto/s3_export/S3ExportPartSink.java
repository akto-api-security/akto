package com.akto.s3_export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Collects the rows of one hour into gzip NDJSON parts and uploads each part as it fills, so
 * memory stays bounded by one part. Rolls to a new part at the record or uncompressed-size cap.
 */
public class S3ExportPartSink {

    public static final int MAX_RECORDS_PER_PART = 50_000;
    public static final long MAX_UNCOMPRESSED_BYTES_PER_PART = 64L * 1024 * 1024;
    static final String PART_CONTENT_TYPE = "application/gzip";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String dirKey;
    private final S3ObjectUploader uploader;
    private final int maxRecords;
    private final long maxBytes;
    private final Runnable onPartUploaded;

    private ByteArrayOutputStream compressed;
    private GZIPOutputStream gzip;
    private int recordsInPart;
    private long bytesInPart;
    private long recordCount;
    private int partCount;

    public S3ExportPartSink(String dirKey, S3ObjectUploader uploader, Runnable onPartUploaded) {
        this(dirKey, uploader, MAX_RECORDS_PER_PART, MAX_UNCOMPRESSED_BYTES_PER_PART, onPartUploaded);
    }

    S3ExportPartSink(String dirKey, S3ObjectUploader uploader, int maxRecords, long maxBytes, Runnable onPartUploaded) {
        this.dirKey = dirKey;
        this.uploader = uploader;
        this.maxRecords = maxRecords;
        this.maxBytes = maxBytes;
        this.onPartUploaded = onPartUploaded;
    }

    public void accept(Map<String, Object> row) {
        try {
            byte[] line = (MAPPER.writeValueAsString(row) + "\n").getBytes(StandardCharsets.UTF_8);
            if (recordsInPart > 0 && (recordsInPart >= maxRecords || bytesInPart + line.length > maxBytes)) {
                flushPart();
            }
            if (gzip == null) {
                compressed = new ByteArrayOutputStream();
                gzip = new GZIPOutputStream(compressed);
            }
            gzip.write(line);
            recordsInPart++;
            bytesInPart += line.length;
            recordCount++;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Uploads the last partial part. */
    public void finish() {
        try {
            if (recordsInPart > 0) flushPart();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public long getRecordCount() {
        return recordCount;
    }

    public int getPartCount() {
        return partCount;
    }

    private void flushPart() throws IOException {
        gzip.close();
        partCount++;
        uploader.upload(S3ExportUtils.partKey(dirKey, partCount), compressed.toByteArray(), PART_CONTENT_TYPE);
        gzip = null;
        compressed = null;
        recordsInPart = 0;
        bytesInPart = 0;
        if (onPartUploaded != null) onPartUploaded.run();
    }
}
