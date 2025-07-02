package com.akto;

import static org.junit.jupiter.api.Assertions.*;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import org.junit.jupiter.api.*;

public class PayloadEncodeUtilTest {

    private static RSAPublicKey publicKey;
    private static RSAPrivateKey privateKey;

    @BeforeAll
    public static void setupKeys() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048); // Minimum recommended for security
        KeyPair keyPair = keyGen.generateKeyPair();

        publicKey = (RSAPublicKey) keyPair.getPublic();
        privateKey = (RSAPrivateKey) keyPair.getPrivate();
    }

    @Test
    public void testEncryptAndDecrypt() throws Exception {
        String originalPayload = "{\"message\":\"Hello from Akto!\",\"value\":123}";

        // Encrypt
        String encrypted = PayloadEncodeUtil.encryptAndPack(originalPayload, publicKey);
        assertNotNull(encrypted);
        assertTrue(encrypted.split(":").length == 3);

        // Decrypt
        String decrypted = PayloadEncodeUtil.decryptPacked(encrypted, privateKey);
        assertEquals(originalPayload, decrypted);
    }

    @Test
    public void testDecryptWithInvalidDataFails() {
        String tampered = "invalid:base64:data";

        Exception exception = assertThrows(Exception.class, () -> {
            PayloadEncodeUtil.decryptPacked(tampered, privateKey);
        });

        assertTrue(exception != null);
    }

    @Test
    public void testEncryptDecryptLargePayload() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("{\"line\":").append(i).append(",\"data\":\"some long value\"},");
        }
        String largePayload = sb.toString();

        String encrypted = PayloadEncodeUtil.encryptAndPack(largePayload, publicKey);
        String decrypted = PayloadEncodeUtil.decryptPacked(encrypted, privateKey);

        assertEquals(largePayload, decrypted);
    }

}