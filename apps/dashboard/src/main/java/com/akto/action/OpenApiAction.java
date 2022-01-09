package com.akto.action;

import com.akto.open_api.Main;
import io.swagger.v3.oas.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenApiAction extends UserAction{

    private static final Logger logger = LoggerFactory.getLogger(OpenApiAction.class);
    private int apiCollectionId;
    private String openAPIString = null;
    @Override
    public String execute() {
        try {
            OpenAPI openAPI = Main.init(apiCollectionId);
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
