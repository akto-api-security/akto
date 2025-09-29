package com.akto.action;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.agents.DiscoveryAgentRunDao;
import com.akto.dao.context.Context;
import com.akto.dao.file.FilesDao;
import com.akto.dao.upload.FileUploadLogsDao;
import com.akto.dao.upload.FileUploadsDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.HttpResponseParams.Source;
import com.akto.dto.agents.Agent;
import com.akto.dto.agents.DiscoveryAgentRun;
import com.akto.dto.agents.Model;
import com.akto.dto.agents.State;
import com.akto.dto.files.File;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.upload.*;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.open_api.Main;
import com.akto.open_api.parser.Parser;
import com.akto.open_api.parser.ParserResult;
import com.akto.util.Constants;
import com.akto.util.DashboardMode;
import com.akto.utils.ApiInfoKeyToSampleData;
import com.akto.utils.GzipUtils;
import com.akto.utils.SampleDataToSTI;
import com.akto.utils.Utils;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.InsertOneResult;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.Setter;

import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.interceptor.ServletResponseAware;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class OpenApiAction extends UserAction implements ServletResponseAware {

    private static final LoggerMaker loggerMaker = new LoggerMaker(OpenApiAction.class, LogDb.DASHBOARD);

    private final boolean skipKafka = DashboardMode.isLocalDeployment();

    private int apiCollectionId;
    private String openAPIString = null;
    private boolean includeHeaders = true;

    private String lastFetchedUrl;
    private String lastFetchedMethod;

    private List<ApiInfo.ApiInfoKey> apiInfoKeyList;

    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();


    @Override
    public String execute() {
        try {
            ApiCollection apiCollection = ApiCollectionsDao.instance.findOne("_id", apiCollectionId);
            if (apiCollection == null) return ERROR.toUpperCase();

            List<SampleData> sampleDataList;
            int limit = 100;

            if(apiInfoKeyList != null && !apiInfoKeyList.isEmpty()) {
                sampleDataList = ApiInfoKeyToSampleData.convertApiInfoKeyToSampleData(apiInfoKeyList);
            } else {
                sampleDataList = SampleDataDao.instance.fetchSampleDataPaginated(
                        apiCollectionId, lastFetchedUrl, lastFetchedMethod, limit, 1
                );
            }

            loggerMaker.debugAndAddToDb("Found API Collection " + apiCollection.getHostName(), LogDb.DASHBOARD);
            String host =  apiCollection.getHostName();
            String apiCollectionName = apiCollection.getDisplayName();

            int size = sampleDataList.size();
            loggerMaker.debugAndAddToDb("Fetched sample data list " + size, LogDb.DASHBOARD);

            if (size < limit) {
                lastFetchedUrl = null;
                lastFetchedMethod = null;
            } else {
                SampleData last = sampleDataList.get(size-1);
                lastFetchedUrl = last.getId().getUrl();
                lastFetchedMethod = last.getId().getMethod().name();
            }
            loggerMaker.debugAndAddToDb("Fetching for " + lastFetchedUrl + " " + lastFetchedMethod, LogDb.DASHBOARD);

            SampleDataToSTI sampleDataToSTI = new SampleDataToSTI();
            sampleDataToSTI.setSampleDataToSTI(sampleDataList);
            loggerMaker.debugAndAddToDb("Converted to STI", LogDb.DASHBOARD);

            Map<String,Map<String, Map<Integer, List<SingleTypeInfo>>>> stiList = sampleDataToSTI.getSingleTypeInfoMap();
            OpenAPI openAPI = Main.init(apiCollectionName,stiList, includeHeaders, host);
            loggerMaker.debugAndAddToDb("Initialized openAPI", LogDb.DASHBOARD);

            openAPIString = Main.convertOpenApiToJSON(openAPI);
            loggerMaker.debugAndAddToDb("Initialize openAPI", LogDb.DASHBOARD);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,"ERROR while downloading openApi file " + e);
            return ERROR.toUpperCase();
        }

        return SUCCESS.toUpperCase();
    }

    public String burpSwagger() throws IOException {
        setIncludeHeaders(false);
        execute();

        servletResponse.setHeader("Content-Type", "application/json");
        try (PrintWriter writer = servletResponse.getWriter()) {
            writer.write(openAPIString);
            servletResponse.setStatus(200);
        } catch (Exception e) {
            servletResponse.sendError(500);
        }

        return null;
    }

    private static final String OPEN_API = "OpenAPI";
    private Source source = null;

    @Setter
    private boolean triggeredWithAIAgent;

    public String importDataFromOpenApiSpec(){

        int accountId = Context.accountId.get();

        if (apiCollectionId == 0) {
            apiCollectionId = -1;
        }

        File file = new File(FileUpload.UploadType.SWAGGER_FILE.toString(), GzipUtils.zipString(openAPIString));
        InsertOneResult fileInsertId = FilesDao.instance.insertOne(file);
        String fileId = fileInsertId.getInsertedId().asObjectId().getValue().toHexString();
        SwaggerFileUpload fileUpload = new SwaggerFileUpload();
        fileUpload.setSwaggerFileId(fileId);
        fileUpload.setUploadType(FileUpload.UploadType.SWAGGER_FILE);
        fileUpload.setUploadStatus(FileUpload.UploadStatus.IN_PROGRESS);
        fileUpload.setCollectionId(apiCollectionId);
        fileUpload.setUploadTs(Context.now());
        InsertOneResult insertOneResult = FileUploadsDao.instance.insertOne(fileUpload);
        String fileUploadId = insertOneResult.getInsertedId().asObjectId().getValue().toString();
        this.uploadId = fileUploadId;
        ApiCollection apiCollection = ApiCollectionsDao.instance.getMeta(apiCollectionId);
        if (apiCollection != null && apiCollection.getHostName() != null) {
            source = HttpResponseParams.Source.MIRRORING;
        }

        if (apiCollection != null && apiCollection.getType() != null && apiCollection.getType().equals(ApiCollection.Type.API_GROUP)) {
            addActionError("Can't upload OpenAPI file for collection groups");
            return ERROR.toUpperCase();
        }

        ApiInfo apiInfoWithSource = ApiInfoDao.instance.findOne(
            Filters.and(
                Filters.eq(ApiInfo.ID_API_COLLECTION_ID, apiCollectionId),
                Filters.exists(ApiInfo.SOURCES, true)
            )
        );

        // In case of quick start menu
        if (source == null && apiInfoWithSource == null) {
            loggerMaker.debugAndAddToDb("No source found, setting source to OPEN_API");
            source = Source.OPEN_API;
        }

        if(triggeredWithAIAgent) {
            // create init document for the ai agent
            String processId = UUID.randomUUID().toString();
            BasicDBObject agentInitDocument = new BasicDBObject();
            agentInitDocument.put("apiCollectionId", apiCollectionId);
            agentInitDocument.put("fileId", fileId);
            int timestamp = Context.now();
            int startTimestamp = -1;
            DiscoveryAgentRun discoveryAgentRun = new DiscoveryAgentRun(
                processId,
                agentInitDocument,
                Agent.DISCOVERY_AGENT,
                timestamp,
                startTimestamp,
                startTimestamp,
                State.SCHEDULED,
                new Model(),
                new HashMap<>(),
                Source.OPEN_API,
                new ArrayList<>(),
                accountId,
                apiCollectionId
            );
            DiscoveryAgentRunDao.instance.insertOne(discoveryAgentRun);
            return SUCCESS.toUpperCase();
        }

        executorService.schedule(new Runnable() {
            public void run() {
                Context.accountId.set(accountId);

                if (apiInfoWithSource == null && !Source.OPEN_API.equals(source)) {
                    loggerMaker.debugAndAddToDb("Starting to add sources to existing STIs and ApiInfos", LogDb.DASHBOARD);
                    Bson sourceUpdate = Updates.set(SingleTypeInfo.SOURCES + "." + source, new BasicDBObject("timestamp", Context.now()) );
                    Bson apiCollectionIdFilterForStis = Filters.eq(SingleTypeInfo._API_COLLECTION_ID, apiCollectionId);
                    SingleTypeInfoDao.instance.updateManyNoUpsert(apiCollectionIdFilterForStis, sourceUpdate);
                    Bson apiCollectionIdFilterForApiInfos = Filters.eq(ApiInfo.ID_API_COLLECTION_ID, apiCollectionId);
                    ApiInfoDao.instance.updateManyNoUpsert(apiCollectionIdFilterForApiInfos, sourceUpdate);
                    loggerMaker.debugAndAddToDb("Starting to process openAPI file", LogDb.DASHBOARD);
                } else {
                    loggerMaker.debugAndAddToDb("No need to add sources to STIs and ApiInfos", LogDb.DASHBOARD);
                }

                String title = OPEN_API + " ";
                try {
                    ParseOptions options = new ParseOptions();
                    options.setResolve(true);
                    options.setResolveFully(true);
                    SwaggerParseResult result = new OpenAPIParser().readContents(openAPIString, null, options);
                    OpenAPI openAPI = result.getOpenAPI();
                    if(openAPI.getInfo()!=null && openAPI.getInfo().getTitle()!=null){
                        title += openAPI.getInfo().getTitle();
                    } else {
                        title += Context.now();
                    }
                    boolean useHost = false;
                    if (Source.OPEN_API.equals(source)) {
                        useHost = true;
                    }

                    ParserResult parsedSwagger = Parser.convertOpenApiToAkto(openAPI, fileUploadId, useHost);
                    List<FileUploadError> fileErrors = parsedSwagger.getFileErrors();

                    List<SwaggerUploadLog> messages = parsedSwagger.getUploadLogs();
                    messages = messages.stream().peek(m -> m.setUploadId(fileUploadId)).collect(Collectors.toList());

                    int chunkSize = 100;
                    int numberOfChunks = (int)Math.ceil((double)messages.size() / chunkSize);

                    List<SwaggerUploadLog> finalMessages = messages;
                    List<List<SwaggerUploadLog>> chunkedLists = IntStream.range(0, numberOfChunks)
                            .mapToObj(i -> finalMessages.subList(i * chunkSize, Math.min(finalMessages.size(), (i + 1) * chunkSize)))
                            .collect(Collectors.toList());

                    if (Source.OPEN_API.equals(source)) {
                        for (List<SwaggerUploadLog> chunk : chunkedLists) {
                            loggerMaker.debugAndAddToDb("Inserting chunk of size " + chunk.size(), LogDb.DASHBOARD);
                            FileUploadLogsDao.instance.getSwaggerMCollection().insertMany(chunk,
                                    new InsertManyOptions().ordered(true));
                        }
                        loggerMaker.debugAndAddToDb("Inserted " + chunkedLists.size() + " chunks of logs",
                                LogDb.DASHBOARD);

                        FileUploadsDao.instance.updateOne(Filters.eq(Constants.ID, new ObjectId(fileUploadId)),
                                Updates.combine(
                                        Updates.set("uploadStatus", FileUpload.UploadStatus.SUCCEEDED),
                                        Updates.set("collectionName", title),
                                        Updates.set("errors", fileErrors),
                                        Updates.set("count", parsedSwagger.getTotalCount())));
                        loggerMaker.debugAndAddToDb("Finished processing openAPI file", LogDb.DASHBOARD);
                    }else {
                        List<String> stringMessages = messages.stream()
                                .map(SwaggerUploadLog::getAktoFormat)
                                .collect(Collectors.toList());

                        List<String> stringErrors = fileErrors.stream()
                                .map(FileUploadError::getError)
                                .collect(Collectors.toList());

                        String topic = System.getenv("AKTO_KAFKA_TOPIC_NAME");
                        if (topic == null) topic = "akto.api.logs";

                        try {
                            loggerMaker.debugAndAddToDb("Calling Utils.pushDataToKafka for openapi file, for apiCollection id " + apiCollectionId, LogDb.DASHBOARD);
                            Utils.pushDataToKafka(apiCollectionId, topic, stringMessages, stringErrors, true, false, true);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }

                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "ERROR while parsing openAPI file");
                    FileUploadsDao.instance.updateOne(Filters.eq(Constants.ID, new ObjectId(fileUploadId)), Updates.combine(
                            Updates.set("uploadStatus", FileUpload.UploadStatus.FAILED),
                            Updates.set("fatalError", e.getMessage())
                    ));
                }
            }
        }, 0, TimeUnit.SECONDS);

        return SUCCESS.toUpperCase();
    }

    private String uploadId;

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public BasicDBObject uploadDetails;

    public BasicDBObject getUploadDetails() {
        return uploadDetails;
    }

    public void setUploadDetails(BasicDBObject uploadDetails) {
        this.uploadDetails = uploadDetails;
    }

    public String fetchImportLogs(){
        if(this.uploadId == null || StringUtils.isEmpty(this.uploadId)){
            addActionError("uploadId is required");
        }
        SwaggerFileUpload swaggerFileUpload = FileUploadsDao.instance.getSwaggerMCollection().find(Filters.eq(Constants.ID, new ObjectId(uploadId))).first();

        if(swaggerFileUpload == null){
            addActionError("Invalid uploadId ");
            return ERROR.toUpperCase();
        }
        uploadDetails = new BasicDBObject();
        // Early return if the upload status is not SUCCEEDED
        if (swaggerFileUpload.getUploadStatus() != FileUpload.UploadStatus.SUCCEEDED) {
            uploadDetails.put("uploadStatus", swaggerFileUpload.getUploadStatus());
            return SUCCESS.toUpperCase();
        }

        uploadDetails.put("uploadStatus", FileUpload.UploadStatus.SUCCEEDED);

        List<SwaggerUploadLog> logs = new ArrayList<>();
        FileUploadLogsDao.instance.getSwaggerMCollection().find(Filters.eq("uploadId", uploadId)).into(logs);

        List<SwaggerUploadLog> errorLogs = logs.stream()
                .filter(log -> log.getErrors() != null && !log.getErrors().isEmpty())
                .collect(Collectors.toList());

        long apisWithErrorsAndCanBeImported = errorLogs.stream()
                .filter(log -> log.getAktoFormat() != null)
                .count();
        long apisWithErrorsAndCannotBeImported = errorLogs.size() - apisWithErrorsAndCanBeImported;

        BasicDBList modifiedLogs = new BasicDBList();
        if (!errorLogs.isEmpty()) {
            modifiedLogs.add(new BasicDBObject("term", "Url").append("description", "Errors"));
            errorLogs.forEach(log -> {
                String errorDescriptions = log.getErrors().stream()
                        .map(FileUploadError::getError)
                        .collect(Collectors.joining(","));
                BasicDBObject logEntry = new BasicDBObject();
                logEntry.put("term", log.getUrl() + " (" + log.getMethod() + ")");
                logEntry.put("description", errorDescriptions);
                modifiedLogs.add(logEntry);
            });
        }
        uploadDetails.put("collectionErrors", swaggerFileUpload.getErrors());
        uploadDetails.put("logs", modifiedLogs);
        uploadDetails.put("apisWithErrorsAndParsed", apisWithErrorsAndCanBeImported);
        uploadDetails.put("apisWithErrorsAndCannotBeImported", apisWithErrorsAndCannotBeImported);
        uploadDetails.put("correctlyParsedApis", logs.stream().filter(log -> log.getAktoFormat() != null && (log.getErrors() == null || log.getErrors().isEmpty())).count());
        uploadDetails.put("totalCount", swaggerFileUpload.getCount());

        return SUCCESS.toUpperCase();
    }

    public PostmanAction.ImportType importType;
    public String importFile(){
        if(importType == null || uploadId == null){
            addActionError("Invalid import type or upload id");
            return ERROR.toUpperCase();
        }
        SwaggerFileUpload swaggerFileUpload = FileUploadsDao.instance.getSwaggerMCollection().find(Filters.eq("_id", new ObjectId(uploadId))).first();
        if(swaggerFileUpload == null){
            addActionError("Invalid upload id");
            return ERROR.toUpperCase();
        }
        List<SwaggerUploadLog> uploads = new ArrayList<>();

        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.eq("uploadId", uploadId));
        filters.add(Filters.exists("aktoFormat", true));

        if(importType == PostmanAction.ImportType.ONLY_SUCCESSFUL_APIS){
            filters.add(Filters.exists("errors", false));
        }

        FileUploadLogsDao.instance.getSwaggerMCollection().find(Filters.and(filters.toArray(new Bson[0]))).into(uploads);
        int accountId = Context.accountId.get();

        new Thread(()-> {
            Context.accountId.set(accountId);
            loggerMaker.debugAndAddToDb(String.format("Starting thread to import %d swagger apis, import type: %s", uploads.size(), importType), LogDb.DASHBOARD);
            String topic = System.getenv("AKTO_KAFKA_TOPIC_NAME");

            try {
                String collectionId = swaggerFileUpload.getCollectionName();
                int aktoCollectionId = collectionId.hashCode();
                aktoCollectionId = aktoCollectionId < 0 ? aktoCollectionId * -1: aktoCollectionId;
                List<String> msgs = new ArrayList<>();
                loggerMaker.debugAndAddToDb(String.format("Processing swagger collection %s, aktoCollectionId: %s", swaggerFileUpload.getCollectionName(), aktoCollectionId), LogDb.DASHBOARD);
                for(SwaggerUploadLog upload : uploads){
                    String aktoFormat = upload.getAktoFormat();
                    msgs.add(aktoFormat);
                }
                if(ApiCollectionsDao.instance.findOne(Filters.eq("_id", aktoCollectionId)) == null){
                    String collectionName = swaggerFileUpload.getCollectionName();
                    loggerMaker.debugAndAddToDb(String.format("Creating manual collection for aktoCollectionId: %s and name: %s", aktoCollectionId, collectionName), LogDb.DASHBOARD);
                    ApiCollectionsDao.instance.insertOne(ApiCollection.createManualCollection(aktoCollectionId, collectionName));
                }
                loggerMaker.debugAndAddToDb(String.format("Pushing data in akto collection id %s", aktoCollectionId), LogDb.DASHBOARD);
                Utils.pushDataToKafka(aktoCollectionId, topic, msgs, new ArrayList<>(), skipKafka, true, true);
                loggerMaker.debugAndAddToDb(String.format("Pushed data in akto collection id %s", aktoCollectionId), LogDb.DASHBOARD);
                FileUploadsDao.instance.getSwaggerMCollection().updateOne(Filters.eq("_id", new ObjectId(uploadId)), new BasicDBObject("$set", new BasicDBObject("ingestionComplete", true).append("markedForDeletion", true)), new UpdateOptions().upsert(false));
                loggerMaker.debugAndAddToDb("Ingestion complete for " + swaggerFileUpload.getId().toString(), LogDb.DASHBOARD);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e,"Error pushing data to kafka");
            }
        }).start();


        return SUCCESS.toUpperCase();
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public void setOpenAPIString(String openAPIString) {
        this.openAPIString = openAPIString;
    }

    public String getOpenAPIString() {
        return openAPIString;
    }

    protected HttpServletResponse servletResponse;
    @Override
    public void setServletResponse(HttpServletResponse response) {
        this.servletResponse = response;
    }

    public void setIncludeHeaders(boolean includeHeaders) {
        this.includeHeaders = includeHeaders;
    }

    public String getLastFetchedUrl() {
        return lastFetchedUrl;
    }

    public void setLastFetchedUrl(String lastFetchedUrl) {
        this.lastFetchedUrl = lastFetchedUrl;
    }

    public String getLastFetchedMethod() {
        return lastFetchedMethod;
    }

    public void setLastFetchedMethod(String lastFetchedMethod) {
        this.lastFetchedMethod = lastFetchedMethod;
    }

    public PostmanAction.ImportType getImportType() {
        return importType;
    }

    public void setImportType(PostmanAction.ImportType importType) {
        this.importType = importType;
    }

    public List<ApiInfo.ApiInfoKey> getApiInfoKeyList() {
        return apiInfoKeyList;
    }

    public void setApiInfoKeyList(List<ApiInfo.ApiInfoKey> apiInfoKeyList) {
        this.apiInfoKeyList = apiInfoKeyList;
    }

    public void setSource(Source source) {
        this.source = source;
    }

}
