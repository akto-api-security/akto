package com.akto.utility;

import com.akto.data_actor.DataActor;
import com.akto.dto.OriginalHttpResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Handles POST /utility/{apiName} for the red-team learning APIs (DataActor.REDTEAM_LEARNING_APIS), so red teaming
 * (test-editor-services) reads and writes what it learned about a target agent without its own database-abstractor
 * URL or token. The JSON body is checked to be an object and relayed untouched; the abstractor validates it. The
 * abstractor's status and body are returned as they came (422 with actionErrors on a refused request), or 502 when it
 * could not be reached.
 */
public class RedteamLearningsHandler implements HttpHandler {

    /** Well above a full batch of observations or a version with its drafts. */
    static final int MAX_BODY_BYTES = 2 * 1024 * 1024;

    private final DataActor dataActor;
    private final String apiName;

    public RedteamLearningsHandler(DataActor dataActor, String apiName) {
        if (!DataActor.REDTEAM_LEARNING_APIS.contains(apiName)) {
            throw new IllegalArgumentException("not a red-team learning API: " + apiName);
        }
        this.dataActor = dataActor;
        this.apiName = apiName;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!HttpUtil.requirePost(exchange)) return;
        String rawBody = HttpUtil.requireRequestBody(exchange);
        if (rawBody == null) return;
        if (rawBody.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
            HttpUtil.sendError(exchange, 413, "Request body is larger than " + MAX_BODY_BYTES + " bytes");
            return;
        }
        if (HttpUtil.parseJsonBody(rawBody, exchange) == null) return;

        OriginalHttpResponse response = dataActor.relayRedteamLearnings(apiName, rawBody);
        if (response == null || response.getBody() == null) {
            HttpUtil.sendError(exchange, 502, "Failed to reach the database-abstractor for " + apiName);
            return;
        }
        HttpUtil.sendRawJson(exchange, response.getStatusCode(), response.getBody());
    }
}
