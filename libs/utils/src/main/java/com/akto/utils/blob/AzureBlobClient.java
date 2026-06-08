package com.akto.utils.blob;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;

/**
 * Singleton wrapper for Azure Blob Storage. Lazy-initialized; throws on first
 * use if AZURE_STORAGE_CONNECTION_STRING is missing so endpoints can return
 * a clean error instead of failing during request handling.
 *
 * Container layout: {accountId}/{sha256[0:2]}/{sha256}
 * SAS URLs are short-lived (5 minutes) and scoped to a single blob.
 */
public class AzureBlobClient {

    public static final String CONTAINER_ENV = "AZURE_BLOB_CONTAINER";
    public static final String CONNECTION_ENV = "AZURE_STORAGE_CONNECTION_STRING";
    public static final String DEFAULT_CONTAINER = "endpoint-shield-content";

    private static volatile AzureBlobClient instance;
    private final BlobContainerClient container;

    private AzureBlobClient(BlobServiceClient svc, String containerName) {
        BlobContainerClient c = svc.getBlobContainerClient(containerName);
        if (!c.exists()) {
            c.create();
        }
        this.container = c;
    }

    public static AzureBlobClient getInstance() {
        AzureBlobClient local = instance;
        if (local == null) {
            synchronized (AzureBlobClient.class) {
                local = instance;
                if (local == null) {
                    String conn = System.getenv(CONNECTION_ENV);
                    if (conn == null || conn.isEmpty()) {
                        throw new IllegalStateException(CONNECTION_ENV + " is not configured");
                    }
                    String containerName = System.getenv(CONTAINER_ENV);
                    if (containerName == null || containerName.isEmpty()) {
                        containerName = DEFAULT_CONTAINER;
                    }
                    BlobServiceClient svc = new BlobServiceClientBuilder().connectionString(conn).buildClient();
                    local = new AzureBlobClient(svc, containerName);
                    instance = local;
                }
            }
        }
        return local;
    }

    public static String buildBlobName(int accountId, String sha256) {
        if (sha256 == null || sha256.length() < 4) {
            throw new IllegalArgumentException("sha256 must be at least 4 chars");
        }
        return accountId + "/" + sha256.substring(0, 2) + "/" + sha256;
    }

    public boolean exists(String blobName) {
        return container.getBlobClient(blobName).exists();
    }

    /**
     * Upload raw bytes to the given blob name. Overwrites if already exists.
     * Used for server-side uploads where the caller already has the bytes.
     */
    public void upload(String blobName, byte[] data) {
        BlobClient blob = container.getBlobClient(blobName);
        blob.upload(new java.io.ByteArrayInputStream(data), data.length, true);
    }

    /**
     * Download blob content as bytes. Returns null if the blob does not exist.
     */
    public byte[] download(String blobName) {
        BlobClient blob = container.getBlobClient(blobName);
        if (!blob.exists()) return null;
        return blob.downloadContent().toBytes();
    }
}
