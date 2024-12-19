package com.akto.threat.backend.interceptors;

import com.akto.dao.ConfigsDao;
import com.akto.dto.Config;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class AuthenticationInterceptor implements Handler<RoutingContext> {

  private static PublicKey getPublicKey()
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

  @Override
  public void handle(RoutingContext context) {
    String token = context.request().getHeader("Authorization");
    if (token == null || !token.startsWith("Bearer ")) {
      context.response().setStatusCode(401).end("Missing or Invalid Authorization header");
      return;
    }

    token = token.substring(7);

    try {
      PublicKey publicKey = getPublicKey();
      Jws<Claims> claims =
          Jwts.parserBuilder().setSigningKey(publicKey).build().parseClaimsJws(token);
      int accountId = (int) claims.getBody().get("accountId");
      context.put("accountId", accountId + "");
      context.next();
    } catch (Exception e) {
      context.response().setStatusCode(401).end("Missing or Invalid Authorization header");
    }
  }
}
