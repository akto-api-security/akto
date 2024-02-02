package com.akto.action;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.open_api.Main;
import com.akto.open_api.parser.Parser;
import com.akto.util.Constants;
import com.akto.utils.SampleDataToSTI;
import com.akto.utils.Utils;
import com.mongodb.client.model.Filters;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import org.apache.struts2.interceptor.ServletResponseAware;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class OpenApiAction extends UserAction implements ServletResponseAware {

    private static final LoggerMaker loggerMaker = new LoggerMaker(OpenApiAction.class);
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
            String host =  apiCollection.getHostName();

            int limit = 200;
            List<SampleData> sampleDataList = SampleDataDao.instance.fetchSampleDataPaginated(
                    apiCollectionId, lastFetchedUrl, lastFetchedMethod, limit, 1
            );

            int size = sampleDataList.size();
            if (size < limit) {
                lastFetchedUrl = null;
                lastFetchedMethod = null;
            } else {
                SampleData last = sampleDataList.get(size-1);
                lastFetchedUrl = last.getId().getUrl();
                lastFetchedMethod = last.getId().getMethod().name();
            }

            SampleDataToSTI sampleDataToSTI = new SampleDataToSTI();
            sampleDataToSTI.setSampleDataToSTI(sampleDataList);
            Map<String,Map<String, Map<Integer, List<SingleTypeInfo>>>> stiList = sampleDataToSTI.getSingleTypeInfoMap();
            OpenAPI openAPI = Main.init(apiCollection.getDisplayName(),stiList, includeHeaders, host);
            openAPIString = Main.convertOpenApiToJSON(openAPI);
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
        executorService.schedule(new Runnable() {
            public void run() {
                Context.accountId.set(accountId);
                loggerMaker.infoAndAddToDb("Starting thread to process openAPI file", LogDb.DASHBOARD);
                List<String> messages = new ArrayList<>();
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
                    messages = Parser.convertOpenApiToAkto(openAPI);
                    
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "ERROR while parsing openAPI file", LogDb.DASHBOARD);
                }
        
                String topic = System.getenv("AKTO_KAFKA_TOPIC_NAME");
        
                if (messages.size() > 0) {
                    apiCollectionId = title.hashCode();
                    ApiCollection apiCollection = ApiCollectionsDao.instance.findOne(Filters.eq(Constants.ID, apiCollectionId));
                    if (apiCollection == null) {
                        ApiCollectionsDao.instance.insertOne(ApiCollection.createManualCollection(apiCollectionId, title));
                    }
        
                    try {
                        Utils.pushDataToKafka(apiCollectionId, topic, messages, new ArrayList<>(), true);
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "ERROR while creating collection from openAPI file", LogDb.DASHBOARD);
                    }
                }
            }
        }, 0, TimeUnit.SECONDS);

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
}
