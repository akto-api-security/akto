package com.akto.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.lang.RuntimeEnvironment;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.*;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

public class JWT {

    private static final byte[] privateKey, publicKey;

    static {
        KeyPairGenerator kpg;
        KeyPair kp = null;
        try {
            kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            kp = kpg.generateKeyPair();
            RuntimeEnvironment.enableBouncyCastleIfPossible();
        } catch (NoSuchAlgorithmException e) {
            ;
        } 

        privateKey = kp == null ? null : kp.getPrivate().getEncoded();
        publicKey = kp == null ? null : kp.getPublic().getEncoded();
    }

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

    static PrivateKey get(byte[] secrets) throws Exception {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(secrets);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    public static String createJWT(String githubAppId,String secret, long ttlMillis) throws Exception {
        //The JWT signature algorithm we will be using to sign the token
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.RS256;

        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);

        //We will sign our JWT with our private key
        Key signingKey = get(Base64.getDecoder().decode(secret));

        //Let's set the JWT Claims
        JwtBuilder builder = Jwts.builder()
                .setIssuedAt(now)
                .setIssuer(githubAppId)
                .signWith(signingKey, signatureAlgorithm);

        //if it has been specified, let's add the expiration
        if (ttlMillis > 0) {
            long expMillis = nowMillis + ttlMillis;
            Date exp = new Date(expMillis);
            builder.setExpiration(exp);
        }

        //Builds the JWT and serializes it to a compact, URL-safe string
        return builder.compact();
    }

    private static PrivateKey getPrivateKey(String privateKeyPath) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKey);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(keySpec);
    }

    private static PublicKey getPublicKey(String publicKeyPath) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
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
