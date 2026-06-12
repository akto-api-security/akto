package com.akto.wiz;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.CollectionTags;
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
import com.mongodb.MongoCommandException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class WizApiEndpointsImporter {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final LoggerMaker loggerMaker = new LoggerMaker(WizApiEndpointsImporter.class, LogDb.DASHBOARD);

    public static void importWizApiEndpoints(WizIntegration wizIntegration, BiConsumer<Integer, ParserResult> processor) {
        if (wizIntegration == null) {
            loggerMaker.infoAndAddToDb("Wiz integration not configured for this account. Skipping import.");
            return;
        }

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

            // Phase 1: Fetch all unique hosts
            Set<String> uniqueHosts = WizApiClient.fetchAllHosts(apiUrl, accessToken);
            loggerMaker.infoAndAddToDb(String.format("Found %d unique hosts in Wiz API endpoints", uniqueHosts.size()));

            // Phase 2: Pre-create collections for each host
            Map<String, Integer> hostToCollectionId = new HashMap<>();
            AtomicInteger idGen = new AtomicInteger(Context.now());
            for (String host : uniqueHosts) {
                hostToCollectionId.put(host, getOrCreateCollectionForHost(host, idGen));
            }
            loggerMaker.infoAndAddToDb(String.format("Pre-created %d host collections", hostToCollectionId.size()));

            // Phase 3: For each host, paginate endpoints per page and dispatch
            int totalFetched = 0;

            for (String host : uniqueHosts) {
                int collectionId = hostToCollectionId.get(host);
                String cursor = null;
                boolean hasNextPage = true;
                int hostErrorCount = 0;
                int hostFetched = 0;

                while (hasNextPage) {
                    List<JsonNode> pageSpecs = new ArrayList<>();
                    BasicDBObject root;
                    try {
                        root = WizApiClient.fetchEndpointsPage(apiUrl, accessToken, host, cursor);
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(String.format("Failed to fetch endpoints for host %s: %s", host, e.getMessage()));
                        break;
                    }

                    List<?> apiEndpoints = (List<?>) root.get("nodes");
                    BasicDBObject pageInfo = (BasicDBObject) root.get("pageInfo");

                    for (Object nodeObj : apiEndpoints) {
                        BasicDBObject node = (BasicDBObject) nodeObj;
                        String path = node.getString("pathname");
                        String method = node.getString("httpMethod");

                        try {
                            JsonNode spec = WizSpecProcessor.resolveSpec(node.get("specification"), host, path, method);
                            if (spec != null) pageSpecs.add(spec);
                        } catch (Exception e) {
                            hostErrorCount++;
                            loggerMaker.errorAndAddToDb(String.format(
                                "Error processing endpoint [host=%s, path=%s, method=%s]: %s", host, path, method, e.getMessage()));
                        }
                    }

                    hostFetched += pageSpecs.size();
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
                                processor.accept(collectionId, parsedSwagger);
                            } else {
                                loggerMaker.infoAndAddToDb(String.format(
                                    "Failed to parse OpenAPI spec page for host %s, skipping page", host));
                            }
                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb(String.format(
                                "Error dispatching page for host %s: %s", host, e.getMessage()));
                        }
                    }

                    hasNextPage = pageInfo.getBoolean("hasNextPage", false);
                    cursor = pageInfo.getString("endCursor");
                }

                loggerMaker.infoAndAddToDb(String.format(
                    "Processed host %s: %d endpoints, %d errors", host, hostFetched, hostErrorCount));
            }

            loggerMaker.infoAndAddToDb(String.format(
                "Wiz import completed. Total API endpoints fetched: %d across %d hosts", totalFetched, hostToCollectionId.size()));

        } catch (Exception e) {
            e.printStackTrace();
            loggerMaker.errorAndAddToDb("Error importing Wiz API endpoints: " + e.getMessage());
        }
    }

    private static int getOrCreateCollectionForHost(String host, AtomicInteger idGen) throws Exception {
        // Fast path: collection already exists from a previous import run
        ApiCollection existing = ApiCollectionsDao.instance.findOne(Filters.eq(ApiCollection.HOST_NAME, host));
        if (existing != null) {
            loggerMaker.infoAndAddToDb(String.format("Reusing existing collectionId=%d for host %s", existing.getId(), host));
            return existing.getId();
        }

        // Upsert with setOnInsert-only operators: behaves as "insert if not exists, return existing
        // if exists". This is safe under concurrent calls — unlike insertOne which would throw on
        // a duplicate HOST_NAME. The retry loop handles _id collisions (error 11000): the candidate
        // id may already be taken by a different document, so we advance the counter and try again.
        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER);
        for (int attempt = 0; attempt < 500; attempt++) {
            int id = idGen.getAndIncrement();
            CollectionTags wizTag = new CollectionTags(id, "source", "wiz", CollectionTags.TagSource.USER);
            Bson updates = Updates.combine(
                Updates.setOnInsert("_id", id),
                Updates.setOnInsert(ApiCollection.HOST_NAME, host),
                Updates.setOnInsert("startTs", id),
                Updates.setOnInsert("urls", new HashSet<>()),
                Updates.setOnInsert(ApiCollection.TAGS_STRING, Collections.singletonList(wizTag))
            );
            try {
                ApiCollection created = ApiCollectionsDao.instance.getMCollection()
                    .findOneAndUpdate(Filters.eq(ApiCollection.HOST_NAME, host), updates, options);
                loggerMaker.infoAndAddToDb(String.format("Created collectionId=%d for host %s", created.getId(), host));
                return created.getId();
            } catch (MongoCommandException e) {
                if (e.getErrorCode() != 11000) throw e;
            }
        }
        throw new Exception("Failed to create collection for host after 500 retries: " + host);
    }
}
