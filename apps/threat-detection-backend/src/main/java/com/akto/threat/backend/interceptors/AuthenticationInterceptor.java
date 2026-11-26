package com.akto.threat.backend.interceptors;

import com.akto.threat.backend.utils.JwtPublicKeyProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class AuthenticationInterceptor implements Handler<RoutingContext> {

  // Service tokens (matched by suffix) whose requests are forwarded to a fixed account.
  private static final Map<String, Integer> SERVICE_TOKEN_SUFFIX_TO_ACCOUNT_ID;

  static {
    Map<String, Integer> m = new LinkedHashMap<>();
    m.put("sqJVMK_iIk2qFaNjE0jmMsyo_x4Ubfw", 1758787662);
    m.put("_GrF0CD443iJTxgqVOQ6IOcmNrwNcg", 1794942795);
    SERVICE_TOKEN_SUFFIX_TO_ACCOUNT_ID = Collections.unmodifiableMap(m);
  }

  static Integer resolveServiceTokenAccountId(String token) {
    if (token == null) {
      return null;
    }
    for (Map.Entry<String, Integer> entry : SERVICE_TOKEN_SUFFIX_TO_ACCOUNT_ID.entrySet()) {
      if (token.endsWith(entry.getKey())) {
        return entry.getValue();
      }
    }
    return null;
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
      Jws<Claims> claims =
          Jwts.parserBuilder()
              .setSigningKey(JwtPublicKeyProvider.get())
              .build()
              .parseClaimsJws(token);
      int accountId = (int) claims.getBody().get("accountId");
      Integer serviceTokenAccountId = resolveServiceTokenAccountId(token);
      if (serviceTokenAccountId != null) {
        accountId = serviceTokenAccountId;
      }
      context.put("accountId", accountId + "");
      context.next();
    } catch (Exception e) {
      context.response().setStatusCode(401).end("Missing or Invalid Authorization header");
    }
  }
}
