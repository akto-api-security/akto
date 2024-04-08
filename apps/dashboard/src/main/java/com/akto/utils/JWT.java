package com.akto.utils;

import com.akto.onprem.Constants;
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

        byte[] privateKey = Constants.getPrivateKey();
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKey);
        KeyFactory kf = KeyFactory.getInstance("RSA");

        return kf.generatePrivate(keySpec);
    }

    private static PublicKey getPublicKey(String publicKeyPath) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {

        byte[] publicKey = Constants.getPublicKey();
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKey);
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
