package com.akto.threat.backend.interceptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class AuthenticationInterceptorServiceTokenTest {

  @Test
  public void resolvesKnownSuffixes() {
    assertEquals(
        Integer.valueOf(1758787662),
        AuthenticationInterceptor.resolveServiceTokenAccountId(
            "eyJhbGciOi.xyz.sqJVMK_iIk2qFaNjE0jmMsyo_x4Ubfw"));
    assertEquals(
        Integer.valueOf(1794942795),
        AuthenticationInterceptor.resolveServiceTokenAccountId(
            "eyJhbGciOi.xyz.abc_GrF0CD443iJTxgqVOQ6IOcmNrwNcg"));
  }

  @Test
  public void unknownOrNullTokenNotForwarded() {
    assertNull(AuthenticationInterceptor.resolveServiceTokenAccountId(null));
    assertNull(AuthenticationInterceptor.resolveServiceTokenAccountId("eyJhbGciOi.xyz.other"));
    assertNull(
        AuthenticationInterceptor.resolveServiceTokenAccountId(
            "_GrF0CD443iJTxgqVOQ6IOcmNrwNcg-suffix"));
  }
}
