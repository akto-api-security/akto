package com.akto.action;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SampleDataDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
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

    private static final Logger logger = LoggerFactory.getLogger(OpenApiAction.class);
    private int apiCollectionId;
    private String openAPIString = null;
    private boolean includeHeaders = true;
    @Override
    public String execute() {
        try {
            
            List<SampleData> sampleData = SampleDataDao.instance.findAll(
                Filters.eq("_id.apiCollectionId", apiCollectionId)
            );
            ApiCollection apiCollection = ApiCollectionsDao.instance.findOne("_id", apiCollectionId);
            if (apiCollection == null) {
                return ERROR.toUpperCase();
            }
            String host =  apiCollection.getHostName();
            SampleDataToSTI sampleDataToSTI = new SampleDataToSTI();
            sampleDataToSTI.setSampleDataToSTI(sampleData);
            Map<String,Map<String, Map<Integer, List<SingleTypeInfo>>>> stiList = sampleDataToSTI.getSingleTypeInfoMap();
            OpenAPI openAPI = Main.init(apiCollection.getDisplayName(),stiList, includeHeaders, host);
            openAPIString = Main.convertOpenApiToJSON(openAPI);
        } catch (Exception e) {
            logger.error("ERROR while downloading openApi file " + e);
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
}
