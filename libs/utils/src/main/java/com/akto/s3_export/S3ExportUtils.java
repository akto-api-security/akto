package com.akto.s3_export;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.sts.model.StsException;

/** Pure helpers of the S3 export: time windows, object keys and error handling. */
public final class S3ExportUtils {

    public static final long HOUR_MS = TimeUnit.HOURS.toMillis(1);
    // an hour is exported only once it has been closed for this long, so late-arriving rows are in
    public static final long LAG_MS = TimeUnit.MINUTES.toMillis(15);
    // 2x the longest interval (24h), so a daily job outpaces scheduling drift and still catches up after an outage
    public static final int MAX_HOURS_PER_RUN = 48;
    public static final int FIRST_RUN_BACKFILL_HOURS = 24;

    static final String ROOT_DIR = "akto";
    static final String SUCCESS_FILE = "_SUCCESS";
    static final String CONNECTION_TEST_FILE = "_connection_test.json";
    static final int MAX_ERROR_LENGTH = 500;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("HH").withZone(ZoneOffset.UTC);
    private static final Pattern PREFIX_CHARS = Pattern.compile("^[\\w.\\-/=@+]*$");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // error codes of a 4xx answer that are worth another try
    private static final List<String> RETRYABLE_AWS_CODES = Arrays.asList(
        "RequestTimeout", "SlowDown", "Throttling", "ThrottlingException", "RequestLimitExceeded",
        "ProvisionedThroughputExceededException", "ServiceUnavailable", "InternalError");

    private S3ExportUtils() {}

    // ── time windows ────────────────────────────────────────────────────────────

    public static long hourFloor(long ms) {
        return ms - Math.floorMod(ms, HOUR_MS);
    }

    /** Where to continue from: the checkpoint, or (first run) a day before the current hour. */
    public static long startCursor(Long checkpointMs, long nowMs) {
        return checkpointMs != null ? checkpointMs : hourFloor(nowMs) - FIRST_RUN_BACKFILL_HOURS * HOUR_MS;
    }

    /** Starts of the closed hours (past the lag) to export from the cursor, at most one run's worth. */
    public static List<Long> windowStarts(long cursorMs, long nowMs) {
        List<Long> starts = new ArrayList<>();
        long start = cursorMs;
        while (starts.size() < MAX_HOURS_PER_RUN && start + HOUR_MS + LAG_MS <= nowMs) {
            starts.add(start);
            start += HOUR_MS;
        }
        return starts;
    }

    // ── object keys ─────────────────────────────────────────────────────────────

    /** Trims and strips the slashes around the prefix; rejects ".." and characters S3 tools trip on. */
    public static String normalizePrefix(String prefix) {
        String normalized = prefix == null ? "" : prefix.trim().replaceAll("^/+|/+$", "");
        if (!PREFIX_CHARS.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Prefix can only contain letters, digits and . - _ / = @ +");
        }
        for (String segment : normalized.split("/")) {
            if (segment.equals("..")) throw new IllegalArgumentException("Prefix must not contain '..'");
        }
        return normalized;
    }

    private static String basePath(String prefix) {
        String normalized = normalizePrefix(prefix);
        return normalized.isEmpty() ? ROOT_DIR : normalized + "/" + ROOT_DIR;
    }

    /** "{prefix/}akto/{dataset}/dt=YYYY-MM-DD/hour=HH/" for the hour starting at windowStartMs. */
    public static String hourDirKey(String prefix, S3ExportDataset dataset, long windowStartMs) {
        Instant start = Instant.ofEpochMilli(windowStartMs);
        return basePath(prefix) + "/" + dataset.getPathSegment()
            + "/dt=" + DATE_FORMAT.format(start) + "/hour=" + HOUR_FORMAT.format(start) + "/";
    }

    public static String partKey(String dirKey, int partNumber) {
        return dirKey + String.format("part-%04d.ndjson.gz", partNumber);
    }

    public static String successKey(String dirKey) {
        return dirKey + SUCCESS_FILE;
    }

    public static String connectionTestKey(String prefix) {
        return basePath(prefix) + "/" + CONNECTION_TEST_FILE;
    }

    /** Written last for an hour: its presence says every part of the hour is in place. */
    public static String successMarker(S3ExportDataset dataset, long windowStartMs, long windowEndMs,
                                       long recordCount, int partCount, long exportedAtMs) throws JsonProcessingException {
        Map<String, Object> marker = new LinkedHashMap<>();
        marker.put("dataset", dataset.name());
        marker.put("windowStartMs", windowStartMs);
        marker.put("windowEndMs", windowEndMs);
        marker.put("recordCount", recordCount);
        marker.put("partCount", partCount);
        marker.put("exportedAt", Instant.ofEpochMilli(exportedAtMs).toString());
        return MAPPER.writeValueAsString(marker);
    }

    // ── errors ──────────────────────────────────────────────────────────────────

    /** Throttling, 5xx, timeouts and backend errors may pass on their own; access and config errors will not. */
    public static boolean isRetryable(Throwable error) {
        AwsServiceException aws = findCause(error, AwsServiceException.class);
        if (aws != null) {
            int status = aws.statusCode();
            String code = aws.awsErrorDetails() != null ? aws.awsErrorDetails().errorCode() : null;
            return status >= 500 || status == 429 || (code != null && RETRYABLE_AWS_CODES.contains(code));
        }
        if (findCause(error, SdkClientException.class) != null) return true; // network / timeout
        return !(error instanceof IllegalStateException || error instanceof IllegalArgumentException
            || error instanceof UnsupportedOperationException);
    }

    /** Short, payload-free description of a failure, safe to store and show. */
    public static String describeError(Throwable error) {
        AwsServiceException aws = findCause(error, AwsServiceException.class);
        String text;
        if (aws != null && aws.awsErrorDetails() != null) {
            text = aws.awsErrorDetails().errorCode() + " (HTTP " + aws.statusCode() + "): "
                + aws.awsErrorDetails().errorMessage();
        } else {
            text = error.getClass().getSimpleName() + ": " + error.getMessage();
        }
        return text.length() > MAX_ERROR_LENGTH ? text.substring(0, MAX_ERROR_LENGTH) : text;
    }

    /** Actionable message for a failed connection test. */
    public static String connectionErrorMessage(Throwable error, String roleArn) {
        if (findCause(error, StsException.class) != null) {
            return "Akto could not assume role " + roleArn + ". Check the role ARN, and that its trust policy allows"
                + " Akto's role with the external ID shown on this page. (" + describeError(error) + ")";
        }
        S3Exception s3 = findCause(error, S3Exception.class);
        if (s3 != null && s3.awsErrorDetails() != null) {
            String code = s3.awsErrorDetails().errorCode();
            if ("NoSuchBucket".equals(code)) {
                return "The bucket was not found. Check the bucket name and region.";
            }
            if ("AccessDenied".equals(code)) {
                return "The role was assumed but cannot write to the bucket. Allow s3:PutObject on the bucket and prefix,"
                    + " and for a KMS key also kms:GenerateDataKey and kms:Decrypt. (" + describeError(error) + ")";
            }
            if ("PermanentRedirect".equals(code) || "AuthorizationHeaderMalformed".equals(code)) {
                return "The bucket is in a different region. Check the region.";
            }
        }
        return "Connection test failed. " + describeError(error);
    }

    public static <T extends Throwable> T findCause(Throwable error, Class<T> type) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (type.isInstance(t)) return type.cast(t);
        }
        return null;
    }
}
