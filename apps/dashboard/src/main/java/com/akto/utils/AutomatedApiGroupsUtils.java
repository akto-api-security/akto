package com.akto.utils;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.akto.listener.InitializerListener;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.kafka.common.protocol.types.Field.Bool;
import org.bson.conversions.Bson;

import com.akto.action.ApiCollectionsAction;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.testing.RegexTestingEndpoints;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.github.GithubFile;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.google.protobuf.Api;
import com.mongodb.BasicDBObject;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

public class AutomatedApiGroupsUtils {

    private static final LoggerMaker loggerMaker = new LoggerMaker(RiskScoreTestingEndpointsUtils.class, LogDb.DASHBOARD);;

    private static final ExecutorService executorService = Executors.newFixedThreadPool(1);

    public static int delete_account_ctr = 0;

    public static final int UPDATE_BATCH_SIZE = 100;
    public static final int DELETE_BATCH_SIZE = 100;

    public static List<CSVRecord> fetchGroups() {
        GithubSync githubSync = new GithubSync();
        GithubFile githubFile = githubSync.syncFile("akto-api-security/akto","automated-api-groups/automated-api-groups.csv", null, null);
        String groupsCsvContent = null;

        if (githubFile == null) {
            // Use the local file as a fallback only for the initial creation of the automated groups
            try {
                String resourceName = "automated-api-groups.csv";
                groupsCsvContent = InitializerListener.convertStreamToString(InitializerListener.class.getResourceAsStream("/" + resourceName));
            } catch (Exception ex) {
                loggerMaker.errorAndAddToDb(ex, String.format("Error while loading automated groups csv file. Error: %s", ex.getMessage()), LogDb.DASHBOARD);
            }
        } else {
            groupsCsvContent = githubFile.getContent();
        }

        if (groupsCsvContent == null) {
            return null;
        }

        List<CSVRecord> apiGroupRecords = null;
        try (CSVParser csvParser = CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build()
                .parse(new StringReader(groupsCsvContent))) {
            apiGroupRecords = csvParser.getRecords();
        } catch (IOException e) {
            loggerMaker.errorAndAddToDb("Error while processing automated API groups CSV - " + e.getMessage(), LogDb.DASHBOARD);
        } 

        return apiGroupRecords;
    }

    public static void deleteAutomatedAPIGroup(List<ApiCollection> apiCollectionsDelete) {
        loggerMaker.debugAndAddToDb("Deleting automated API groups - count: " + apiCollectionsDelete.size(), LogDb.DASHBOARD);
        
        try {
            ApiCollectionsAction apiCollectionsAction = new ApiCollectionsAction();

            for (int start = 0; start < apiCollectionsDelete.size(); start += AutomatedApiGroupsUtils.DELETE_BATCH_SIZE) {
                int end = Math.min(start + AutomatedApiGroupsUtils.DELETE_BATCH_SIZE, apiCollectionsDelete.size());
                List<ApiCollection> batch = apiCollectionsDelete.subList(start, end);
                apiCollectionsAction.setApiCollections(batch);
                apiCollectionsAction.deleteMultipleCollections();
            }

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while deleting automated API group - " + e.getMessage(),  LogDb.DASHBOARD);
        }
        
    }

    public static void processAutomatedGroups(List<CSVRecord> apiGroupRecords) {
        loggerMaker.debugAndAddToDb("Syncing automated API groups", LogDb.DASHBOARD);

        try {
            Map<Integer, ApiCollection> apiCollectionsMap = new HashMap<>(); 
            List<WriteModel<ApiCollection>> bulkUpdatesForApiCollections = new ArrayList<>();

            List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());
            List<ApiCollection> apiCollectionsDelete = new ArrayList<>();
            
            for (ApiCollection apiCollection : apiCollections) {
                apiCollectionsMap.put(apiCollection.getId(), apiCollection);
            }

            for (CSVRecord csvRecord : apiGroupRecords) {
                String apiCollectionIdStr = csvRecord.get("apiCollectionId");
                apiCollectionIdStr = apiCollectionIdStr.replaceAll("_", "");
                int apiCollectionId = Integer.parseInt(apiCollectionIdStr);

                String groupName = csvRecord.get("groupName");
                Boolean isActive = Boolean.parseBoolean(csvRecord.get("isActive"));
                String regex = csvRecord.get("regex");

                if (isActive) {
                    if (!apiCollectionsMap.containsKey(apiCollectionId)) {
                        // If automated api group does not exist, create it
                        ApiCollection automatedGroup = ApiCollection.createManualCollection(apiCollectionId, groupName);
                        automatedGroup.setType(ApiCollection.Type.API_GROUP);
                        automatedGroup.setAutomated(true);
                        List<TestingEndpoints> automatedGroupConditions = new ArrayList<>();
                        automatedGroupConditions.add(new RegexTestingEndpoints(TestingEndpoints.Operator.OR, regex));
                        automatedGroup.setConditions(automatedGroupConditions);

                        bulkUpdatesForApiCollections.add(
                            new InsertOneModel<>(automatedGroup)
                        );
                        apiCollectionsMap.put(apiCollectionId, automatedGroup);
                    } else {
                        ApiCollection automatedGroup = apiCollectionsMap.get(apiCollectionId);
                        List<TestingEndpoints> automatedGroupConditions = automatedGroup.getConditions();
                        RegexTestingEndpoints regexTestingEndpoint = (RegexTestingEndpoints) automatedGroupConditions.get(0);
                        
                        // Update api group if regex has changed
                        if (!regexTestingEndpoint.getRegex().equals(regex)) {
                            regexTestingEndpoint.setRegex(regex);
                            
                            bulkUpdatesForApiCollections.add(
                                new UpdateOneModel<> (
                                    Filters.eq(ApiCollection.ID, apiCollectionId),
                                    Updates.set("conditions", automatedGroupConditions)
                                )
                            );
                        }
                    }
                } else {
                    if (apiCollectionsMap.containsKey(apiCollectionId)) {
                        // If automated api group already exists, delete it
                        ApiCollection apiCollection = apiCollectionsMap.get(apiCollectionId);

                        apiCollectionsDelete.add(apiCollection);
                        apiCollectionsMap.remove(apiCollectionId);
                    }
                }
            }

            if (bulkUpdatesForApiCollections.size() > 0) {
                try {
                    loggerMaker.debugAndAddToDb("Performing automated API group bulk writes, count: " + bulkUpdatesForApiCollections.size(), LogDb.DASHBOARD);
                    for (int start = 0; start < bulkUpdatesForApiCollections.size(); start += AutomatedApiGroupsUtils.UPDATE_BATCH_SIZE) {
                        int end = Math.min(start + AutomatedApiGroupsUtils.UPDATE_BATCH_SIZE, bulkUpdatesForApiCollections.size());
                        List<WriteModel<ApiCollection>> batch = bulkUpdatesForApiCollections.subList(start, end);
                        ApiCollectionsDao.instance.getMCollection().bulkWrite(batch);
                    }
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Error while performing automated API group bulk writes- " + e.getMessage(), LogDb.DASHBOARD);
                }
            }

            if (!apiCollectionsDelete.isEmpty()) {
                int accountId = Context.accountId.get();

                try {
                    executorService.submit(() -> {
                        Context.accountId.set(accountId);
                        deleteAutomatedAPIGroup(apiCollectionsDelete);

                        AutomatedApiGroupsUtils.delete_account_ctr += 1;
                        
                        // Sleep for 1.5 seconds after every 50 accounts
                        if (AutomatedApiGroupsUtils.delete_account_ctr % 50 == 0) {
                            try {
                                Thread.sleep(1500);
                            } catch (InterruptedException e) {
                            }
                        } 
                    });
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Error while deleting automated API group - " + e.getMessage(), LogDb.DASHBOARD);
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while processing automated API groups - " + e.getMessage(), LogDb.DASHBOARD);
        }
    }
}
