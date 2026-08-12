package com.akto.action;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.log.LoggerMaker;
import com.akto.testing.ApiExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.interceptor.ServletRequestAware;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Allowlisted reverse proxy from data-ingestion-service to the database-abstractor service.
 * Lets callers that must not reach database-abstractor directly (e.g. CLI hooks sending
 * heartbeats) go through data-ingestion-service, which alone has network access to it.
 * The exposed path is identical to the abstractor's own path (see struts.xml) so client
 * config changes are limited to which base URL/env var they point at.
 *
 * The request body is read raw (no "json" interceptor) and forwarded verbatim — callers
 * post their payload at the top level (e.g. {@code {"moduleInfo": {...}}}), not nested
 * under a named field, so binding it onto a typed action property would drop it.
 *
 * To proxy another database-abstractor endpoint, add its name to ALLOWED_PATHS here and
 * register one more literal &lt;action&gt; for it in struts.xml (same class, different
 * "subpath" param) — no new Action class needed.
 */
@lombok.Getter
@lombok.Setter
public class AbstractorProxyAction extends ActionSupport implements ServletRequestAware {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AbstractorProxyAction.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final Set<String> ALLOWED_PATHS = new HashSet<>(Arrays.asList(
            "updateModuleInfoForHeartbeat",
            "updateModuleInfoForHeartbeatV2"
    ));

    private static final String ABSTRACTOR_URL = buildAbstractorUrl();
    private static final String ABSTRACTOR_TOKEN = System.getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN");

    private String subpath;
    private HttpServletRequest servletRequest;

    private Map<String, Object> data;
    private boolean success;
    private String message;

    private static String buildAbstractorUrl() {
        String base = System.getenv("DATABASE_ABSTRACTOR_SERVICE_URL");
        if (base == null || base.trim().isEmpty()) {
            return null;
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public String proxy() {
        String normalizedPath = subpath == null ? "" : subpath.replaceAll("^/+", "").replaceAll("/+$", "");

        if (!ALLOWED_PATHS.contains(normalizedPath)) {
            loggerMaker.warnAndAddToDb("Rejected abstractor proxy request for disallowed path: " + normalizedPath);
            success = false;
            message = "Path not allowed";
            return "FORBIDDEN";
        }

        if (ABSTRACTOR_URL == null) {
            loggerMaker.errorAndAddToDb("DATABASE_ABSTRACTOR_SERVICE_URL not configured; cannot proxy " + normalizedPath);
            success = false;
            message = "Abstractor URL not configured";
            return Action.ERROR.toUpperCase();
        }

        try {
            Map<String, List<String>> headers = new HashMap<>();
            headers.put("Content-Type", Collections.singletonList("application/json"));
            if (ABSTRACTOR_TOKEN != null && !ABSTRACTOR_TOKEN.isEmpty()) {
                headers.put("Authorization", Collections.singletonList(ABSTRACTOR_TOKEN));
            }

            String payload = readRawBody();
            OriginalHttpRequest request = new OriginalHttpRequest(
                    ABSTRACTOR_URL + "/api/" + normalizedPath, "", "POST", payload, headers, "");

            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            data = (responsePayload != null && !responsePayload.isEmpty())
                    ? objectMapper.readValue(responsePayload, Map.class)
                    : new HashMap<>();

            int statusCode = response.getStatusCode();
            success = statusCode >= 200 && statusCode < 300;
            if (success) {
                loggerMaker.info("Proxied {} to abstractor - status: {}", normalizedPath, statusCode);
            } else {
                message = "Abstractor returned status " + statusCode;
                loggerMaker.errorAndAddToDb("Non-2xx response proxying to abstractor path " + normalizedPath + ": " + statusCode);
            }
            return success ? Action.SUCCESS.toUpperCase() : Action.ERROR.toUpperCase();

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error proxying to abstractor path " + normalizedPath + ": " + e.getMessage());
            success = false;
            message = "Unexpected error: " + e.getMessage();
            return Action.ERROR.toUpperCase();
        }
    }

    private String readRawBody() throws Exception {
        try (InputStream is = servletRequest.getInputStream()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = is.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8.name());
        }
    }
}
