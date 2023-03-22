package com.akto.action;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SampleDataDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.open_api.Main;
import com.akto.utils.SampleDataToSTI;
import com.mongodb.client.model.Filters;
import io.swagger.v3.oas.models.OpenAPI;
import org.apache.struts2.interceptor.ServletResponseAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
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

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
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
