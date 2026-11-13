package com.akto.s3_export;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.StsClientBuilder;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

/**
 * Writes to the customer's bucket as the customer's role. The base credentials are the default
 * chain (in SaaS the dashboard pod's IRSA role); with them the role is assumed using the
 * external id Akto generated for the account. One writer per run; close it when done.
 */
public class S3ExportWriter implements S3ObjectUploader, AutoCloseable {

    // dev/test only, e.g. LocalStack
    private static final String ENDPOINT_OVERRIDE_ENV = "AKTO_S3_EXPORT_ENDPOINT_OVERRIDE";
    private static final String SESSION_NAME_PREFIX = "akto-s3-export-";
    private static final int SESSION_SECONDS = 3600;
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String CONNECTION_TEST_BODY = "{\"source\":\"akto\",\"test\":true}";

    private final String bucket;
    private final String kmsKeyArn;
    private final StsClient stsClient;
    private final StsAssumeRoleCredentialsProvider credentials;
    private final S3Client s3Client;

    public S3ExportWriter(int accountId, String bucket, String region, String roleArn, String externalId, String kmsKeyArn) {
        this.bucket = bucket;
        this.kmsKeyArn = kmsKeyArn;
        String endpointOverride = System.getenv(ENDPOINT_OVERRIDE_ENV);

        // the STS client uses the default credential chain, and is closed with the writer
        StsClientBuilder sts = StsClient.builder().region(Region.of(region));
        S3ClientBuilder s3 = S3Client.builder().region(Region.of(region));
        if (endpointOverride != null && !endpointOverride.isEmpty()) {
            sts.endpointOverride(URI.create(endpointOverride));
            s3.endpointOverride(URI.create(endpointOverride)).forcePathStyle(true);
        }
        this.stsClient = sts.build();
        this.credentials = StsAssumeRoleCredentialsProvider.builder()
            .stsClient(stsClient)
            .refreshRequest(AssumeRoleRequest.builder()
                .roleArn(roleArn)
                .externalId(externalId)
                .roleSessionName(SESSION_NAME_PREFIX + accountId)
                .durationSeconds(SESSION_SECONDS)
                .build())
            .build();
        this.s3Client = s3.credentialsProvider(credentials).build();
    }

    @Override
    public void upload(String key, byte[] body, String contentType) {
        PutObjectRequest.Builder request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(contentType);
        if (kmsKeyArn != null && !kmsKeyArn.isEmpty()) {
            request.serverSideEncryption(ServerSideEncryption.AWS_KMS).ssekmsKeyId(kmsKeyArn);
        } else {
            request.serverSideEncryption(ServerSideEncryption.AES256);
        }
        s3Client.putObject(request.build(), RequestBody.fromBytes(body));
    }

    public void uploadJson(String key, String json) {
        upload(key, json.getBytes(StandardCharsets.UTF_8), JSON_CONTENT_TYPE);
    }

    /** Assumes the role, then puts {prefix/}akto/_connection_test.json. Throws the AWS exception on failure. */
    public static void testConnection(int accountId, String bucket, String region, String prefix, String roleArn,
                                      String externalId, String kmsKeyArn) {
        try (S3ExportWriter writer = new S3ExportWriter(accountId, bucket, region, roleArn, externalId, kmsKeyArn)) {
            // resolve first so a failed role assumption is reported as such, not as a failed put
            writer.credentials.resolveCredentials();
            writer.uploadJson(S3ExportUtils.connectionTestKey(prefix), CONNECTION_TEST_BODY);
        }
    }

    @Override
    public void close() {
        s3Client.close();
        credentials.close();
        stsClient.close();
    }
}
