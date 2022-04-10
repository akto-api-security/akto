package com.akto.action.observe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import com.mongodb.client.model.Sorts;
import com.opensymphony.xwork2.Action;

import org.bson.conversions.Bson;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

public class InventoryAction extends UserAction {

    int apiCollectionId;

    BasicDBObject response;

    public String fetchAPICollection() {
        List<SingleTypeInfo> list = SingleTypeInfoDao.instance.findAll(Filters.eq("apiCollectionId", apiCollectionId));
        response = new BasicDBObject();
        response.put("data", new BasicDBObject("name", "Main application").append("endpoints", list));

        return Action.SUCCESS.toUpperCase();
    }

    public List<SingleTypeInfo> fetchRecentParams(int deltaPeriod) {
        int now = Context.now();
        int twoMonthsAgo = now - deltaPeriod;
        List<SingleTypeInfo> list = SingleTypeInfoDao.instance.findAll(Filters.gt("timestamp", twoMonthsAgo), 0, 2, null);

        return list;
    }

    public final static int deltaPeriodValue = 600 * 24 * 60 * 60;

    // if this function is changed then make sure to update fetchApiInfoListForRecentEndpoints method too
    public String loadRecentParameters() {
        response = new BasicDBObject();
        List<SingleTypeInfo> list = fetchRecentParams(deltaPeriodValue);
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

    public String fetchAllUrlsAndMethods() {
        response = new BasicDBObject();
        BasicDBObject ret = new BasicDBObject();

        APISpec apiSpec = APISpecDao.instance.findById(apiCollectionId);
        if (apiSpec != null) {
            SwaggerParseResult result = new OpenAPIParser().readContents(apiSpec.getContent(), null, null);
            OpenAPI openAPI = result.getOpenAPI();
            Paths paths = openAPI.getPaths();
            for(String path: paths.keySet()) {
                ret.append(path, paths.get(path).readOperationsMap().keySet());
            }
        }

        response.put("data", ret);

        return Action.SUCCESS.toUpperCase();
    }

    private String sortKey;
    private int sortOrder;
    private int limit;
    private int skip;
    private Map<String, List> filters;
    private Map<String, String> filterOperators;

    private Bson prepareFilters() {
        int now = Context.now();
        int twoMonthsAgo = now - deltaPeriodValue;

        ArrayList<Bson> filterList = new ArrayList<>();
        filterList.add(Filters.gt("timestamp", twoMonthsAgo));
        for(Map.Entry<String, List> entry: filters.entrySet()) {
            String key = entry.getKey();
            List value = entry.getValue();

            if (value.size() == 0) continue;
            String operator = filterOperators.get(key);

            switch (key) {
                case "color": continue;
                case "url":
                case "param":
                    switch (operator) {
                        case "OR":
                        case "AND":
                            filterList.add(Filters.regex(key, ".*"+value.get(0)+".*", "i"));
                            break;
                        case "NOT":
                            filterList.add(Filters.not(Filters.regex(key, ".*"+value.get(0)+".*", "i")));
                            break;
                    }

                    break;
                case "timestamp": 
                    List<Long> ll = value;
                    filterList.add(Filters.lte(key, (long) (Context.now()) - ll.get(0) * 86400L));
                    filterList.add(Filters.gte(key, (long) (Context.now()) - ll.get(1) * 86400L));
                    break;
                default: 
                    switch (operator) {
                        case "OR":
                        case "AND":
                            filterList.add(Filters.in(key, value));
                            break;

                        case "NOT":
                            filterList.add(Filters.nin(key, value));
                            break;
                    }
                    
            }
        }

        return Filters.and(filterList);

    }

    private List<SingleTypeInfo> getMongoResults() {

        List<String> sortFields = new ArrayList<>();
        sortFields.add(sortKey);

        Bson sort = sortOrder == 1 ? Sorts.ascending(sortFields) : Sorts.descending(sortFields);

        List<SingleTypeInfo> list = SingleTypeInfoDao.instance.findAll(Filters.and(prepareFilters()), skip, limit, sort);
        return list;        
    }

    private long getTotalParams() {
        return SingleTypeInfoDao.instance.getMCollection().countDocuments(prepareFilters());
    }

    public String fetchChanges() {
        response = new BasicDBObject();
        response.put("data", new BasicDBObject("endpoints", getMongoResults()).append("total", getTotalParams()));

        return Action.SUCCESS.toUpperCase();
    }

    public String getSortKey() {
        return this.sortKey;
    }

    public void setSortKey(String sortKey) {
        this.sortKey = sortKey;
    }

    public int getSortOrder() {
        return this.sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public int getLimit() {
        return this.limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getSkip() {
        return this.skip;
    }

    public void setSkip(int skip) {
        this.skip = skip;
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

    public Map<String,List> getFilters() {
        return this.filters;
    }

    public void setFilters(Map<String,List> filters) {
        this.filters = filters;
    }

    public Map<String,String> getFilterOperators() {
        return this.filterOperators;
    }

    public void setFilterOperators(Map<String,String> filterOperators) {
        this.filterOperators = filterOperators;
    }

}
