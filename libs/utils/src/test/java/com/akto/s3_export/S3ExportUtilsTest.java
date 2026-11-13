package com.akto.s3_export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.List;

import org.junit.Test;

import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.sts.model.StsException;

public class S3ExportUtilsTest {

    private static final long HOUR = S3ExportUtils.HOUR_MS;
    // 2024-01-02T10:20:00Z
    private static final long NOW = 1704189600000L + 20 * 60_000L;

    private static S3Exception s3Error(int status, String code) {
        return (S3Exception) S3Exception.builder().statusCode(status)
            .awsErrorDetails(AwsErrorDetails.builder().errorCode(code).errorMessage("msg").build()).build();
    }

    private static StsException stsError(String code) {
        return (StsException) StsException.builder().statusCode(403)
            .awsErrorDetails(AwsErrorDetails.builder().errorCode(code).errorMessage("msg").build()).build();
    }

    // ── time windows ──

    @Test
    public void hourFloorRoundsDownToUtcHour() {
        long hour = 1704189600000L; // 2024-01-02T10:00:00Z
        assertEquals(hour, S3ExportUtils.hourFloor(hour));
        assertEquals(hour, S3ExportUtils.hourFloor(hour + HOUR - 1));
    }

    @Test
    public void firstRunStartsOneDayBeforeTheCurrentHour() {
        long hour = S3ExportUtils.hourFloor(NOW);
        assertEquals(hour - 24 * HOUR, S3ExportUtils.startCursor(null, NOW));
        assertEquals(hour - 3 * HOUR, S3ExportUtils.startCursor(hour - 3 * HOUR, NOW));
    }

    @Test
    public void hourIsExportedOnlyAfterTheLag() {
        long hour = S3ExportUtils.hourFloor(NOW);
        long cursor = hour - HOUR;
        // the hour [hour-1h, hour) closed 20 minutes ago, past the 15 minute lag
        assertEquals(1, S3ExportUtils.windowStarts(cursor, NOW).size());
        // 10 minutes after the close it is still inside the lag
        assertTrue(S3ExportUtils.windowStarts(cursor, hour + 10 * 60_000L).isEmpty());
        assertEquals(1, S3ExportUtils.windowStarts(cursor, hour + S3ExportUtils.LAG_MS).size());
    }

    @Test
    public void windowsAreConsecutiveAndCappedPerRun() {
        long cursor = S3ExportUtils.hourFloor(NOW) - 100 * HOUR;
        List<Long> starts = S3ExportUtils.windowStarts(cursor, NOW);
        assertEquals(S3ExportUtils.MAX_HOURS_PER_RUN, starts.size());
        assertEquals(cursor, (long) starts.get(0));
        assertEquals(cursor + HOUR, (long) starts.get(1));
    }

    // ── keys ──

    @Test
    public void normalizePrefixTrimsSlashes() {
        assertEquals("", S3ExportUtils.normalizePrefix(null));
        assertEquals("", S3ExportUtils.normalizePrefix("  / "));
        assertEquals("a/b", S3ExportUtils.normalizePrefix(" /a/b/ "));
    }

    @Test
    public void normalizePrefixRejectsTraversalAndOddCharacters() {
        for (String bad : new String[] {"a/../b", "..", "a b", "a\\b", "a?b"}) {
            try {
                S3ExportUtils.normalizePrefix(bad);
                fail("accepted " + bad);
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

    @Test
    public void hourDirKeyIsPartitionedByUtcDateAndHour() {
        long start = 1704189600000L; // 2024-01-02T10:00:00Z
        assertEquals("akto/atlas/traces/dt=2024-01-02/hour=10/",
            S3ExportUtils.hourDirKey("", S3ExportDataset.ATLAS_TRACES, start));
        assertEquals("exports/acme/akto/atlas/guardrail_events/dt=2024-01-02/hour=10/",
            S3ExportUtils.hourDirKey("/exports/acme/", S3ExportDataset.ATLAS_GUARDRAIL_EVENTS, start));
    }

    @Test
    public void partSuccessAndTestKeys() {
        String dir = "akto/atlas/traces/dt=2024-01-02/hour=10/";
        assertEquals(dir + "part-0001.ndjson.gz", S3ExportUtils.partKey(dir, 1));
        assertEquals(dir + "part-0012.ndjson.gz", S3ExportUtils.partKey(dir, 12));
        assertEquals(dir + "_SUCCESS", S3ExportUtils.successKey(dir));
        assertEquals("p/akto/_connection_test.json", S3ExportUtils.connectionTestKey("p"));
    }

    @Test
    public void successMarkerCarriesTheWindowAndCounts() throws Exception {
        String json = S3ExportUtils.successMarker(S3ExportDataset.ATLAS_TRACES, 1000L, 2000L, 5L, 1, 3000L);
        assertTrue(json.contains("\"dataset\":\"ATLAS_TRACES\""));
        assertTrue(json.contains("\"windowStartMs\":1000"));
        assertTrue(json.contains("\"windowEndMs\":2000"));
        assertTrue(json.contains("\"recordCount\":5"));
        assertTrue(json.contains("\"partCount\":1"));
        assertTrue(json.contains("\"exportedAt\":\"1970-01-01T00:00:03Z\""));
    }

    // ── errors ──

    @Test
    public void accessAndConfigurationErrorsAreNotRetryable() {
        assertFalse(S3ExportUtils.isRetryable(s3Error(403, "AccessDenied")));
        assertFalse(S3ExportUtils.isRetryable(s3Error(404, "NoSuchBucket")));
        assertFalse(S3ExportUtils.isRetryable(stsError("AccessDenied")));
        assertFalse(S3ExportUtils.isRetryable(new IllegalStateException("Elasticsearch is not configured")));
        assertFalse(S3ExportUtils.isRetryable(new UnsupportedOperationException()));
    }

    @Test
    public void throttlingServerErrorsAndBackendFailuresAreRetryable() {
        assertTrue(S3ExportUtils.isRetryable(s3Error(503, "ServiceUnavailable")));
        assertTrue(S3ExportUtils.isRetryable(s3Error(429, "Whatever")));
        assertTrue(S3ExportUtils.isRetryable(s3Error(503, "SlowDown")));
        assertTrue(S3ExportUtils.isRetryable(s3Error(400, "RequestTimeout")));
        assertTrue(S3ExportUtils.isRetryable(SdkClientException.create("Unable to execute HTTP request")));
        assertTrue(S3ExportUtils.isRetryable(new IOException("Elasticsearch request failed with HTTP 500")));
    }

    @Test
    public void awsErrorIsFoundThroughWrapping() {
        assertFalse(S3ExportUtils.isRetryable(new RuntimeException("wrapped", s3Error(403, "AccessDenied"))));
    }

    @Test
    public void describeErrorUsesTheAwsCodeAndIsTruncated() {
        assertEquals("AccessDenied (HTTP 403): msg", S3ExportUtils.describeError(s3Error(403, "AccessDenied")));
        String longMessage = new String(new char[2000]).replace('\0', 'x');
        assertEquals(S3ExportUtils.MAX_ERROR_LENGTH, S3ExportUtils.describeError(new IOException(longMessage)).length());
    }

    @Test
    public void connectionErrorsAreActionable() {
        String arn = "arn:aws:iam::123456789012:role/test";
        assertTrue(S3ExportUtils.connectionErrorMessage(stsError("AccessDenied"), arn).contains("could not assume role " + arn));
        assertTrue(S3ExportUtils.connectionErrorMessage(s3Error(403, "AccessDenied"), arn).contains("s3:PutObject"));
        assertTrue(S3ExportUtils.connectionErrorMessage(s3Error(404, "NoSuchBucket"), arn).contains("bucket was not found"));
        assertTrue(S3ExportUtils.connectionErrorMessage(s3Error(301, "PermanentRedirect"), arn).contains("region"));
        assertNull(S3ExportUtils.findCause(new RuntimeException("x"), StsException.class));
    }
}
