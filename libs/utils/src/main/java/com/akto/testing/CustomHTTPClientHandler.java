package com.akto.testing;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dto.testing.TLSAuthParam;
import com.akto.util.http_util.CoreHTTPClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import okhttp3.OkHttpClient;

public class CustomHTTPClientHandler {

    private static CertificateFactory _certificateFactory;

    private static final Logger logger = LoggerFactory.getLogger(CustomHTTPClientHandler.class);

    private static CertificateFactory certificateFactory() {
        try {
            if (_certificateFactory == null) {
                _certificateFactory = CertificateFactory.getInstance("X.509");
            }
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
        return _certificateFactory;
    }
    // TLSAuthParam -> hashCode
    private static Map<Integer, OkHttpClient> tlsClientMap = new HashMap<>();

    public static CustomHTTPClientHandler instance = new CustomHTTPClientHandler();

    public OkHttpClient getClient(TLSAuthParam authParam, boolean isHttps, boolean followRedirect, String contentType) throws Exception {
        if (authParam == null) {
            return CoreHTTPClient.client;
        }

        int hashCode = authParam.hashCode() + (isHttps ? 1 : 0) + (followRedirect ? 1 : 0) + contentType.hashCode();

        if (!tlsClientMap.containsKey(hashCode)) {
            OkHttpClient okHttpClient = getTLSClient(authParam, isHttps, followRedirect,contentType);
            tlsClientMap.put(hashCode, okHttpClient);
        }

        return tlsClientMap.get(hashCode);
    }

    private OkHttpClient getTLSClient(TLSAuthParam authParam, boolean isHttps, boolean followRedirect, String contentType) throws Exception {
        try {
            TrustManager[] trustManagers = getTrustManagers(authParam.getCAcertificate());
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(getKeyManagers(authParam.getClientKey(), authParam.getClientCertificate()), trustManagers,
                    new java.security.SecureRandom());

            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient okHttpClient = HTTPClientHandler.instance.getHTTPClient(isHttps, followRedirect, contentType).newBuilder()
                    .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustManagers[0])
                    // might need to revisit this.
                    .hostnameVerifier((hostname, session) -> true)
                    .build();

            return okHttpClient;
        } catch (Exception e) {
            logger.error("Error in creating TLS client ", e);
        }
        return CoreHTTPClient.client.newBuilder().build();
    }

    private KeyManager[] getKeyManagers(String privateKey, String certificate) throws Exception {

        String privateKeyContent = privateKey
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replaceAll(System.lineSeparator(), "")
                .replace("-----END PRIVATE KEY-----", "");

        byte[] privateKeyAsBytes = Base64.getDecoder().decode(privateKeyContent);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyAsBytes);

        InputStream certificateChainAsInputStream = IOUtils.toInputStream(certificate, Charset.defaultCharset());
        Certificate certificateChain = certificateFactory().generateCertificate(certificateChainAsInputStream);

        KeyStore identityStore = createEmptyKeyStore("secret".toCharArray());
        identityStore.setKeyEntry("client", keyFactory.generatePrivate(keySpec), "secret".toCharArray(),
                new Certificate[] { certificateChain });
        certificateChainAsInputStream.close();

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(identityStore, "secret".toCharArray());
        KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();

        return keyManagers;
    }

    private TrustManager[] getTrustManagers(String CAcertificate) throws Exception {

        if (CAcertificate == null || CAcertificate.isEmpty()) {
            return CoreHTTPClient.trustAllCerts;
        }

        InputStream trustedCertificateAsInputStream = IOUtils.toInputStream(CAcertificate, Charset.defaultCharset());
        Certificate trustedCertificate = certificateFactory().generateCertificate(trustedCertificateAsInputStream);
        KeyStore trustStore = createEmptyKeyStore("secret".toCharArray());
        trustStore.setCertificateEntry("server-certificate", trustedCertificate);
        trustedCertificateAsInputStream.close();

        TrustManagerFactory trustManagerFactory = TrustManagerFactory
                .getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

        return trustManagers;
    }

    private KeyStore createEmptyKeyStore(char[] keyStorePassword)
            throws CertificateException, NoSuchAlgorithmException, IOException, KeyStoreException {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, keyStorePassword);
        return keyStore;
    }

}
