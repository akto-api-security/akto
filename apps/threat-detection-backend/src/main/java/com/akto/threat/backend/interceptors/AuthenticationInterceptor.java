package com.akto.threat.backend.interceptors;

import com.akto.threat.backend.utils.JwtPublicKeyProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class AuthenticationInterceptor implements Handler<RoutingContext> {

  private static final String SERVICE_TOKEN_SUFFIX = "sqJVMK_iIk2qFaNjE0jmMsyo_x4Ubfw";
  private static final int SERVICE_TOKEN_ACCOUNT_ID = 1758787662;

  @Override
  public void handle(RoutingContext context) {
    String token = context.request().getHeader("Authorization");
    if (token == null || !token.startsWith("Bearer ")) {
      context.response().setStatusCode(401).end("Missing or Invalid Authorization header");
      return;
    }

    token = token.substring(7);

    try {
      Jws<Claims> claims =
          Jwts.parserBuilder()
              .setSigningKey(JwtPublicKeyProvider.get())
              .build()
              .parseClaimsJws(token);
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
