package com.akto.utils.imperva;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiCollection;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.upload.SwaggerUploadLog;
import com.akto.listener.RuntimeListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.open_api.parser.ParserResult;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.Main;
import com.akto.utils.AccountHTTPCallParserAktoPolicyInfo;
import com.akto.utils.wiz.WizApiEndpointsImportJobUtil;
import com.akto.wiz.WizImportJobPageContext;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

public class ImpervaApiEndpointsImportJobUtil {

    private static final LoggerMaker logger = new LoggerMaker(ImpervaApiEndpointsImportJobUtil.class, LogDb.DASHBOARD);

    // accountId -> (collection name (lowercase host) -> collection id), rebuilt on the first batch of every run
    private static final Map<Integer, Map<String, Integer>> accountHostToCollectionId = new ConcurrentHashMap<>();

    public static void impervaApiEndpointsProcessor(ParserResult parsedResult, WizImportJobPageContext pageContext) {
        try {
            int accountId = Context.accountId.get();

            if (pageContext != null && pageContext.isFirstPage()) {
                WizApiEndpointsImportJobUtil.setupBeforeImport(accountId);
                loadCollections(accountId);
            }
            boolean syncImmediately = pageContext != null && pageContext.isLastPage();

            List<HttpResponseParams> responses = new ArrayList<>();
            for (SwaggerUploadLog uploadLog : parsedResult.getUploadLogs()) {
                try {
                    HttpResponseParams responseParams = HttpCallParser.parseKafkaMessage(uploadLog.getAktoFormat());
                    String host = getHost(responseParams);
                    if (host == null) {
                        continue;
                    }
                    // IMPERVA isn't a host-based source in the runtime, so the collection is assigned here
                    responseParams.getRequestParams().setApiCollectionId(getOrCreateCollection(accountId, host));
                    responses.add(responseParams);
                } catch (Exception e) {
                    logger.error("Error parsing Imperva message: " + e.getMessage(), e);
                }
            }

            AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
            responses = Main.filterBasedOnHeaders(responses, accountSettings);

            AccountHTTPCallParserAktoPolicyInfo aktoPolicyInfo = RuntimeListener.accountHTTPParserMap.get(accountId);
            HttpCallParser httpCallParser = aktoPolicyInfo.getHttpCallParser();
            httpCallParser.syncFunction(responses, syncImmediately, false, accountSettings, true);
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error processing Imperva import data: " + e.getMessage());
        }
    }

    private static String getHost(HttpResponseParams responseParams) {
        Map<String, List<String>> headers = responseParams.getRequestParams().getHeaders();
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if ("host".equalsIgnoreCase(entry.getKey()) && entry.getValue() != null && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0).trim().toLowerCase();
            }
        }
        return null;
    }

    private static void loadCollections(int accountId) {
        Map<String, Integer> hostToCollectionId = new HashMap<>();
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject(),
            Projections.include(ApiCollection.ID, ApiCollection.NAME, ApiCollection.HOST_NAME));
        for (ApiCollection apiCollection : apiCollections) {
            String name = apiCollection.getDisplayName();
            if (name != null) {
                hostToCollectionId.putIfAbsent(name.toLowerCase(), apiCollection.getId());
            }
        }
        accountHostToCollectionId.put(accountId, hostToCollectionId);
    }

    /**
     * Same naming as the manual Imperva upload (collection named after the host), so both land in one collection.
     */
    private static int getOrCreateCollection(int accountId, String host) {
        Map<String, Integer> hostToCollectionId = accountHostToCollectionId.computeIfAbsent(accountId, k -> new HashMap<>());
        Integer existingId = hostToCollectionId.get(host);
        if (existingId != null) {
            return existingId;
        }

        int id = Context.now();
        while (ApiCollectionsDao.instance.findOne(Filters.eq(ApiCollection.ID, id)) != null) {
            id++;
        }
        ApiCollection apiCollection = new ApiCollection(id, host, Context.now(), new HashSet<>(), null, 0, false, true);
        ApiCollectionsDao.instance.insertOne(apiCollection);
        hostToCollectionId.put(host, id);
        logger.infoAndAddToDb("Created collection " + host + " with id " + id + " for Imperva import");
        return id;
    }
}
