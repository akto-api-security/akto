package com.akto.database_abstractor_authenticator;

import com.akto.dao.ConfigsDao;
import com.akto.dto.Config;
import com.akto.dto.Config.HybridSaasConfig;
import com.mongodb.client.model.Filters;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;

import java.io.IOException;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

public class JwtAuthenticator {

    public static String createJWT(Map<String, Object> claims, String issuer, String subject, int expiryUnit, int expiryDuration)
            throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {

        PrivateKey privateKey = getPrivateKey();

        Calendar calendar = Calendar.getInstance();
        java.util.Date issueTime = calendar.getTime();

        calendar.setTime(issueTime);
        if(expiryDuration < 0){
           return Jwts.builder()
                .setIssuer(issuer)
                .setSubject(subject)
                .addClaims(claims)
                .setIssuedAt(issueTime)
                .signWith(privateKey)
                .compact();
        }
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

    private static PrivateKey getPrivateKey() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        HybridSaasConfig config = (Config.HybridSaasConfig) ConfigsDao.instance.findOne(Filters.eq("_id", "HYBRID_SAAS"));
        String rsaPrivateKey = config.getPrivateKey();

        rsaPrivateKey = rsaPrivateKey.replace("-----BEGIN PRIVATE KEY-----","");
        rsaPrivateKey = rsaPrivateKey.replace("-----END PRIVATE KEY-----","");
        rsaPrivateKey = rsaPrivateKey.replace("\n","");


        byte [] decoded = Base64.getDecoder().decode(rsaPrivateKey);

        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");

        return kf.generatePrivate(keySpec);
    }

    public static Jws<Claims> authenticate(String jwsString)
            throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {

         PublicKey publicKey = getPublicKey();
         return Jwts.parserBuilder()
                 .setSigningKey(publicKey)
                 .build()
                 .parseClaimsJws(jwsString);
    }

    public static byte[] readFileBytes(String filename) throws IOException
    {
        Path path = Paths.get(filename);
        return Files.readAllBytes(path);        
    }

    private static PublicKey getPublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        HybridSaasConfig config = null;
        try {
            config = (HybridSaasConfig) ConfigsDao.instance.findOne("_id", Config.ConfigType.HYBRID_SAAS.name());
        } catch (Exception e) {
            System.out.println(e);
            throw e;
        }
        String rsaPublicKey = config.getPublicKey();

        rsaPublicKey = rsaPublicKey.replace("-----BEGIN PUBLIC KEY-----","");
        rsaPublicKey = rsaPublicKey.replace("-----END PUBLIC KEY-----","");
        rsaPublicKey = rsaPublicKey.replace("\n","");
        byte [] decoded = Base64.getDecoder().decode(rsaPublicKey);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");

        try {
            return kf.generatePublic(keySpec);
        } catch (Exception e) {
            System.out.println(e);
            throw e;
        }
    }

}
