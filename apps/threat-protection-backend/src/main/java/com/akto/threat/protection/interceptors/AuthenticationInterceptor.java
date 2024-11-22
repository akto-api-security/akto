package com.akto.threat.protection.interceptors;

import com.akto.dao.ConfigsDao;
import com.akto.dto.Config;
import io.grpc.*;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class AuthenticationInterceptor implements ServerInterceptor {
  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata metadata, ServerCallHandler<ReqT, RespT> next) {
    String value = metadata.get(Constants.AUTHORIZATION_METADATA_KEY);

    if (value == null) {
      call.close(
          Status.UNAUTHENTICATED.withDescription("Authorization token is required"), metadata);
      return null;
    }

    try {
      PublicKey publicKey = getPublicKey();
      Jws<Claims> claims =
          Jwts.parserBuilder().setSigningKey(publicKey).build().parseClaimsJws(value);
      int accountId = (int) claims.getBody().get("accountId");
      Context ctx = Context.current().withValue(Constants.ACCOUNT_ID_CONTEXT_KEY, accountId);
      return Contexts.interceptCall(ctx, call, metadata, next);
    } catch (Exception e) {
      e.printStackTrace();
      call.close(Status.UNAUTHENTICATED.withDescription("Invalid token"), metadata);
    }

    return null;
  }

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
}
