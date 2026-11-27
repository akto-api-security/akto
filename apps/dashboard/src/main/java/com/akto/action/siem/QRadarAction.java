package com.akto.action.siem;

import com.akto.action.threat_detection.AbstractThreatDetectionAction;
import com.akto.util.http_util.CoreHTTPClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** QRadar settings live in the threat backend, which does the forwarding; these actions proxy to it. */
public class QRadarAction extends AbstractThreatDetectionAction {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder().build();

    @Setter private String url;
    @Setter private String authToken;
    @Setter private boolean enabled;
    @Getter private Map<String, Object> qradarIntegration;

    public String fetchQRadarIntegration() {
        qradarIntegration = callBackend("fetchQRadarIntegration", new HashMap<>());
        return qradarIntegration == null ? ERROR.toUpperCase() : SUCCESS.toUpperCase();
    }

    public String saveQRadarIntegration() {
        Map<String, Object> body = new HashMap<>();
        body.put("url", url);
        body.put("authToken", authToken);
        body.put("enabled", enabled);
        return callBackend("saveQRadarIntegration", body) == null ? ERROR.toUpperCase() : SUCCESS.toUpperCase();
    }

    public String testQRadarIntegration() {
        Map<String, Object> body = new HashMap<>();
        body.put("url", url);
        body.put("authToken", authToken);
        Map<String, Object> resp = callBackend("testQRadarIntegration", body);
        if (resp == null) return ERROR.toUpperCase();
        if (resp.get("error") != null) {
            addActionError(String.valueOf(resp.get("error")));
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    /** Returns the backend's JSON response, or null after adding an action error. */
    private Map<String, Object> callBackend(String path, Map<String, Object> body) {
        try {
            Request request = new Request.Builder()
                .url(String.format("%s/api/dashboard/%s", getBackendUrl(), path))
                .post(RequestBody.create(objectMapper.writeValueAsString(body), MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + getApiToken())
                .build();
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                Map<String, Object> json = responseBody.isEmpty()
                    ? new HashMap<>()
                    : objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
                if (!response.isSuccessful()) {
                    addActionError(String.valueOf(json.getOrDefault("errorMessage", "Threat backend responded " + response.code())));
                    return null;
                }
                return json;
            }
        } catch (Exception e) {
            addActionError("Could not reach threat backend: " + e.getMessage());
            return null;
        }
    }
}
