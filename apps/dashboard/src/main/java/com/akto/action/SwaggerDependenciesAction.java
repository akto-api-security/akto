package com.akto.action;

import com.akto.dao.ApiDependenciesFromSwaggerDao;
import com.akto.dto.ApiDependenciesFromSwagger;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
public class SwaggerDependenciesAction extends UserAction {


    @Getter
    private List<ApiDependenciesFromSwagger> dependencies = new ArrayList<>();

    @Setter
    private int apiCollectionId;

    public String fetchSwaggerDependencies() {
        this.dependencies = ApiDependenciesFromSwaggerDao.instance.findAll(Filters.eq(SingleTypeInfo._API_COLLECTION_ID, apiCollectionId));
        return Action.SUCCESS.toUpperCase();
    }

}
