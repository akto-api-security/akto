package com.akto.action;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.HttpResponseParams.Source;
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

    // File upload fields
    private File impervaFile;
    private String impervaFileContentType;
    private String impervaFileFileName;

    private boolean useHost = true;
    private boolean generateMultipleSamples = false; // false = new logic (merged samples with responses), true = old logic (multiple samples)
    private Source source = Source.IMPERVA; // Imperva imports treated similar to OpenAPI

    // Response fields
    private int totalCount;
    private List<FileUploadError> errors;
    private List<SwaggerUploadLog> uploadLogs;
    private String message;
    private String fileId;

    @Override
    public String execute() {
        try {
            if (impervaFile == null) {
                message = "Imperva file is required";
                return ERROR.toUpperCase();
            }

            loggerMaker.debugAndAddToDb("Starting Imperva schema import from file: " + impervaFileFileName, LogDb.DASHBOARD);

            // Read file content
            String impervaJsonString = readFileContent(impervaFile);
            if (impervaJsonString == null || impervaJsonString.trim().isEmpty()) {
                message = "Imperva file is empty or could not be read";
                return ERROR.toUpperCase();
            }

            // Parse JSON once to ImpervaSchema object
            ImpervaSchema impervaSchema = parseImpervaJson(impervaJsonString);
            if (impervaSchema == null) {
                message = "Failed to parse Imperva JSON file";
                return ERROR.toUpperCase();
            }

            // Extract hostname from schema to create collection
            String collectionName = impervaSchema.getHostName();
            if (StringUtils.isEmpty(collectionName)) {
                message = "Hostname not found in Imperva file";
                return ERROR.toUpperCase();
            }

            final int accountId = Context.accountId.get();
            executorService.submit(new Runnable() {
                public void run() {
                    Context.accountId.set(accountId);

                    try {
                        loggerMaker.debugAndAddToDb("Starting to process Imperva file", LogDb.DASHBOARD);

                        // Create or find collection by hostname
                        ApiCollection apiCollection = getOrCreateCollection(collectionName);
                        if (apiCollection == null) {
                            loggerMaker.errorAndAddToDb("Failed to create or find collection: " + collectionName, LogDb.DASHBOARD);
                            return;
                        }

                        int collectionId = apiCollection.getId();

                        // Parse Imperva schema (already deserialized)
                        ParserResult parsedResult = ImpervaSchemaParser.convertImpervaSchemaToAkto(
                            impervaSchema, null, useHost, generateMultipleSamples
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
                        if (topic == null) topic = "akto.api.logs";

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
            message = "Import failed: " + e.getMessage();
            return ERROR.toUpperCase();
        }
    }

    private String readFileContent(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            return new String(data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            loggerMaker.errorAndAddToDb(e, "Error reading uploaded file: " + e.getMessage());
            return null;
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
    private ImpervaSchema parseImpervaJson(String impervaJsonString) {
        try {
            JsonNode rootNode = objectMapper.readTree(impervaJsonString);
            JsonNode dataNode = rootNode.get("data");
            if (dataNode == null) {
                loggerMaker.errorAndAddToDb("Missing 'data' node in Imperva JSON", LogDb.DASHBOARD);
                return null;
            }
            ImpervaSchema schema = objectMapper.treeToValue(dataNode, ImpervaSchema.class);
            return schema;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error parsing Imperva JSON: " + e.getMessage());
            return null;
        }
    }

}