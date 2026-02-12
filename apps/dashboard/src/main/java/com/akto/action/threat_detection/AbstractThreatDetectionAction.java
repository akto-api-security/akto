package com.akto.action.threat_detection;

import com.akto.ProtoMessageUtils;
import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.database_abstractor_authenticator.JwtAuthenticator;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsResponse;
import com.akto.util.http_util.CoreHTTPClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AbstractThreatDetectionAction extends UserAction {

  private Map<Integer, String> tokens = new HashMap<>();
  private String backendUrl;
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder().build();

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

  /**
   * Fetch malicious events from threat-backend service
   * @param startTimestamp Start timestamp for time range filter (0 or negative to skip)
   * @param endTimestamp End timestamp for time range filter (0 or negative to skip)
   * @param limit Maximum number of events to fetch
   * @param additionalFilters Optional additional filters to add to the request (can be null or empty)
   * @return List of DashboardMaliciousEvent objects
   */
  protected List<DashboardMaliciousEvent> fetchAllMaliciousEvents(
      int startTimestamp, 
      int endTimestamp, 
      int limit,
      Map<String, Object> additionalFilters) {
    final List<DashboardMaliciousEvent> result = new ArrayList<>();
    try {
      String url = String.format("%s/api/dashboard/list_malicious_requests", this.getBackendUrl());
      MediaType JSON = MediaType.parse("application/json; charset=utf-8");

      Map<String, Object> filter = new HashMap<>();
      
      // Time range filter
      Map<String, Integer> time_range = new HashMap<>();
      if (startTimestamp > 0) {
        time_range.put("start", startTimestamp);
      }
      if (endTimestamp > 0) {
        time_range.put("end", endTimestamp);
      }
      // Always put time_range (even if empty) to match existing pattern
      filter.put("detected_at_time_range", time_range);
      
      // Add any additional filters
      if (additionalFilters != null && !additionalFilters.isEmpty()) {
        filter.putAll(additionalFilters);
      }

      Map<String, Object> body = new HashMap<String, Object>() {
        {
          put("skip", 0);
          put("limit", limit);
          put("sort", new HashMap<String, Integer>() {{ put("detectedAt", -1); }});
          put("filter", filter);
        }
      };

      String msg = objectMapper.valueToTree(body).toString();
      String contextSourceValue = Context.contextSource.get() != null ? Context.contextSource.get().toString() : "";
      
      RequestBody requestBody = RequestBody.create(msg, JSON);
      Request request = new Request.Builder()
          .url(url)
          .post(requestBody)
          .addHeader("Authorization", "Bearer " + this.getApiToken())
          .addHeader("Content-Type", "application/json")
          .addHeader("x-context-source", contextSourceValue)
          .build();

      try (Response resp = httpClient.newCall(request).execute()) {
        String responseBody = resp.body() != null ? resp.body().string() : "";

        ProtoMessageUtils.<ListMaliciousRequestsResponse>toProtoMessage(
            ListMaliciousRequestsResponse.class, responseBody
        ).ifPresent(m -> {
          result.addAll(m.getMaliciousEventsList().stream()
              .map(smr -> new DashboardMaliciousEvent(
                  smr.getId(),
                  smr.getActor(),
                  smr.getFilterId(),
                  smr.getEndpoint(),
                  com.akto.dto.type.URLMethods.Method.fromString(smr.getMethod()),
                  smr.getApiCollectionId(),
                  smr.getIp(),
                  smr.getCountry(),
                  smr.getDetectedAt(),
                  smr.getType(),
                  smr.getRefId(),
                  smr.getCategory(),
                  smr.getSubCategory(),
                  smr.getEventTypeVal(),
                  smr.getPayload(),
                  smr.getMetadata(),
                  smr.getSuccessfulExploit(),
                  smr.getStatus(),
                  smr.getLabel(),
                  smr.getHost(),
                  smr.getJiraTicketUrl(),
                  smr.getSeverity(),
                  smr.getSessionId() != null && !smr.getSessionId().isEmpty() ? smr.getSessionId() : ""
              ))
              .collect(Collectors.toList())
          );
        });
      }
    } catch (Exception e) {
      // Error handling is left to the caller - return empty list on error
    }
    return result;
  }
}
