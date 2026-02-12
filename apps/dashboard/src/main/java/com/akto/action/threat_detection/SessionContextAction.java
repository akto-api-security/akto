package com.akto.action.threat_detection;

import com.akto.dto.agentic_sessions.SessionDocument;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensymphony.xwork2.Action;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SessionContextAction extends AbstractThreatDetectionAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(SessionContextAction.class, LogDb.DASHBOARD);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder().build();

    private String sessionId;
    private SessionDocument sessionData;
    private String errorMessage;

    public String fetchSessionContext() {
        try {
            if (sessionId == null || sessionId.isEmpty()) {
                errorMessage = "Session ID is required";
                return Action.ERROR.toUpperCase();
            }

            // Call the threat-detection-backend
            String url = String.format("%s/api/dashboard/fetch_session_context?sessionId=%s",
                this.getBackendUrl(), sessionId);

            Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer " + this.getApiToken())
                .addHeader("Content-Type", "application/json")
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful() || responseBody.isEmpty()) {
                    errorMessage = "Failed to fetch session context from backend";
                    return Action.ERROR.toUpperCase();
                }

                // Parse the response
                SessionContextResponse backendResponse = objectMapper.readValue(responseBody, SessionContextResponse.class);
                this.sessionData = backendResponse.getSessionData();

                if (this.sessionData == null) {
                    errorMessage = backendResponse.getErrorMessage() != null
                        ? backendResponse.getErrorMessage()
                        : "Session not found";
                    return Action.ERROR.toUpperCase();
                }

                return Action.SUCCESS.toUpperCase();
            }

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error fetching session context");
            errorMessage = "Failed to fetch session context: " + e.getMessage();
            return Action.ERROR.toUpperCase();
        }
    }

    // Response wrapper class for backend response
    private static class SessionContextResponse {
        private SessionDocument sessionData;
        private String errorMessage;

        public SessionDocument getSessionData() {
            return sessionData;
        }

        public void setSessionData(SessionDocument sessionData) {
            this.sessionData = sessionData;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }

    // Getters and setters
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public SessionDocument getSessionData() {
        return sessionData;
    }

    public void setSessionData(SessionDocument sessionData) {
        this.sessionData = sessionData;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
