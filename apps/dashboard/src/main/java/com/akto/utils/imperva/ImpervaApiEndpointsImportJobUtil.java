package com.akto.utils.imperva;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.imperva_integration.ImpervaIntegration;
import com.akto.dto.upload.SwaggerUploadLog;
import com.akto.imperva.ImpervaApiClient;
import com.akto.imperva.ImpervaApiClient.EndpointSummary;
import com.akto.imperva.model.ImpervaSchema;
import com.akto.imperva.parser.ImpervaSchemaParser;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.Utils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

// Imports the API inventory from Imperva API Security, run by ImpervaApiEndpointsImportJobExecutor
// when an import is triggered from the dashboard (Settings > Integrations > Imperva).
// Each Imperva host goes into a collection named after it, the same as the manual Imperva upload.
public class ImpervaApiEndpointsImportJobUtil {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ImpervaApiEndpointsImportJobUtil.class, LogDb.DASHBOARD);

    // after this many failed detail calls in a row (e.g. Imperva blocking us) use the inventory data only
    private static final int MAX_CONSECUTIVE_DETAILS_FAILURES = 100;
    // how often to send the job heartbeat and check if the import was stopped from the dashboard
    private static final int CHECK_INTERVAL_SECONDS = 15;

    public static void importApis(ImpervaIntegration integration, BooleanSupplier shouldContinue) {
        ImpervaApiClient client = new ImpervaApiClient(null, integration.getApiId(), integration.getApiKey());
        List<EndpointSummary> endpoints;
        try {
            endpoints = client.fetchAllEndpoints();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error fetching Imperva API inventory: " + e.getMessage());
            return;
        }
        Map<String, List<EndpointSummary>> endpointsByHost = endpoints.stream()
            .filter(e -> StringUtils.isNotBlank(e.getHostName()))
            .collect(Collectors.groupingBy(e -> e.getHostName().trim().toLowerCase()));
        loggerMaker.infoAndAddToDb(String.format("Starting Imperva API import: %d endpoints across %d hosts", endpoints.size(), endpointsByHost.size()));

        // collection name (lowercase) -> id, read once; names are matched case-insensitively like ApiCollectionsDao.findByName
        Map<String, Integer> collectionIds = new HashMap<>();
        for (ApiCollection apiCollection : ApiCollectionsDao.instance.findAll(new BasicDBObject(),
                Projections.include(ApiCollection.ID, ApiCollection.NAME, ApiCollection.HOST_NAME))) {
            if (apiCollection.getDisplayName() != null) {
                collectionIds.putIfAbsent(apiCollection.getDisplayName().toLowerCase(), apiCollection.getId());
            }
        }

        int consecutiveFailures = 0;
        int detailsFailures = 0;
        int lastCheckTs = Context.now();
        for (Map.Entry<String, List<EndpointSummary>> entry : endpointsByHost.entrySet()) {
            List<String> messages = new ArrayList<>();
            for (EndpointSummary endpoint : entry.getValue()) {
                if (Context.now() - lastCheckTs >= CHECK_INTERVAL_SECONDS) {
                    if (!shouldContinue.getAsBoolean()) {
                        loggerMaker.infoAndAddToDb("Imperva API import stopped from the dashboard");
                        return;
                    }
                    lastCheckTs = Context.now();
                }
                ImpervaSchema schema = null;
                if (consecutiveFailures < MAX_CONSECUTIVE_DETAILS_FAILURES) {
                    try {
                        schema = client.fetchEndpointDetails(endpoint.getId());
                        consecutiveFailures = 0;
                    } catch (Exception e) {
                        consecutiveFailures++;
                        detailsFailures++;
                        loggerMaker.error("Failed to fetch Imperva details for endpoint " + endpoint.getId() + ": " + e.getMessage());
                    }
                }
                // if the detailed schema isn't available, a basic sample is built from the inventory (host, method, path)
                List<String> endpointMessages = schema != null ? toAktoMessages(schema, endpoint) : new ArrayList<>();
                if (endpointMessages.isEmpty()) {
                    endpointMessages = toAktoMessages(new ImpervaSchema(), endpoint);
                }
                messages.addAll(endpointMessages);
            }

            if (messages.isEmpty()) {
                continue;
            }
            if (!shouldContinue.getAsBoolean()) {
                loggerMaker.infoAndAddToDb("Imperva API import stopped from the dashboard");
                return;
            }
            lastCheckTs = Context.now();
            try {
                // skipKafka=true processes the messages directly, so the topic isn't used
                int collectionId = collectionIds.computeIfAbsent(entry.getKey(), ImpervaApiEndpointsImportJobUtil::createCollection);
                Utils.pushDataToKafka(collectionId, null, messages, new ArrayList<>(), true, false, true);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error importing Imperva APIs for host " + entry.getKey() + ": " + e.getMessage());
            }
        }

        loggerMaker.infoAndAddToDb(String.format("Imperva API import finished: %d endpoints, %d detail calls failed", endpoints.size(), detailsFailures));
    }

    private static List<String> toAktoMessages(ImpervaSchema schema, EndpointSummary endpoint) {
        if (StringUtils.isEmpty(schema.getHostName())) schema.setHostName(endpoint.getHostName());
        if (StringUtils.isEmpty(schema.getMethod())) schema.setMethod(endpoint.getMethod());
        if (StringUtils.isEmpty(schema.getResource())) schema.setResource(endpoint.getResource());
        if (StringUtils.isEmpty(schema.getMethod()) || StringUtils.isEmpty(schema.getResource())) {
            return new ArrayList<>();
        }
        schema.setResource(ImpervaSchemaParser.replacePathWildcards(schema.getResource()));
        return ImpervaSchemaParser.convertImpervaSchemaToAkto(schema, null, true, false, true).getUploadLogs()
            .stream().map(SwaggerUploadLog::getAktoFormat).collect(Collectors.toList());
    }

    private static int createCollection(String host) {
        // same as ApiCollectionsAction.createCollection, with an id that is free even when several are created in a second
        int id = Context.now();
        while (ApiCollectionsDao.instance.findOne(Filters.eq(ApiCollection.ID, id)) != null) {
            id++;
        }
        ApiCollectionsDao.instance.insertOne(new ApiCollection(id, host, Context.now(), new HashSet<>(), null, 0, false, true));
        return id;
    }
}
