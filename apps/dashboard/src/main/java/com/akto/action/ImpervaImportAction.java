package com.akto.action;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.upload.FileUploadError;
import com.akto.dto.upload.SwaggerUploadLog;
import com.akto.imperva.model.ImpervaSchema;
import com.akto.imperva.parser.ImpervaSchemaParser;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.open_api.parser.ParserResult;
import com.akto.utils.Utils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensymphony.xwork2.Action;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ImpervaImportAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ImpervaImportAction.class, LogDb.DASHBOARD);
    private static final ExecutorService executorService = Executors.newFixedThreadPool(1);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_10_MB = 10 * 1024 * 1024;

    // String input field
    private String impervaString;
    private boolean generateMultipleSamples = false; // false = new logic (merged samples with responses), true = old logic (multiple samples)

    // Response fields
    private String message;

    @Override
    public String execute() {
        try {
            if (impervaString == null || impervaString.trim().isEmpty()) {
                addActionError("Empty file uploaded");
                return ERROR.toUpperCase();
            }

            if (impervaString.length() > MAX_10_MB) {
                addActionError("Upload file should be less than 10 MB");
                return ERROR.toUpperCase();
            }

            loggerMaker.debugAndAddToDb("Starting Imperva schema import", LogDb.DASHBOARD);

            // Parse JSON once to ImpervaSchema object
            ImpervaSchema impervaSchema = parseImpervaJson(impervaString);
            if (impervaSchema == null) {
                addActionError("Failed to parse Imperva JSON file");
                return ERROR.toUpperCase();
            }

            // Extract hostname from schema to create collection
            String collectionName = impervaSchema.getHostName();
            if (StringUtils.isEmpty(collectionName)) {
                addActionError("Hostname not found in Imperva file");
                return ERROR.toUpperCase();
            }

            ApiCollection apiCollection = getOrCreateCollection(collectionName);
            if (apiCollection == null) {
                addActionError("Failed to create or find collection: " + collectionName);
                return ERROR.toUpperCase();
            }

            final int collectionId = apiCollection.getId();
            final int accountId = Context.accountId.get();

            executorService.submit(new Runnable() {
                public void run() {
                    Context.accountId.set(accountId);

                    try {
                        loggerMaker.debugAndAddToDb("Starting to process Imperva file", LogDb.DASHBOARD);
                        // Parse Imperva schema (already deserialized)
                        ParserResult parsedResult = ImpervaSchemaParser.convertImpervaSchemaToAkto(
                            impervaSchema, null, true, generateMultipleSamples
                        );

                        List<FileUploadError> fileErrors = parsedResult.getFileErrors();
                        List<SwaggerUploadLog> messages = parsedResult.getUploadLogs();

                        List<String> stringMessages = messages.stream()
                                .map(SwaggerUploadLog::getAktoFormat)
                                .collect(Collectors.toList());

                        List<String> stringErrors = fileErrors.stream()
                                .map(FileUploadError::getError)
                                .collect(Collectors.toList());

                        String topic = System.getenv("AKTO_KAFKA_TOPIC_NAME");
                        if (topic == null) {
                            topic = "akto.api.logs";
                        }
                        
                        try {
                            loggerMaker.debugAndAddToDb("Calling Utils.pushDataToKafka for imperva file, for apiCollection id " + collectionId, LogDb.DASHBOARD);
                            Utils.pushDataToKafka(collectionId, topic, stringMessages, stringErrors, true, false, true);
                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb(e, "Error pushing data to kafka for imperva file");
                            throw new RuntimeException(e);
                        }

                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "ERROR while parsing Imperva file");
                    }
                }
            });

            message = "Imperva import started successfully. Please check the API collections to see the imported APIs.";
            return SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error during Imperva import: " + e.getMessage());
            addActionError("Import failed: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }


    /**
     * Gets existing collection or creates a new one with the given name
     */
    private ApiCollection getOrCreateCollection(String collectionName) {
        ApiCollection apiCollection = ApiCollectionsDao.instance.findByName(collectionName);
        if (apiCollection == null) {
            ApiCollectionsAction apiCollectionsAction = new ApiCollectionsAction();
            apiCollectionsAction.setSession(getSession());
            apiCollectionsAction.setCollectionName(collectionName);
            String result = apiCollectionsAction.createCollection();
            if (result.equalsIgnoreCase(Action.SUCCESS)) {
                List<ApiCollection> apiCollections = apiCollectionsAction.getApiCollections();
                if (apiCollections != null && apiCollections.size() > 0) {
                    apiCollection = apiCollections.get(0);
                    loggerMaker.debugAndAddToDb("Created new collection: " + collectionName + " with ID: " + apiCollection.getId(), LogDb.DASHBOARD);
                } else {
                    loggerMaker.errorAndAddToDb("Couldn't create api collection " + collectionName, LogDb.DASHBOARD);
                    return null;
                }
            } else {
                Collection<String> actionErrors = apiCollectionsAction.getActionErrors();
                if (actionErrors != null && actionErrors.size() > 0) {
                    for (String actionError: actionErrors) {
                        loggerMaker.errorAndAddToDb(actionError, LogDb.DASHBOARD);
                    }
                }
                return null;
            }
        } else {
            loggerMaker.debugAndAddToDb("Using existing collection: " + collectionName + " with ID: " + apiCollection.getId(), LogDb.DASHBOARD);
        }
        return apiCollection;
    }

    /**
     * Parses Imperva JSON string to ImpervaSchema object
     */
    private ImpervaSchema parseImpervaJson(String impervaJsonString) throws Exception {
        JsonNode rootNode = objectMapper.readTree(impervaJsonString);
            JsonNode dataNode = rootNode.get("data");
            if (dataNode == null) {
                String errorMessage = "Missing 'data' node in Imperva JSON";
                addActionError(errorMessage);
                throw new Exception(errorMessage);
            }
            ImpervaSchema schema = objectMapper.treeToValue(dataNode, ImpervaSchema.class);
            return schema;
    }

}