package com.akto.testing;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

public class HTTPClientHandler {
    private int readTimeout = 30;
    private final OkHttpClient clientWithoutFollowRedirect;
    private final OkHttpClient clientWithFollowRedirect;
    private HTTPClientHandler(boolean isSaas) {
        if(isSaas) {
            readTimeout = 60;
        }
        clientWithoutFollowRedirect = new OkHttpClient().newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(256, 5L, TimeUnit.MINUTES))
                .followRedirects(false)
                .sslSocketFactory(trustAllSslSocketFactory, (X509TrustManager)trustAllCerts[0])
                .hostnameVerifier((hostname, session) -> true)
                .build();

        clientWithFollowRedirect = new OkHttpClient().newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(256, 5L, TimeUnit.MINUTES))
                .followRedirects(true)
                .sslSocketFactory(trustAllSslSocketFactory, (X509TrustManager)trustAllCerts[0])
                .hostnameVerifier((hostname, session) -> true)
                .build();

    }

    private static final TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[]{};
                }
            }
    };

    private static final SSLContext trustAllSslContext;
    static {
        try {
            trustAllSslContext = SSLContext.getInstance("SSL");
            trustAllSslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    private static final SSLSocketFactory trustAllSslSocketFactory = trustAllSslContext.getSocketFactory();

    public static HTTPClientHandler instance = null;

    public static void initHttpClientHandler(boolean isSaas) {
        if (instance == null) {
            instance = new HTTPClientHandler(isSaas);
        }
    }

    public OkHttpClient getHTTPClient (boolean followRedirect) {
        if (followRedirect) {
            return clientWithFollowRedirect;
        }
        return clientWithoutFollowRedirect;
    }
}
