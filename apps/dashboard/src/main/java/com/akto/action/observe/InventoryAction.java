package com.akto.action.observe;

import java.util.List;

import com.akto.action.SensitiveFieldAction;
import com.akto.action.UserAction;
import com.akto.dao.APISpecDao;
import com.akto.dao.SensitiveParamInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.APISpec;
import com.akto.dto.SensitiveParamInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.SingleTypeInfo.SubType;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

public class InventoryAction extends UserAction {

    int apiCollectionId;

    BasicDBObject response;

    public String getAPICollection() {
        List<SingleTypeInfo> list = SingleTypeInfoDao.instance.findAll(Filters.eq("apiCollectionId", apiCollectionId));
        response = new BasicDBObject();
        response.put("data", new BasicDBObject("name", "Main application").append("endpoints", list));

        return Action.SUCCESS.toUpperCase();
    }

    public String loadRecentParameters() {
        int now = Context.now();
        int twoMonthsAgo = now - 62 * 24 * 60 * 60;
        List<SingleTypeInfo> list = SingleTypeInfoDao.instance.findAll(Filters.gt("timestamp", twoMonthsAgo));
        response = new BasicDBObject();
        response.put("data", new BasicDBObject("endpoints", list));

        return Action.SUCCESS.toUpperCase();
    }

    public String loadSensitiveParameters() {
        List list = SingleTypeInfoDao.instance.findAll(Filters.in("subType", SubType.getSensitiveTypes()));

        List<SensitiveParamInfo> customSensitiveList = SensitiveParamInfoDao.instance.findAll(Filters.eq("sensitive", true));

        list.addAll(customSensitiveList);
        response = new BasicDBObject();
        response.put("data", new BasicDBObject("endpoints", list));

        return Action.SUCCESS.toUpperCase();
    }

    public String getAllUrlsAndMethods() {
        response = new BasicDBObject();
        BasicDBObject ret = new BasicDBObject();
        response.put("data", ret);

        APISpec apiSpec = APISpecDao.instance.findById(apiCollectionId);
        SwaggerParseResult result = new OpenAPIParser().readContents(apiSpec.getContent(), null, null);
        OpenAPI openAPI = result.getOpenAPI();
        Paths paths = openAPI.getPaths();
        for(String path: paths.keySet()) {
            ret.append(path, paths.get(path).readOperationsMap().keySet());
        }

        return Action.SUCCESS.toUpperCase();
    }

    public int getApiCollectionId() {
        return this.apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public BasicDBObject getResponse() {
        return this.response;
    }

    public void setResponse(BasicDBObject response) {
        this.response = response;
    }
}
