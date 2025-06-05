package com.akto;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.*;

import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;

public class PayloadEncodeUtilTest {

    private static RSAPublicKey publicKey;
    private static RSAPrivateKey privateKey;

    @BeforeAll
    public static void setup() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        publicKey = (RSAPublicKey) keyPair.getPublic();
        privateKey = (RSAPrivateKey) keyPair.getPrivate();
    }

    @Test
    public void testEncodeAndDecodePayload() {
        String originalPayload = "{\"message\":\"test\"}";
        String jwt = PayloadEncodeUtil.encodePayload(originalPayload, privateKey);
        assertNotNull(jwt);

        String decodedPayload = PayloadEncodeUtil.decodePayload(jwt, publicKey);
        assertEquals(originalPayload, decodedPayload);
    }

    @Test
    public void testGetPrivateKey_withValidPem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(privateKey.getEncoded());
        String base64Key = Base64.getEncoder().encodeToString(spec.getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + base64Key + "\n-----END PRIVATE KEY-----";
        setEnv("PRIVATE_KEY", pem);

        RSAPrivateKey result = PayloadEncodeUtil.getPrivateKey();
        assertNotNull(result);
    }

    @Test
    public void testGetPublicKey_withValidPem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

        X509EncodedKeySpec spec = new X509EncodedKeySpec(publicKey.getEncoded());
        String base64Key = Base64.getEncoder().encodeToString(spec.getEncoded());
        String pem = "-----BEGIN PUBLIC KEY-----\n" + base64Key + "\n-----END PUBLIC KEY-----";
        setEnv("PUBLIC_KEY", pem);

        RSAPublicKey result = PayloadEncodeUtil.getPublicKey();
        assertNotNull(result);
    }

    // Helper to simulate environment variable override
    private void setEnv(String key, String value) {
        try {
            System.setProperty(key, value); // Not actual env var but helps in isolated test env
            Map<String, String> env = System.getenv();
            Class<?> cl = env.getClass();
            java.lang.reflect.Field field = cl.getDeclaredField("m");
            field.setAccessible(true);
            ((Map<String, String>) field.get(env)).put(key, value);
        } catch (Exception e) {
            // ignore; best effort
        }
    }
}