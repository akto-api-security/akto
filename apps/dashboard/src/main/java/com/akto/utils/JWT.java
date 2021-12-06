package com.akto.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;

import java.io.*;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Calendar;
import java.util.Map;

public class JWT {

    public static String createJWT(String privateKeyPath, Map<String, Object> claims,
                                   String issuer, String subject, int expiryUnit, int expiryDuration)
            throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {

        PrivateKey privateKey = getPrivateKey(privateKeyPath);

        Calendar calendar = Calendar.getInstance();
        java.util.Date issueTime = calendar.getTime();

        calendar.setTime(issueTime);
        calendar.add(expiryUnit, expiryDuration);
        java.util.Date expiryTime = calendar.getTime();


        return Jwts.builder()
                .setIssuer(issuer)
                .setSubject(subject)
                .addClaims(claims)
                .setIssuedAt(issueTime)
                .setExpiration(expiryTime)
                .signWith(privateKey)
                .compact();
    }

    public static Jws<Claims> parseJwt(String jwsString, String publicKeyPath)
            throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {

        PublicKey publicKey= getPublicKey(publicKeyPath);

        return Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .build()
                .parseClaimsJws(jwsString);
    }


    private static PrivateKey getPrivateKey(String privateKeyPath) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {

        String rsaPrivateKey = "-----BEGIN PRIVATE KEY-----\n" +
            "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDHMhUHHu5tPkUO\n" +
            "v25+zcInH68kCunuVVg5jkAht9g1znXvAw2PZtLJp5PFQe0EZjnDGLmqZtcF1mSp\n" +
            "4yLFvt2+8TXZ7SHT9wd274sa4GX/8VkRwgEgEgqE+XE+zGD0OIcVBOiV/aI8l/3j\n" +
            "wHjN8nOoAi/PZo21q/HKPsIOvco70iJOfQREqvZhROvmbpGSLcY9M5EpUBPMX2kP\n" +
            "hpsIc1S3mOJ1O6ggwXkuopaDbbznXQ1vXOVFTs1rrgbVGF/8fMnwNesnOFMvnN7S\n" +
            "mdI14WILyxxk03gzlOpKeXSl0KjpppAwPHXOJO+PubG7blGmBrdIKoiKwLNgevy5\n" +
            "sqCJPNGrAgMBAAECggEBAKApxbyXA1C1S+OCyr6MhLref/10BQpMdwa4ORqjbuY8\n" +
            "npjSlJmkLIJWCrwFuSTbaUBUZhz8WZHpA8GRzwi+4h9AZXNXduau7sKT8v5mRb1Y\n" +
            "eqyLmEoIF4s54fODc8WKmhqterH3YPZlo4/312qIsrP5JiYWKIVVvrFGatRdh/k2\n" +
            "HpBDBT5zYcPS+JYoznwEollaZB732KpcFIy+xIIZwnZ+IxxkxUNSbOIuRPC8ejMI\n" +
            "ev5tlClX8PnMRYMbVjifQqkEghhZki9QMTrxKVipedxjsILZu5dhE0WfMyVfWfOf\n" +
            "4Bxx+5Nf55r8jlArSubAIv0p/sqUTtY8FT9dKNjTUzECgYEA5NvqgpnxaZxHAqVe\n" +
            "t8zkHQ4A4m78YldxAwTNVCTWlbGrAERyybuCIwRLA3oZJsqGaJArNCLd8FBo6aZj\n" +
            "M8I/VMVY7TZ/4Xg8zVqTZwpwDDcYYZyJwXbYMCfKX0oD2qVVqLCWMFJGFpFdyhGD\n" +
            "t0g3jpUAGVmZMOLUbRLZpiq6jfMCgYEA3tGYRAx6NVTi9Mbr3jxz3xwfDaY/lQ4X\n" +
            "I7MsbaXl4MMCnX0reyXiVgp/BtrIUUPwONd6AebOf4BDyAbrk9oZkbZppPqH3DKd\n" +
            "aM7VWFhhpVILnV+Wv051e+xtBMUlgRIvh7WBpo6/NiOsc5ggt7Cj8nFQTIf9BLN5\n" +
            "c273mkymQ2kCgYAuj0MdgN4pYz7bHfOwJwH9mzy41GWKSEnYmGCBjuENIVEtw78q\n" +
            "pXgrUZNFSNDXIHrJyRLRD4rheipt5ojmHc2unFukHuNTZpfQG25Dl08GXSsbqLIp\n" +
            "5tjCq0WzryYrlFlh8VMMz6AstTREiN2oKwwKeOvFPII6/NrEugIHmUfM2wKBgFUi\n" +
            "H9tgKRFXbZ+4oUY4Ms18ISFF8+UvjQfG3aLHUB2gZM9nk8VppCDbwc0MqpziKFaP\n" +
            "fb5cQWnVyBAVFM1Y94wE9jhiwKYRcLMq07j9i9Ful6F5dE+D2r+OhdjcuoemOyLO\n" +
            "5sqMn+I/nxRLe7SzePCY+uVC0BmLuUuxikkaICyBAoGAdt8UqtENved5DEFpUep4\n" +
            "KYlur8dCa5tXzfqnlkPc8Mm+CEunWDZJPb9Vx/wUUk6UTrEHRdPBiy6spvO4F0Og\n" +
            "58oY3YGgOCq2mTOxNqLa/6lAgJRDmsxpAYCedWbz54egyz7NtNi6b1WH0iXxkUqC\n" +
            "6dIM5dJAAB3PnHBzZBRNpVk=\n" +
            "-----END PRIVATE KEY-----\n";//new String(keyBytes);
        rsaPrivateKey = rsaPrivateKey.replace("-----BEGIN PRIVATE KEY-----","");
        rsaPrivateKey = rsaPrivateKey.replace("-----END PRIVATE KEY-----","");
        rsaPrivateKey = rsaPrivateKey.replace("\n","");

        byte [] decoded = Base64.getDecoder().decode(rsaPrivateKey);

        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");

        return kf.generatePrivate(keySpec);
    }

    private static PublicKey getPublicKey(String publicKeyPath) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {

        String rsaPublicKey= "-----BEGIN PUBLIC KEY-----\n" +
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxzIVBx7ubT5FDr9ufs3C\n" +
            "Jx+vJArp7lVYOY5AIbfYNc517wMNj2bSyaeTxUHtBGY5wxi5qmbXBdZkqeMixb7d\n" +
            "vvE12e0h0/cHdu+LGuBl//FZEcIBIBIKhPlxPsxg9DiHFQTolf2iPJf948B4zfJz\n" +
            "qAIvz2aNtavxyj7CDr3KO9IiTn0ERKr2YUTr5m6Rki3GPTORKVATzF9pD4abCHNU\n" +
            "t5jidTuoIMF5LqKWg228510Nb1zlRU7Na64G1Rhf/HzJ8DXrJzhTL5ze0pnSNeFi\n" +
            "C8scZNN4M5TqSnl0pdCo6aaQMDx1ziTvj7mxu25Rpga3SCqIisCzYHr8ubKgiTzR\n" +
            "qwIDAQAB\n" +
            "-----END PUBLIC KEY-----\n";//new String(keyBytes);
        rsaPublicKey = rsaPublicKey.replace("-----BEGIN PUBLIC KEY-----","");
        rsaPublicKey = rsaPublicKey.replace("-----END PUBLIC KEY-----","");
        rsaPublicKey = rsaPublicKey.replace("\n","");

        byte [] decoded = Base64.getDecoder().decode(rsaPublicKey);

        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");

        return kf.generatePublic(keySpec);
    }

    private static String readKey(String keyPath) throws IOException {
        File f = new File(keyPath);
        FileInputStream fis = new FileInputStream(f);
        DataInputStream dis = new DataInputStream(fis);
        byte[] keyBytes = new byte[(int) f.length()];
        dis.readFully(keyBytes);
        dis.close();

        return new String(keyBytes);
    }
}
