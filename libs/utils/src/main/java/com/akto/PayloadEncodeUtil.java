package com.akto;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

public class PayloadEncodeUtil {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(PayloadEncodeUtil.class, LogDb.RUNTIME);

    public static RSAPrivateKey getPrivateKey() {
        try {
            String privateKeyPem = System.getenv("PRIVATE_KEY");
            if (privateKeyPem == null || privateKeyPem.isEmpty()) {
                throw new IllegalStateException("Environment variable RSA_PRIVATE_KEY is not set or empty");
            }

            // Remove PEM headers/footers and whitespace
            privateKeyPem = privateKeyPem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");

            // Decode Base64 and parse to RSAPrivateKey
            byte[] decodedKey = Base64.getDecoder().decode(privateKeyPem);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decodedKey);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);   
        } catch (Exception e) {
            return null;
        }
    }

    public static RSAPublicKey getPublicKey() {
        try {
            String publicKeyPem = System.getenv("PUBLIC_KEY");
            if (publicKeyPem == null || publicKeyPem.isEmpty()) {
                throw new IllegalStateException("Environment variable RSA_PUBLIC_KEY is not set or empty");
            }

            // Remove PEM headers/footers and whitespace
            publicKeyPem = publicKeyPem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");

            // Decode Base64 and parse to RSAPublicKey
            byte[] decodedKey = Base64.getDecoder().decode(publicKeyPem);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decodedKey);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) keyFactory.generatePublic(keySpec);
        } catch (Exception e) {
            return null;
        }
        
    }

    public static KeyPair generateRSAKeyPairFromSecret() {
        String secretKey = System.getenv("SECRET_KEY");
        if (secretKey == null || secretKey.isEmpty()) {
            loggerMaker.errorAndAddToDb("payload encode secret key absent, avoiding key pair generation");
            return null;
        }

        try {
            byte[] seed = secretKey.getBytes("UTF-8");
            SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
            secureRandom.setSeed(seed);

            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048, secureRandom);
            return keyPairGenerator.generateKeyPair();   
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error generating keypair for payload encode secret key");
            return null;
        }
    }

    public static String encodePayload(String payload, RSAPublicKey publicKey) throws Exception {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedBytes = cipher.doFinal(payload.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new Exception("Error encoding payload with public key", e);
        }
    }

    public static String decodePayload(String payload, RSAPrivateKey privateKey) throws Exception {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decodedBytes = cipher.doFinal(Base64.getDecoder().decode(payload));
            return new String(decodedBytes, "UTF-8");
        } catch (Exception e) {
            throw new Exception("Error decoding payload with private key", e);
        }
    }


    public static String encryptAndPack(String payload, RSAPublicKey rsaPublicKey) throws Exception {
        // Generate AES key
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        SecretKey aesKey = keyGen.generateKey();
    
        // Generate IV
        byte[] iv = new byte[12];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);
    
        // Encrypt payload with AES
        Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, spec);
        byte[] encryptedPayload = aesCipher.doFinal(payload.getBytes("UTF-8"));
    
        // Encrypt AES key with RSA
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsaCipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
        byte[] encryptedAesKey = rsaCipher.doFinal(aesKey.getEncoded());
    
        // Encode all parts to Base64
        String encKey = Base64.getEncoder().encodeToString(encryptedAesKey);
        String encIv = Base64.getEncoder().encodeToString(iv);
        String encData = Base64.getEncoder().encodeToString(encryptedPayload);
    
        // Pack as single string
        return encKey + ":" + encIv + ":" + encData;
    }
    
    public static String decryptPacked(String packed, RSAPrivateKey rsaPrivateKey) throws Exception {
        String[] parts = packed.split(":");
        if (parts.length != 3) throw new IllegalArgumentException("Invalid encrypted string format");

        byte[] encryptedAesKey = Base64.getDecoder().decode(parts[0]);
        byte[] iv = Base64.getDecoder().decode(parts[1]);
        byte[] encryptedPayload = Base64.getDecoder().decode(parts[2]);

        // Decrypt AES key using RSA
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsaCipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey);
        byte[] aesKeyBytes = rsaCipher.doFinal(encryptedAesKey);
        SecretKeySpec aesKey = new SecretKeySpec(aesKeyBytes, "AES");

        // Decrypt payload using AES
        Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey, spec);
        byte[] decrypted = aesCipher.doFinal(encryptedPayload);

        return new String(decrypted, "UTF-8");
    }

}
