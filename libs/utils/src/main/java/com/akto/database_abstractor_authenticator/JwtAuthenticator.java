package com.akto.database_abstractor_authenticator;

import com.akto.dao.ConfigsDao;
import com.akto.dto.Config;
import com.akto.dto.Config.HybridSaasConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;

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
import java.util.concurrent.TimeUnit;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

public class JwtAuthenticator {
    private static final LoggerMaker loggerMaker = new LoggerMaker(JwtAuthenticator.class, LogDb.DB_ABS);

    // The public key is read from the configs collection and parsed, so cache it instead of doing that on every request.
    private static final long PUBLIC_KEY_TTL_MS = TimeUnit.MINUTES.toMillis(10);
    // A bad signature can mean the key was rotated; reload at most this often so forged tokens can't force a DB read each.
    private static final long PUBLIC_KEY_MIN_RELOAD_MS = TimeUnit.SECONDS.toMillis(30);
    private static final Object publicKeyLock = new Object();
    private static volatile PublicKey cachedPublicKey;
    private static volatile long publicKeyLoadedAt;
    private static volatile long lastForcedReloadAt;

    interface PublicKeyLoader {
        PublicKey load() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException;
    }

    // Package-private so tests can run without a database
    static PublicKeyLoader publicKeyLoader = JwtAuthenticator::loadPublicKey;

    static void resetPublicKeyCacheForTest() {
        synchronized (publicKeyLock) {
            cachedPublicKey = null;
            publicKeyLoadedAt = 0;
            lastForcedReloadAt = 0;
        }
    }

    public static String createJWT(Map<String, Object> claims, String issuer, String subject, int expiryUnit, int expiryDuration)
            throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {

        PrivateKey privateKey = getPrivateKey();

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

    public static String createJWT(Map<String, Object> claims, String issuer, String subject, java.util.Date expiry)
            throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {

        PrivateKey privateKey = getPrivateKey();

        java.util.Date issueTime = Calendar.getInstance().getTime();

        return Jwts.builder()
                .setIssuer(issuer)
                .setSubject(subject)
                .addClaims(claims)
                .setIssuedAt(issueTime)
                .setExpiration(expiry)
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

        PublicKey publicKey = getPublicKey(false);
        try {
            return parse(jwsString, publicKey);
        } catch (SignatureException e) {
            // Retry only if the key actually changed; otherwise the token is simply bad
            PublicKey reloaded = getPublicKey(true);
            if (reloaded.equals(publicKey)) {
                throw e;
            }
            return parse(jwsString, reloaded);
        }
    }

    private static Jws<Claims> parse(String jwsString, PublicKey publicKey) {
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

    private static boolean isPublicKeyUsable(boolean forceReload, long now) {
        if (cachedPublicKey == null) {
            return false;
        }
        return forceReload
                ? now - lastForcedReloadAt < PUBLIC_KEY_MIN_RELOAD_MS
                : now - publicKeyLoadedAt < PUBLIC_KEY_TTL_MS;
    }

    private static PublicKey getPublicKey(boolean forceReload) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        long now = System.currentTimeMillis();
        if (isPublicKeyUsable(forceReload, now)) {
            return cachedPublicKey;
        }

        synchronized (publicKeyLock) {
            // Another thread may have refreshed (or tried to) while this one waited for the lock
            if (isPublicKeyUsable(forceReload, now)) {
                return cachedPublicKey;
            }
            if (forceReload) {
                lastForcedReloadAt = now;
            }
            PublicKey previous = cachedPublicKey;
            try {
                cachedPublicKey = publicKeyLoader.load();
            } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException | RuntimeException e) {
                if (previous == null) {
                    throw e;
                }
                // Keep authenticating with the last good key and retry after the TTL instead of failing every request
                loggerMaker.errorAndAddToDb(e, "Failed to refresh JWT public key, using cached key");
            }
            publicKeyLoadedAt = now;
            return cachedPublicKey;
        }
    }

    private static PublicKey loadPublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        
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
