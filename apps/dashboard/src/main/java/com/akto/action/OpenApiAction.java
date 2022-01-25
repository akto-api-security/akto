package com.akto.action;

import com.akto.dao.SingleTypeInfoDao;
import com.akto.open_api.Main;
import io.swagger.v3.oas.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
public class OpenApiAction extends UserAction{

    private static final Logger logger = LoggerFactory.getLogger(OpenApiAction.class);
    private int apiCollectionId;
    private String openAPIString = null;
    @Override
    public String execute() {
        try {
            Set<String> uniqueUrls = SingleTypeInfoDao.instance.getUniqueEndpoints(apiCollectionId);
            List<String> urlList = new ArrayList<>(uniqueUrls);
            OpenAPI openAPI = Main.init(apiCollectionId,urlList);
            openAPIString = Main.convertOpenApiToJSON(openAPI);
        } catch (Exception e) {
            logger.error("ERROR while downloading openApi file " + e);
            return ERROR.toUpperCase();
        }

        return SUCCESS.toUpperCase();
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public String getOpenAPIString() {
        return openAPIString;
    }
}
