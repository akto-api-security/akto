package com.akto.utils;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;

public class DependencyBucketS3Util {

    private final S3Client s3Client;
    private final String BUCKET_NAME = "dependencygraphbucket";
    public DependencyBucketS3Util() {
        s3Client = S3Client.builder().build();
    }

    public void uploadSwaggerSchema(String jobId, String swaggerSchema, boolean isJson) {
        String key = jobId+"/SwaggerSchema" + (isJson ? ".json" : ".yml");
        uploadData(key, swaggerSchema);
    }

    public String getSwaggerSchema(String jobId) {
        String key = jobId+"/SwaggerSchema.yml";
        return getData(key);
    }

    public void uploadApiResultJson(String jobId, String apiResultJson) {
        String key = jobId+"/ApiResultJson.json";
        uploadData(key, apiResultJson);
    }

    public String getApiResultJson(String jobId) {
        String key = jobId+"/ApiResultJson.json";
        return getData(key);
    }

    private void uploadData(String key, String data) {
        byte[] content = data.getBytes();

        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(BUCKET_NAME)
                        .key(key)
                        .build(),
                RequestBody.fromBytes(content));
    }

    private String getData(String key) {
        try {
            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(key)
                    .build(), ResponseTransformer.toBytes());

            byte[] content = objectBytes.asByteArray();

            return new String(content, StandardCharsets.UTF_8);
        } catch(NoSuchKeyException noSuchKeyException) {
            return null;
        } catch(Exception e) {
            System.err.println(e.getMessage());
            return e.getMessage();
        }
    }

    public void uploadErrorMessages(String jobId, String error) {
        String key = jobId+"/ErrorMessages.txt";
        byte[] content = error.getBytes();

        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(BUCKET_NAME)
                        .key(key)
                        .build(),
                RequestBody.fromBytes(content));
    }

    public String getErrorMessages(String jobId) {
        String key = jobId+"/ErrorMessages.txt";

        return getData(key);
    }

    public void close() {
        s3Client.close();
    }

}
