package com.akto.jobs.executors.copilotstudio;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.akto.log.LoggerMaker;
import com.akto.util.http_util.CoreHTTPClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Resolves Copilot Studio conversation users against Microsoft Graph, caching the raw
 * directory list in a local runtime JSON file so repeated job ticks don't re-hit Graph.
 * The Go copilot-shield binary never calls Graph itself — it only receives the small
 * derived {aadObjectId: userId} map this class builds, via an env var.
 */
public class CopilotStudioUserResolver {

    private static final LoggerMaker logger = new LoggerMaker(CopilotStudioUserResolver.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();

    private static final String TOKEN_ENDPOINT_TEMPLATE = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";
    private static final String GRAPH_SCOPE = "https://graph.microsoft.com/.default";
    private static final String GRAPH_USERS_URL =
        "https://graph.microsoft.com/v1.0/users?$select=id,displayName,userPrincipalName&$top=999";
    private static final long CACHE_TTL_MILLIS = 24L * 60 * 60 * 1000;

    /** One raw Microsoft Graph user record, as cached to disk. */
    public static class GraphUser {
        public String id;
        public String displayName;
        public String userPrincipalName;
    }

    /**
     * Builds the {aadObjectId: userId} map (userId = "{sanitized UPN local-part}-{aadObjectId[:8]}")
     * and writes it to a local runtime file, returning the file PATH rather than the JSON
     * content. copilot-shield runs as a local child process of this JVM (BinaryExecutor),
     * so it can read the file directly — passing the content itself via env var breaks at
     * scale, since Linux caps a single argv/envp string at 128KB (MAX_ARG_STRLEN), which a
     * 10k-user tenant's map (~600KB) blows past by ~5x.
     */
    public static String buildUserMapFile(String tenantId, String clientId, String clientSecret) throws Exception {
        List<GraphUser> users = loadCachedUsers(tenantId);
        if (users == null) {
            String token = fetchGraphToken(tenantId, clientId, clientSecret);
            users = fetchAllUsers(token);
            cacheUsers(tenantId, users);
        }

        Map<String, String> userIdMap = new LinkedHashMap<>();
        for (GraphUser u : users) {
            if (u.id == null || u.id.isEmpty()) continue;
            userIdMap.put(u.id, buildUserId(u));
        }

        File mapFile = userMapFile(tenantId);
        Files.write(mapFile.toPath(), objectMapper.writeValueAsBytes(userIdMap));
        return mapFile.getAbsolutePath();
    }

    private static File userMapFile(String tenantId) {
        return new File(System.getProperty("java.io.tmpdir"), "copilot-studio-user-map-" + tenantId + ".json");
    }

    private static String buildUserId(GraphUser u) {
        String localPart = (u.userPrincipalName != null && u.userPrincipalName.contains("@"))
            ? u.userPrincipalName.substring(0, u.userPrincipalName.indexOf('@'))
            : (u.displayName != null ? u.displayName : "user");
        String sanitized = localPart.replaceAll("[^a-zA-Z0-9]+", "-").replaceAll("^-+|-+$", "").toLowerCase();
        if (sanitized.isEmpty()) {
            sanitized = "user";
        }
        String idPrefix = u.id.length() > 8 ? u.id.substring(0, 8) : u.id;
        return sanitized + "-" + idPrefix;
    }

    private static File cacheFile(String tenantId) {
        return new File(System.getProperty("java.io.tmpdir"), "copilot-studio-users-" + tenantId + ".json");
    }

    private static List<GraphUser> loadCachedUsers(String tenantId) {
        try {
            File f = cacheFile(tenantId);
            if (!f.exists() || System.currentTimeMillis() - f.lastModified() > CACHE_TTL_MILLIS) {
                return null;
            }
            String raw = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            return objectMapper.readValue(raw,
                objectMapper.getTypeFactory().constructCollectionType(List.class, GraphUser.class));
        } catch (Exception e) {
            logger.error("CopilotStudioUserResolver: failed to read cache: {}", e.getMessage());
            return null;
        }
    }

    private static void cacheUsers(String tenantId, List<GraphUser> users) {
        try {
            Files.write(cacheFile(tenantId).toPath(), objectMapper.writeValueAsBytes(users));
        } catch (Exception e) {
            logger.error("CopilotStudioUserResolver: failed to write cache: {}", e.getMessage());
        }
    }

    private static String fetchGraphToken(String tenantId, String clientId, String clientSecret) throws Exception {
        FormBody formBody = new FormBody.Builder()
            .add("grant_type", "client_credentials")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("scope", GRAPH_SCOPE)
            .build();
        Request request = new Request.Builder()
            .url(String.format(TOKEN_ENDPOINT_TEMPLATE, tenantId))
            .post(formBody)
            .build();
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new Exception("Failed to obtain Graph token: status=" + response.code() + " body=" + body);
            }
            JsonNode json = objectMapper.readTree(body);
            String token = json.has("access_token") ? json.get("access_token").asText() : null;
            if (token == null || token.isEmpty()) {
                throw new Exception("Graph token response missing access_token");
            }
            return token;
        }
    }

    private static List<GraphUser> fetchAllUsers(String accessToken) throws Exception {
        List<GraphUser> users = new ArrayList<>();
        String nextUrl = GRAPH_USERS_URL;

        while (nextUrl != null) {
            Request request = new Request.Builder()
                .url(nextUrl)
                .header("Authorization", "Bearer " + accessToken)
                .get()
                .build();

            try (Response response = client.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    throw new Exception("Failed to list Graph users: status=" + response.code() + " body=" + body);
                }

                JsonNode root = objectMapper.readTree(body);
                JsonNode values = root.get("value");
                
                if (values != null && values.isArray()) {
                    for (JsonNode node : values) {
                        GraphUser u = new GraphUser();
                        u.id = node.has("id") ? node.get("id").asText() : null;
                        u.displayName = (node.has("displayName") && !node.get("displayName").isNull())
                            ? node.get("displayName").asText() : null;
                        u.userPrincipalName = (node.has("userPrincipalName") && !node.get("userPrincipalName").isNull())
                            ? node.get("userPrincipalName").asText() : null;
                        users.add(u);
                    }
                }

                JsonNode nextLink = root.get("@odata.nextLink");
                nextUrl = (nextLink != null && !nextLink.isNull()) ? nextLink.asText() : null;
            }
        }

        logger.info("CopilotStudioUserResolver: fetched {} users from Graph", users.size());
        return users;
    }
}
