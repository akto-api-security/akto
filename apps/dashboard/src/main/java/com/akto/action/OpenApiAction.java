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

public class OpenApiAction extends UserAction implements ServletResponseAware {

    private static final LoggerMaker loggerMaker = new LoggerMaker(OpenApiAction.class);
    private int apiCollectionId;
    private String openAPIString = null;
    private boolean includeHeaders = true;

    private String lastFetchedUrl;
    private String lastFetchedMethod;
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
            loggerMaker.errorAndAddToDb("ERROR while downloading openApi file " + e, LogDb.DASHBOARD);
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
            addActionError("ERROR while parsing openAPI file.");
            return ERROR.toUpperCase();
        }

        String topic = System.getenv("AKTO_KAFKA_TOPIC_NAME");

        if (messages.size() > 0) {
            apiCollectionId = title.hashCode();
            if (ApiCollectionsDao.instance.findOne(Filters.eq(Constants.ID, apiCollectionId)) == null) {
                ApiCollectionsDao.instance.insertOne(ApiCollection.createManualCollection(apiCollectionId, title));
            }

            try {
                Utils.pushDataToKafka(apiCollectionId, topic, messages, new ArrayList<>(), true);
            } catch (Exception e) {
                addActionError("ERROR while creating collection from openAPI file.");
                return ERROR.toUpperCase();
            }
        }
        return SUCCESS.toUpperCase();
    }

    public int getApiCollectionId() {
        return apiCollectionId;
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
