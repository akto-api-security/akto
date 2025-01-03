package com.akto.action.threat_detection;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.database_abstractor_authenticator.JwtAuthenticator;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class AbstractThreatDetectionAction extends UserAction {

  private Map<Integer, String> tokens = new HashMap<>();
  private String backendUrl;

  public AbstractThreatDetectionAction() {
    super();
    this.backendUrl = System.getenv().getOrDefault("THREAT_DETECTION_BACKEND_URL", "https://tbs.akto.io");
  }

  public String getApiToken() {
    try {
      int accountId = Context.accountId.get();
      if (tokens.containsKey(accountId)) {
        return tokens.get(accountId);
      }

      Map<String, Object> claims = new HashMap<>();
      claims.put("accountId", accountId);
      String token = JwtAuthenticator.createJWT(claims, "Akto", "access_tbs", Calendar.MINUTE, 1);
      tokens.put(accountId, token);

      return token;
    } catch (Exception e) {
      System.out.println(e);
      return "";
    }
  }

  public String getBackendUrl() {
    return backendUrl;
  }
}
