package com.akto.wiz;

import com.akto.dao.WizIntegrationDao;
import com.akto.dto.wiz_integration.WizIntegration;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.upload.SwaggerUploadLog;
import com.akto.open_api.parser.Parser;
import com.akto.open_api.parser.ParserResult;
import com.akto.testing.HostDNSLookup;
import com.akto.util.http_util.CoreHTTPClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class WizApiEndpointsImporter {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final LoggerMaker loggerMaker = new LoggerMaker(WizApiEndpointsImporter.class, LogDb.DASHBOARD);

    private static final ExecutorService dnsCheckPool = Executors.newFixedThreadPool(5);

    private static final OkHttpClient reachabilityClient = CoreHTTPClient.client.newBuilder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build();

    public static boolean checkApiEndpointsScope() {
        try {
            WizIntegration wizIntegration = WizIntegrationDao.instance.findOne(new BasicDBObject());
            if (wizIntegration == null) return false;
            String accessToken = WizIntegrationUtils.getValidAccessToken();
            String apiUrl = String.format(WizIntegration.API_BASE_URL_PATTERN, wizIntegration.getTenantDataCenter(), WizIntegration.ENVIRONMENT);
            if (WizIntegrationUtils.isWizDevMode()) {
                apiUrl = WizIntegrationUtils.getWizDevGraphQLEndpoint();
            }

            String graphqlQuery = "{\"query\":\"{ apiEndpoints(first: 1) { nodes { id } } }\"}";
            BasicDBObject response = WizApiClient.executeRaw(apiUrl, graphqlQuery, WizApiClient.buildHeaders(accessToken));

            List<?> errors = (List<?>) response.get("errors");
            if (errors != null && !errors.isEmpty()) {
                BasicDBObject firstError = (BasicDBObject) errors.get(0);
                BasicDBObject extensions = (BasicDBObject) firstError.get("extensions");
                if (extensions != null && "UNAUTHORIZED".equals(extensions.getString("code"))) {
                    loggerMaker.infoAndAddToDb("Wiz credentials are missing read:api_endpoints scope");
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Failed to check Wiz api_endpoints scope: " + e.getMessage());
            return false;
        }
    }

    /**
     * Builds the Wiz tags JSON string for an Akto message. Always includes source=wiz,
     * plus whatever additional tags the caller has already resolved for this message.
     */
    private static String buildWizTagsJson(Map<String, String> extraTags) {
        BasicDBObject tagsMap = new BasicDBObject();
        tagsMap.put("source", "wiz");
        if (extraTags != null) {
            for (Map.Entry<String, String> entry : extraTags.entrySet()) {
                if (entry.getValue() != null) {
                    tagsMap.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return tagsMap.toJson();
    }

    public static int importWizApiEndpoints(WizIntegration wizIntegration, BiConsumer<ParserResult, WizImportJobPageContext> processor, Runnable heartbeat) {
        if (wizIntegration == null) {
            loggerMaker.infoAndAddToDb("Wiz integration not configured for this account. Skipping import.");
            return 0; 
        }

        int totalFetched = 0;

        try {
            String accessToken = WizIntegrationUtils.getValidAccessToken();

            String apiUrl = String.format(
                WizIntegration.API_BASE_URL_PATTERN,
                wizIntegration.getTenantDataCenter(),
                WizIntegration.ENVIRONMENT
            );

            if (WizIntegrationUtils.isWizDevMode()) {
                apiUrl = WizIntegrationUtils.getWizDevGraphQLEndpoint();
                loggerMaker.infoAndAddToDb("Wiz dev mode enabled. Using dev GraphQL endpoint: " + apiUrl);
            }

            List<BasicDBObject> allEndpointMeta = WizApiClient.fetchAllEndpointMeta(apiUrl, accessToken);
            loggerMaker.infoAndAddToDb(String.format("Fetched metadata for %d total endpoints from Wiz", allEndpointMeta.size()));
            heartbeat.run();

            int deltaTs = wizIntegration.getWizImportApiEndpointsJobDeltaTs();
            Instant cutoff = Instant.ofEpochSecond(deltaTs);
            Set<String> updatedEndpointIds = new HashSet<>();
            Map<String, Boolean> hostToPubliclyAccessible = new HashMap<>();

            for (BasicDBObject meta : allEndpointMeta) {
                String updatedAt = meta.getString("updatedAt");
                if (updatedAt == null) continue;
                try {
                    String id = meta.getString("id");
                    if (id != null && Instant.parse(updatedAt).isAfter(cutoff)) {
                        updatedEndpointIds.add(id);

                        // Strip the port from the host if present, to ensure we only check the hostname for public accessibility
                        String host = meta.getString("host");
                        if (host != null) {
                            try {
                                String parsedHost = new URI("//" + host).getHost();
                                if (parsedHost != null) {
                                    hostToPubliclyAccessible.putIfAbsent(parsedHost, null);
                                }
                            } catch (Exception e) {
                                loggerMaker.errorAndAddToDb(String.format(
                                    "Failed to parse host '%s' for endpoint %s", host, id));
                            }
                        }
                    }
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(String.format(
                        "Failed to parse updatedAt '%s' for endpoint %s", updatedAt, meta.getString("id")));
                }
            }

            loggerMaker.infoAndAddToDb(String.format("Starting reachability check for %d unique hosts", hostToPubliclyAccessible.size()));
            
            Map<String, Future<Boolean>> hostResolutions = new HashMap<>();
            for (String host : hostToPubliclyAccessible.keySet()) {
                hostResolutions.put(host, dnsCheckPool.submit(() -> {
                    try {
                        if (!HostDNSLookup.isRequestValid(host)) {
                            loggerMaker.infoAndAddToDb(String.format("%s: invalid host. Address resolves to private IP or localhost", host));
                            return false;
                        }
                        Request request = new Request.Builder().url("https://" + host + "/").get().build();
                        try (Response response = reachabilityClient.newCall(request).execute()) {
                            return true;
                        }
                    } catch (SSLPeerUnverifiedException | SSLHandshakeException e) {
                        return true;
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(String.format("%s: unreachable (%s)", host, e.getClass().getSimpleName()));
                        return false;
                    }
                }));
            }

            int publicHostCount = 0;
            int unresolvedHostCount = 0;

            for (Map.Entry<String, Future<Boolean>> entry : hostResolutions.entrySet()) {
                String host = entry.getKey();
                boolean accessible = false;
                try {
                    accessible = entry.getValue().get(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    // timed out, or the check itself failed; treated as unresolved
                }

                hostToPubliclyAccessible.put(host, accessible);
                if (accessible) {
                    publicHostCount++;
                } else {
                    unresolvedHostCount++;
                }
            }

            loggerMaker.infoAndAddToDb(String.format(
                "DNS check pool completed: %d public, %d unresolved (of %d hosts checked)",
                publicHostCount, unresolvedHostCount, hostResolutions.size()));

            Map<String, String> canonicalKeyToQaHost = WizApiGatewayFilter.buildCanonicalKeyToQaHost(allEndpointMeta);

            String sinceStr = deltaTs == 0 ? "first run" : ((Instant.now().getEpochSecond() - deltaTs) / 3600) + " hrs ago";
            loggerMaker.infoAndAddToDb(String.format("%d endpoints updated since last sync (%s, out of %d total)", updatedEndpointIds.size(), sinceStr, allEndpointMeta.size()));

            totalFetched = 0;
            int totalErrors = 0;

            if (!updatedEndpointIds.isEmpty()) {
                List<String> idList = new ArrayList<>(updatedEndpointIds);
                int batchSize = WizApiClient.ENDPOINT_FETCH_PAGE_SIZE;
                int totalBatches = (idList.size() + batchSize - 1) / batchSize;

                for (int i = 0; i < idList.size(); i += batchSize) {
                    List<String> batch = idList.subList(i, Math.min(i + batchSize, idList.size()));
                    boolean isFirstBatch = (i == 0);
                    boolean isLastBatch = (i + batchSize) >= idList.size();
                    int currentBatch = (i / batchSize) + 1;

                    List<JsonNode> pageSpecs = new ArrayList<>();
                    Map<String, String> hostToGatewayName = new HashMap<>();
                    BasicDBObject root;
                    try {
                        root = WizApiClient.fetchEndpointsPageByIds(apiUrl, accessToken, batch);
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(String.format("Failed to fetch batch [%d-%d]: %s", i, i + batch.size(), e.getMessage()));
                        continue;
                    }

                    List<?> nodes = (List<?>) root.get("nodes");
                    if (nodes == null) continue;

                    for (Object nodeObj : nodes) {
                        BasicDBObject node = (BasicDBObject) nodeObj;
                        String host = node.getString("host");
                        String path = node.getString("pathname");
                        String method = node.getString("httpMethod");
                        List<?> relatedResources = (List<?>) node.get("relatedResources");
                        List<?> authSchemes = (List<?>) node.get("authSchemes");
                        try {
                            JsonNode spec = WizSpecProcessor.resolveSpec(node.get("specification"), host, path, method);
                            if (spec != null) {
                                spec = WizSpecProcessor.injectMissingAuthScheme(spec, authSchemes);
                                String qaHost = WizApiGatewayFilter.resolveQaHost(host, relatedResources, canonicalKeyToQaHost);
                                if (qaHost != null) {
                                    spec = WizSpecProcessor.replaceSpecHost(spec, qaHost);
                                }
                                String effectiveHost = qaHost != null ? qaHost : host;
                                String gatewayName = WizApiGatewayFilter.extractGatewayName(relatedResources);
                                if (gatewayName != null) {
                                    hostToGatewayName.putIfAbsent(effectiveHost, gatewayName);
                                }
                                pageSpecs.add(spec);
                            }
                        } catch (Exception e) {
                            totalErrors++;
                            loggerMaker.errorAndAddToDb(String.format(
                                "Error processing endpoint [host=%s, path=%s, method=%s]: %s", host, path, method, e.getMessage()));
                        }
                    }

                    totalFetched += pageSpecs.size();

                    if (!pageSpecs.isEmpty()) {
                        try {
                            String pageOpenAPISpec = objectMapper.writeValueAsString(WizSpecProcessor.buildMergedSpec(pageSpecs));
                            ParseOptions options = new ParseOptions();
                            options.setResolve(true);
                            OpenAPI openAPI = new OpenAPIParser().readContents(pageOpenAPISpec, null, options).getOpenAPI();

                            if (openAPI != null) {
                                ParserResult parsedSwagger = Parser.convertOpenApiToAkto(openAPI, "", true, new ArrayList<>(), true);
                                for (SwaggerUploadLog log : parsedSwagger.getUploadLogs()) {
                                    log.setUploadId("fileUploadId");
                                    String aktoFormat = log.getAktoFormat();
                                    if (aktoFormat != null) {
                                        BasicDBObject msg = BasicDBObject.parse(aktoFormat);
                                        msg.put("source", HttpResponseParams.Source.WIZ.name());

                                        Map<String, String> tags = new HashMap<>();
                                        
                                        String requestHeadersStr = msg.getString("requestHeaders");
                                        String host = null;
                                        if (requestHeadersStr != null) {
                                            try {
                                                BasicDBObject requestHeaders = BasicDBObject.parse(requestHeadersStr);
                                                for (String key : requestHeaders.keySet()) {
                                                    if ("host".equalsIgnoreCase(key)) {
                                                        host = requestHeaders.getString(key);
                                                        break;
                                                    }
                                                }
                                            } catch (Exception ignored) {
                                            }
                                        }

                                        Boolean publiclyAccessible = hostToPubliclyAccessible.getOrDefault(host, false);
                                        if (publiclyAccessible) {
                                            tags.put("public", "true");
                                        }

                                        String gatewayName = hostToGatewayName.get(host);
                                        if (gatewayName != null) {
                                            tags.put("api-gateway", gatewayName);
                                        }
                                        msg.put("tags", buildWizTagsJson(tags));

                                        log.setAktoFormat(msg.toJson());
                                    }
                                }
                                processor.accept(parsedSwagger, new WizImportJobPageContext(isFirstBatch, isLastBatch));
                            } else {
                                loggerMaker.infoAndAddToDb(String.format("Failed to parse OpenAPI spec for batch [%d-%d], skipping", i, i + batch.size()));
                            }
                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb(String.format("Error dispatching batch [%d-%d]: %s", i, i + batch.size(), e.getMessage()));
                        }
                    }

                    loggerMaker.infoAndAddToDb(String.format("Completed batch %d/%d (%d remaining)", currentBatch, totalBatches, totalBatches - currentBatch));
                    heartbeat.run();
                }
            }
            loggerMaker.infoAndAddToDb(String.format(
                "Wiz import completed. Total endpoints dispatched: %d, errors: %d", totalFetched, totalErrors));

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error importing Wiz API endpoints: " + e.getMessage());
        }

        return totalFetched;
    }
}
