package com.akto.action;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dao.file.FilesDao;
import com.akto.dao.upload.FileUploadLogsDao;
import com.akto.dao.upload.FileUploadsDao;
import com.akto.dto.ApiCollection;
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

import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.interceptor.ServletResponseAware;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class OpenApiAction extends UserAction implements ServletResponseAware {

    private static final LoggerMaker loggerMaker = new LoggerMaker(OpenApiAction.class);

    private final boolean skipKafka = DashboardMode.isLocalDeployment();

    private int apiCollectionId;
    private String openAPIString = null;
    private boolean includeHeaders = true;

    private String lastFetchedUrl;
    private String lastFetchedMethod;

    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    @Override
    public String execute() {
        try {
            ApiCollection apiCollection = ApiCollectionsDao.instance.findOne("_id", apiCollectionId);
            if (apiCollection == null) return ERROR.toUpperCase();

            loggerMaker.infoAndAddToDb("Found API Collection " + apiCollection.getHostName(), LogDb.DASHBOARD);
            String host =  apiCollection.getHostName();

            int limit = 100;
            List<SampleData> sampleDataList = SampleDataDao.instance.fetchSampleDataPaginated(
                    apiCollectionId, lastFetchedUrl, lastFetchedMethod, limit, 1
            );

            int size = sampleDataList.size();
            loggerMaker.infoAndAddToDb("Fetched sample data list " + size, LogDb.DASHBOARD);

            if (size < limit) {
                lastFetchedUrl = null;
                lastFetchedMethod = null;
            } else {
                SampleData last = sampleDataList.get(size-1);
                lastFetchedUrl = last.getId().getUrl();
                lastFetchedMethod = last.getId().getMethod().name();
            }
            loggerMaker.infoAndAddToDb("Fetching for " + lastFetchedUrl + " " + lastFetchedMethod, LogDb.DASHBOARD);

            SampleDataToSTI sampleDataToSTI = new SampleDataToSTI();
            sampleDataToSTI.setSampleDataToSTI(sampleDataList);
            loggerMaker.infoAndAddToDb("Converted to STI", LogDb.DASHBOARD);

            Map<String,Map<String, Map<Integer, List<SingleTypeInfo>>>> stiList = sampleDataToSTI.getSingleTypeInfoMap();
            OpenAPI openAPI = Main.init(apiCollection.getDisplayName(),stiList, includeHeaders, host);
            loggerMaker.infoAndAddToDb("Initialized openAPI", LogDb.DASHBOARD);

            openAPIString = Main.convertOpenApiToJSON(openAPI);
            loggerMaker.infoAndAddToDb("Initialize openAPI", LogDb.DASHBOARD);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,"ERROR while downloading openApi file " + e, LogDb.DASHBOARD);
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

    public String importDataFromOpenApiSpec(){

        int accountId = Context.accountId.get();

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
        executorService.schedule(new Runnable() {
            public void run() {
                Context.accountId.set(accountId);
                loggerMaker.infoAndAddToDb("Starting thread to process openAPI file", LogDb.DASHBOARD);
                SwaggerFileUpload fileUpload = FileUploadsDao.instance.getSwaggerMCollection().find(Filters.eq(Constants.ID, new ObjectId(fileUploadId))).first();
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
                    ParserResult parsedSwagger = Parser.convertOpenApiToAkto(openAPI, fileUploadId, true, new ArrayList<>());
                    List<FileUploadError> fileErrors = parsedSwagger.getFileErrors();

                    List<SwaggerUploadLog> messages = parsedSwagger.getUploadLogs();
                    messages = messages.stream().peek(m -> m.setUploadId(fileUploadId)).collect(Collectors.toList());

                    int chunkSize = 100;
                    int numberOfChunks = (int)Math.ceil((double)messages.size() / chunkSize);

                    List<SwaggerUploadLog> finalMessages = messages;
                    List<List<SwaggerUploadLog>> chunkedLists = IntStream.range(0, numberOfChunks)
                            .mapToObj(i -> finalMessages.subList(i * chunkSize, Math.min(finalMessages.size(), (i + 1) * chunkSize)))
                            .collect(Collectors.toList());

                    for(List<SwaggerUploadLog> chunk : chunkedLists){
                        loggerMaker.infoAndAddToDb("Inserting chunk of size " + chunk.size(), LogDb.DASHBOARD);
                        FileUploadLogsDao.instance.getSwaggerMCollection().insertMany(chunk, new InsertManyOptions().ordered(true));
                    }
                    loggerMaker.infoAndAddToDb("Inserted " + chunkedLists.size() + " chunks of logs", LogDb.DASHBOARD);

                    FileUploadsDao.instance.updateOne(Filters.eq(Constants.ID, new ObjectId(fileUploadId)), Updates.combine(
                            Updates.set("uploadStatus", FileUpload.UploadStatus.SUCCEEDED),
                            Updates.set("collectionName", title),
                            Updates.set("errors", fileErrors),
                            Updates.set("count",parsedSwagger.getTotalCount())
                    ));
                    loggerMaker.infoAndAddToDb("Finished processing openAPI file", LogDb.DASHBOARD);

                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "ERROR while parsing openAPI file", LogDb.DASHBOARD);
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
            loggerMaker.infoAndAddToDb(String.format("Starting thread to import %d swagger apis, import type: %s", uploads.size(), importType), LogDb.DASHBOARD);
            String topic = System.getenv("AKTO_KAFKA_TOPIC_NAME");

            try {
                String collectionId = swaggerFileUpload.getCollectionName();
                int aktoCollectionId = collectionId.hashCode();
                aktoCollectionId = aktoCollectionId < 0 ? aktoCollectionId * -1: aktoCollectionId;
                List<String> msgs = new ArrayList<>();
                loggerMaker.infoAndAddToDb(String.format("Processing swagger collection %s, aktoCollectionId: %s", swaggerFileUpload.getCollectionName(), aktoCollectionId), LogDb.DASHBOARD);
                for(SwaggerUploadLog upload : uploads){
                    String aktoFormat = upload.getAktoFormat();
                    msgs.add(aktoFormat);
                }
                if(ApiCollectionsDao.instance.findOne(Filters.eq("_id", aktoCollectionId)) == null){
                    String collectionName = swaggerFileUpload.getCollectionName();
                    loggerMaker.infoAndAddToDb(String.format("Creating manual collection for aktoCollectionId: %s and name: %s", aktoCollectionId, collectionName), LogDb.DASHBOARD);
                    ApiCollectionsDao.instance.insertOne(ApiCollection.createManualCollection(aktoCollectionId, collectionName));
                }
                loggerMaker.infoAndAddToDb(String.format("Pushing data in akto collection id %s", aktoCollectionId), LogDb.DASHBOARD);
                Utils.pushDataToKafka(aktoCollectionId, topic, msgs, new ArrayList<>(), skipKafka);
                loggerMaker.infoAndAddToDb(String.format("Pushed data in akto collection id %s", aktoCollectionId), LogDb.DASHBOARD);
                FileUploadsDao.instance.getSwaggerMCollection().updateOne(Filters.eq("_id", new ObjectId(uploadId)), new BasicDBObject("$set", new BasicDBObject("ingestionComplete", true).append("markedForDeletion", true)), new UpdateOptions().upsert(false));
                loggerMaker.infoAndAddToDb("Ingestion complete for " + swaggerFileUpload.getId().toString(), LogDb.DASHBOARD);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e,"Error pushing data to kafka", LogDb.DASHBOARD);
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
}
