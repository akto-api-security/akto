package com.akto.s3_export;

/** Puts one object into the export bucket. Failures are unchecked (AWS SDK exceptions). */
public interface S3ObjectUploader {

    void upload(String key, byte[] body, String contentType);
}
