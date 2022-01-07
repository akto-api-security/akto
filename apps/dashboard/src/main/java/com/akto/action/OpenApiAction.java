package com.akto.action;

import com.akto.open_api.Main;
import io.swagger.v3.oas.models.OpenAPI;

public class OpenApiAction extends UserAction{

    private int apiCollectionId;
    private String openAPIString = null;
    @Override
    public String execute() {
        try {
            OpenAPI openAPI = Main.init(apiCollectionId);
            openAPIString = Main.convertOpenApiToJSON(openAPI);
            System.out.println(openAPIString);
        } catch (Exception e) {
            e.printStackTrace();
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
