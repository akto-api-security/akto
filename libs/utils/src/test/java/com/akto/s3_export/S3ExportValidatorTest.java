package com.akto.s3_export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class S3ExportValidatorTest {

    private static void assertRejected(Runnable validation) {
        try {
            validation.run();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void bucketNameRules() {
        assertEquals("my-bucket.logs", S3ExportValidator.validateBucket(" my-bucket.logs "));
        for (String bad : new String[] {null, "", "ab", "My-Bucket", "-bucket", "bucket-", "a..b", "192.168.1.1", "bucket_name"}) {
            assertRejected(() -> S3ExportValidator.validateBucket(bad));
        }
    }

    @Test
    public void regionMustBeAKnownAwsRegion() {
        assertEquals("us-east-1", S3ExportValidator.validateRegion("us-east-1"));
        assertRejected(() -> S3ExportValidator.validateRegion("mars-north-1"));
        assertRejected(() -> S3ExportValidator.validateRegion(null));
    }

    @Test
    public void roleArnFormat() {
        String arn = "arn:aws:iam::123456789012:role/path/akto-export";
        assertEquals(arn, S3ExportValidator.validateRoleArn(arn));
        for (String bad : new String[] {null, "", "arn:aws:iam::123:role/x", "arn:aws:iam::123456789012:user/x", "role/x"}) {
            assertRejected(() -> S3ExportValidator.validateRoleArn(bad));
        }
    }

    @Test
    public void kmsKeyArnIsOptional() {
        assertEquals("", S3ExportValidator.validateKmsKeyArn(null));
        assertEquals("", S3ExportValidator.validateKmsKeyArn("  "));
        String key = "arn:aws:kms:us-east-1:123456789012:key/1234abcd";
        assertEquals(key, S3ExportValidator.validateKmsKeyArn(key));
        assertEquals("arn:aws:kms:us-east-1:123456789012:alias/export",
            S3ExportValidator.validateKmsKeyArn("arn:aws:kms:us-east-1:123456789012:alias/export"));
        assertRejected(() -> S3ExportValidator.validateKmsKeyArn("arn:aws:kms:us-east-1:123456789012:thing/x"));
    }

    @Test
    public void datasetsMustBeKnownAndNonEmpty() {
        assertEquals(Arrays.asList("ATLAS_TRACES", "ATLAS_GUARDRAIL_EVENTS"),
            S3ExportValidator.validateDatasets(Arrays.asList("ATLAS_TRACES", "ATLAS_GUARDRAIL_EVENTS", "ATLAS_TRACES")));
        assertRejected(() -> S3ExportValidator.validateDatasets(null));
        assertRejected(() -> S3ExportValidator.validateDatasets(Collections.emptyList()));
        assertRejected(() -> S3ExportValidator.validateDatasets(Arrays.asList("ATLAS_TRACES", "NOPE")));
    }

    @Test
    public void intervalAndBackfillRanges() {
        for (int ok : new int[] {1, 6, 24}) assertEquals(ok, S3ExportValidator.validateIntervalHours(ok));
        for (int bad : new int[] {0, 2, 12, 48}) assertRejected(() -> S3ExportValidator.validateIntervalHours(bad));
        assertEquals(0, S3ExportValidator.validateInitialBackfillHours(0));
        assertEquals(168, S3ExportValidator.validateInitialBackfillHours(168));
        assertRejected(() -> S3ExportValidator.validateInitialBackfillHours(-1));
        assertRejected(() -> S3ExportValidator.validateInitialBackfillHours(169));
    }

    @Test
    public void prefixIsNormalized() {
        assertEquals("a/b", S3ExportValidator.validatePrefix("/a/b/"));
        assertRejected(() -> S3ExportValidator.validatePrefix("a/../b"));
    }
}
