package com.akto.s3_export;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import software.amazon.awssdk.regions.Region;

/** Validates the S3 export settings a user submits. Each method returns the normalized value or throws IllegalArgumentException with a user-facing message. */
public final class S3ExportValidator {

    public static final List<Integer> ALLOWED_INTERVAL_HOURS = Arrays.asList(1, 6, 24);
    public static final int MAX_INITIAL_BACKFILL_HOURS = 168;

    private static final Pattern BUCKET = Pattern.compile("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$");
    private static final Pattern IP_LIKE = Pattern.compile("^\\d+\\.\\d+\\.\\d+\\.\\d+$");
    private static final Pattern ROLE_ARN = Pattern.compile("^arn:aws:iam::\\d{12}:role/[\\w+=,.@/-]+$");
    private static final Pattern KMS_ARN = Pattern.compile("^arn:aws:kms:[a-z0-9-]+:\\d{12}:(key|alias)/.+$");

    private S3ExportValidator() {}

    public static String validateBucket(String bucket) {
        String value = bucket == null ? "" : bucket.trim();
        if (!BUCKET.matcher(value).matches() || value.contains("..") || value.contains(".-")
            || value.contains("-.") || IP_LIKE.matcher(value).matches()) {
            throw new IllegalArgumentException("Please enter a valid S3 bucket name (3-63 lowercase letters, digits, dots or hyphens).");
        }
        return value;
    }

    public static String validateRegion(String region) {
        String value = region == null ? "" : region.trim();
        for (Region known : Region.regions()) {
            if (known.id().equals(value)) return value;
        }
        throw new IllegalArgumentException("Please select a valid AWS region.");
    }

    public static String validateRoleArn(String roleArn) {
        String value = roleArn == null ? "" : roleArn.trim();
        if (!ROLE_ARN.matcher(value).matches()) {
            throw new IllegalArgumentException("Please enter a valid IAM role ARN (arn:aws:iam::<account-id>:role/<name>).");
        }
        return value;
    }

    /** Empty means the bucket's default encryption (SSE-S3). */
    public static String validateKmsKeyArn(String kmsKeyArn) {
        String value = kmsKeyArn == null ? "" : kmsKeyArn.trim();
        if (!value.isEmpty() && !KMS_ARN.matcher(value).matches()) {
            throw new IllegalArgumentException("Please enter a valid KMS key ARN (arn:aws:kms:<region>:<account-id>:key/<id>).");
        }
        return value;
    }

    public static String validatePrefix(String prefix) {
        return S3ExportUtils.normalizePrefix(prefix);
    }

    public static List<String> validateDatasets(List<String> datasets) {
        if (datasets == null || datasets.isEmpty()) {
            throw new IllegalArgumentException("Please select at least one dataset to export.");
        }
        Set<String> valid = new LinkedHashSet<>();
        for (String name : datasets) {
            if (S3ExportDataset.fromName(name) == null) {
                throw new IllegalArgumentException("Unknown dataset: " + name);
            }
            valid.add(name);
        }
        return new ArrayList<>(valid);
    }

    public static int validateIntervalHours(int intervalHours) {
        if (!ALLOWED_INTERVAL_HOURS.contains(intervalHours)) {
            throw new IllegalArgumentException("Export interval must be 1, 6 or 24 hours.");
        }
        return intervalHours;
    }

    public static int validateInitialBackfillHours(int initialBackfillHours) {
        if (initialBackfillHours < 0 || initialBackfillHours > MAX_INITIAL_BACKFILL_HOURS) {
            throw new IllegalArgumentException("Initial backfill must be between 0 and " + MAX_INITIAL_BACKFILL_HOURS + " hours.");
        }
        return initialBackfillHours;
    }
}
