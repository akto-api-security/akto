package com.akto.wiz;

import com.akto.dao.WizIntegrationDao;
import com.akto.dto.wiz_integration.WizIntegration;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.upload.SwaggerUploadLog;
import com.akto.open_api.parser.Parser;
import com.akto.open_api.parser.ParserResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

public class WizApiEndpointsImporter {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final LoggerMaker loggerMaker = new LoggerMaker(WizApiEndpointsImporter.class, LogDb.DASHBOARD);

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
            for (BasicDBObject meta : allEndpointMeta) {
                String updatedAt = meta.getString("updatedAt");
                if (updatedAt == null) continue;
                try {
                    String id = meta.getString("id");
                    if (id != null && Instant.parse(updatedAt).isAfter(cutoff)) {
                        updatedEndpointIds.add(id);
                    }
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(String.format(
                        "Failed to parse updatedAt '%s' for endpoint %s", updatedAt, meta.getString("id")));
                }
            }
            long cutoffEpochSeconds = deltaTs;
            long secondsSinceCutoff = Instant.now().getEpochSecond() - cutoffEpochSeconds;
            long hoursSinceCutoff = secondsSinceCutoff / 3600;
            loggerMaker.infoAndAddToDb(String.format("%d endpoints updated since last sync (%d hrs ago, out of %d total)", updatedEndpointIds.size(), hoursSinceCutoff, allEndpointMeta.size()));
            
                
            totalFetched = 0;
            int totalErrors = 0;

            if (!updatedEndpointIds.isEmpty()) {
                List<String> idList = new ArrayList<>(updatedEndpointIds);
                int batchSize = WizApiClient.ENDPOINT_FETCH_PAGE_SIZE;

                for (int i = 0; i < idList.size(); i += batchSize) {
                    List<String> batch = idList.subList(i, Math.min(i + batchSize, idList.size()));
                    boolean isFirstBatch = (i == 0);
                    boolean isLastBatch = (i + batchSize) >= idList.size();

                    List<JsonNode> pageSpecs = new ArrayList<>();
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
                        try {
                            JsonNode spec = WizSpecProcessor.resolveSpec(node.get("specification"), host, path, method);
                            if (spec != null) pageSpecs.add(spec);
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
