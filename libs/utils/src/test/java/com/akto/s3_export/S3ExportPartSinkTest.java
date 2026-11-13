package com.akto.s3_export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

import org.junit.Test;

public class S3ExportPartSinkTest {

    private static final String DIR = "akto/atlas/traces/dt=2024-01-02/hour=10/";

    private static class Recorder implements S3ObjectUploader {
        final List<String> keys = new ArrayList<>();
        final List<byte[]> bodies = new ArrayList<>();

        @Override
        public void upload(String key, byte[] body, String contentType) {
            keys.add(key);
            bodies.add(body);
        }
    }

    private static Map<String, Object> row(int n) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", n);
        row.put("name", "row-" + n);
        return row;
    }

    private static String gunzip(byte[] body) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(body))) {
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    public void emptyHourUploadsNoPart() {
        Recorder uploader = new Recorder();
        S3ExportPartSink sink = new S3ExportPartSink(DIR, uploader, null);
        sink.finish();
        assertEquals(0, sink.getRecordCount());
        assertEquals(0, sink.getPartCount());
        assertTrue(uploader.keys.isEmpty());
    }

    @Test
    public void rowsAreWrittenAsGzipNdjson() throws Exception {
        Recorder uploader = new Recorder();
        S3ExportPartSink sink = new S3ExportPartSink(DIR, uploader, null);
        sink.accept(row(1));
        sink.accept(row(2));
        sink.finish();
        assertEquals(Collections.singletonList(DIR + "part-0001.ndjson.gz"), uploader.keys);
        assertEquals("{\"id\":1,\"name\":\"row-1\"}\n{\"id\":2,\"name\":\"row-2\"}\n", gunzip(uploader.bodies.get(0)));
    }

    @Test
    public void rollsToANewPartAtTheRecordCap() {
        Recorder uploader = new Recorder();
        AtomicInteger uploaded = new AtomicInteger();
        S3ExportPartSink sink = new S3ExportPartSink(DIR, uploader, 2, Long.MAX_VALUE, uploaded::incrementAndGet);
        for (int i = 0; i < 5; i++) sink.accept(row(i));
        sink.finish();
        assertEquals(5, sink.getRecordCount());
        assertEquals(3, sink.getPartCount());
        assertEquals(3, uploaded.get());
        assertEquals(DIR + "part-0003.ndjson.gz", uploader.keys.get(2));
    }

    @Test
    public void rollsToANewPartAtTheSizeCap() throws Exception {
        Recorder uploader = new Recorder();
        int lineLength = "{\"id\":1,\"name\":\"row-1\"}\n".length();
        S3ExportPartSink sink = new S3ExportPartSink(DIR, uploader, 1000, lineLength * 2L, null);
        for (int i = 1; i <= 3; i++) sink.accept(row(i));
        sink.finish();
        assertEquals(2, sink.getPartCount());
        assertEquals("{\"id\":3,\"name\":\"row-3\"}\n", gunzip(uploader.bodies.get(1)));
    }

    @Test
    public void aSingleOversizedRowStillGetsAPart() {
        Recorder uploader = new Recorder();
        S3ExportPartSink sink = new S3ExportPartSink(DIR, uploader, 1000, 1, null);
        sink.accept(row(1));
        sink.finish();
        assertEquals(1, sink.getPartCount());
    }
}
