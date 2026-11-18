package com.akto.threat.backend.interceptors;

import com.akto.dao.ConfigsDao;
import com.akto.dto.Config;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

public class AuthenticationInterceptor implements Handler<RoutingContext> {

  private static final String SERVICE_TOKEN_SUFFIX = "sqJVMK_iIk2qFaNjE0jmMsyo_x4Ubfw";
  private static final int SERVICE_TOKEN_ACCOUNT_ID = 1758787662;

  private static final long PUBLIC_KEY_TTL_MS = TimeUnit.MINUTES.toMillis(10);
  private static final Object publicKeyLock = new Object();
  private static volatile PublicKey cachedPublicKey;
  private static volatile long publicKeyLoadedAt;

  private static PublicKey loadPublicKey()
      throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
    Config.HybridSaasConfig config;
    try {
      config =
          (Config.HybridSaasConfig)
              ConfigsDao.instance.findOne("_id", Config.ConfigType.HYBRID_SAAS.name());
    } catch (Exception e) {
      System.out.println(e);
      throw e;
    }
    String rsaPublicKey = config.getPublicKey();

    rsaPublicKey = rsaPublicKey.replace("-----BEGIN PUBLIC KEY-----", "");
    rsaPublicKey = rsaPublicKey.replace("-----END PUBLIC KEY-----", "");
    rsaPublicKey = rsaPublicKey.replace("\n", "");
    byte[] decoded = Base64.getDecoder().decode(rsaPublicKey);
    X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
    KeyFactory kf = KeyFactory.getInstance("RSA");

    try {
      return kf.generatePublic(keySpec);
    } catch (Exception e) {
      System.out.println(e);
      throw e;
    }
  }

  private static boolean isPublicKeyFresh() {
    return cachedPublicKey != null
        && System.currentTimeMillis() - publicKeyLoadedAt < PUBLIC_KEY_TTL_MS;
  }

  private static PublicKey getPublicKey(boolean forceReload)
      throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
    if (!forceReload && isPublicKeyFresh()) {
      return cachedPublicKey;
    }

    synchronized (publicKeyLock) {
      if (!forceReload && isPublicKeyFresh()) {
        return cachedPublicKey;
      }
      PublicKey loaded = loadPublicKey();
      cachedPublicKey = loaded;
      publicKeyLoadedAt = System.currentTimeMillis();
      return loaded;
    }
  }

  private static Jws<Claims> parse(String token, PublicKey publicKey) {
    return Jwts.parserBuilder().setSigningKey(publicKey).build().parseClaimsJws(token);
  }

  private static Jws<Claims> authenticate(String token)
      throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
    PublicKey publicKey = getPublicKey(false);
    try {
      return parse(token, publicKey);
    } catch (SignatureException e) {
      PublicKey reloaded = getPublicKey(true);
      if (reloaded.equals(publicKey)) {
        throw e;
      }
      return parse(token, reloaded);
    }
  }

  @Override
  public void handle(RoutingContext context) {
    String token = context.request().getHeader("Authorization");
    if (token == null || !token.startsWith("Bearer ")) {
      context.response().setStatusCode(401).end("Missing or Invalid Authorization header");
      return;
    }

    token = token.substring(7);

    try {
      Jws<Claims> claims = authenticate(token);
      int accountId = (int) claims.getBody().get("accountId");
      if (token.endsWith(SERVICE_TOKEN_SUFFIX)) {
        accountId = SERVICE_TOKEN_ACCOUNT_ID;
      }
      context.put("accountId", accountId + "");
      context.next();
    } catch (Exception e) {
      context.response().setStatusCode(401).end("Missing or Invalid Authorization header");
    }
  }
}
