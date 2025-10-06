package com.akto.action.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import com.akto.DaoInit;
import com.akto.action.UserAction;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.FilterConfigYamlParser;
import com.akto.dao.runtime_filters.AdvancedTrafficFiltersDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.Constants;
import com.akto.utils.TrafficFilterUtil;
import com.akto.utils.jobs.CleanInventory;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import com.akto.dto.traffic.Key;

import lombok.Getter;
import static com.akto.utils.Utils.deleteApis;

public class AdvancedTrafficFiltersAction extends UserAction {

    private List<YamlTemplate> templatesList;
    private String yamlContent;
    private String templateId;
    private boolean inactive;

    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public String fetchAllFilterTemplates(){
        List<YamlTemplate> yamlTemplates =  AdvancedTrafficFiltersDao.instance.findAll(
            Filters.empty(),
            0, 100000,Sorts.descending(YamlTemplate.UPDATED_AT),
            Projections.include(
                Constants.ID, YamlTemplate.AUTHOR, YamlTemplate.CONTENT, YamlTemplate.INACTIVE
            )
        );
        this.templatesList = yamlTemplates;
        return SUCCESS.toUpperCase();
    }

    public String saveYamlTemplateForTrafficFilters(){
        FilterConfig filterConfig = new FilterConfig();
        try {
            filterConfig = FilterConfigYamlParser.parseTemplate(yamlContent, true);
            if (filterConfig.getId() == null) {
                throw new Exception("id field cannot be empty");
            }
            if (filterConfig.getFilter() == null) {
                throw new Exception("filter field cannot be empty");
            }
            String userName = "system";
            if(getSUser() != null && !getSUser().getLogin().isEmpty()){
                userName = getSUser().getLogin();
            }
            List<Bson> updates = TrafficFilterUtil.getDbUpdateForTemplate(this.yamlContent, userName);
            AdvancedTrafficFiltersDao.instance.updateOne(
                    Filters.eq(Constants.ID, filterConfig.getId()),
                    Updates.combine(updates));

        } catch (Exception e) {
            e.printStackTrace();
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public String changeActivityOfFilter() {
        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
        options.upsert(false);
        AdvancedTrafficFiltersDao.instance.getMCollection().findOneAndUpdate(
            Filters.eq(Constants.ID, this.templateId),
            Updates.set(YamlTemplate.INACTIVE, this.inactive),
            options
        );

        return SUCCESS.toUpperCase();
    }

    public String deleteAdvancedFilter(){
        AdvancedTrafficFiltersDao.instance.getMCollection().deleteOne(
            Filters.eq(Constants.ID, this.templateId)
        );
        return SUCCESS.toUpperCase();
    }

    private boolean deleteAPIsInstantly;
    public static void main(String[] args) {
        Context.accountId.set(1000000);
        DaoInit.init(new ConnectionString("mongodb://localhost:27017"));
        AdvancedTrafficFiltersAction action = new AdvancedTrafficFiltersAction();
        action.setDeleteAPIsInstantly(false);
        String content = "id: DEFAULT_BLOCK_FILTER\nfilter:\n  or:\n    - url:\n        regex: (\\.\\.|\\/[^\\/?]*\\.[^\\/?]*(\\?|$))\n    - response_code:\n        eq: 302\n    - response_headers:\n        for_one:\n          key:\n            eq: content-type\n          value:\n            contains_either:\n              - html\n              - text/html\n    - and:\n        - method: \n            eq: GET\n        - response_payload:\n            or: \n              - length:\n                  eq: 0\n              - eq: \"{}\"";
        action.setYamlContent(content);
        action.syncTrafficFromFilters();
    }

    public String syncTrafficFromFilters(){
        FilterConfig filterConfig = new FilterConfig();
        try {
            filterConfig = FilterConfigYamlParser.parseTemplate(yamlContent, true);
            if (filterConfig.getId() == null) {
                throw new Exception("id field cannot be empty");
            }
            if (filterConfig.getFilter() == null) {
                throw new Exception("filter field cannot be empty");
            }

            List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(
                Filters.ne(ApiCollection._TYPE, ApiCollection.Type.API_GROUP.name()), Projections.include(ApiCollection.HOST_NAME, ApiCollection.NAME));
            YamlTemplate yamlTemplate = new YamlTemplate(filterConfig.getId(), Context.now(), "aryan@akto.io", Context.now(), this.yamlContent, null, null);
            int accountId = Context.accountId.get();
            executorService.schedule( new Runnable() {
                public void run() {
                    Context.accountId.set(accountId);
                    try {
                        CleanInventory.cleanFilteredSampleDataFromAdvancedFilters(apiCollections,Arrays.asList(yamlTemplate),new ArrayList<>() , "",deleteAPIsInstantly, true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, 0 , TimeUnit.SECONDS);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            e.printStackTrace();
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    @Getter
    public int deleteApisCount;

    

    public String deleteNonHostApiInfos() {
        try {
            int accountId = Context.accountId.get();
            if (this.deleteAPIsInstantly) {
                executorService.schedule(new Runnable() {
                    public void run() {
                        Context.accountId.set(accountId);
                        try {
                            CleanInventory.deleteApiInfosForMissingSTIs(deleteAPIsInstantly);
                            System.out.println("Done");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }, 0, TimeUnit.SECONDS);
            }else{
                this.deleteApisCount = CleanInventory.deleteApiInfosForMissingSTIs(deleteAPIsInstantly);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    @Getter
    BasicDBObject response;

    public String deleteOptionAndSlashApis(){
        List<ApiCollection> apiCollectionList = ApiCollectionsDao.fetchAllHosts();
        response = new BasicDBObject();
        for (ApiCollection apiCollection: apiCollectionList) {
            if(apiCollection.isDeactivated()) {
                continue;
            }
            List<Key> toBeDeleted = new ArrayList<>();
            if (apiCollection.getHostName() == null) {
                continue;
            }
            List<Key> toBeMoved = new ArrayList<>();
            int optionsApisCount = 0;
            int slashApisCount = 0;
            List<BasicDBObject> endpoints = ApiCollectionsDao.fetchEndpointsInCollectionUsingHost(apiCollection.getId(), 0, false);

            if (endpoints == null || endpoints.isEmpty()) {
                continue;
            }
            for (BasicDBObject singleTypeInfo: endpoints) {
                singleTypeInfo = (BasicDBObject) (singleTypeInfo.getOrDefault("_id", new BasicDBObject()));
                int apiCollectionId = singleTypeInfo.getInt("apiCollectionId");
                String url = singleTypeInfo.getString("url");
                String method = singleTypeInfo.getString("method");

                Key key = new Key(apiCollectionId, url, Method.fromString(method), -1, 0, 0);

                if (method.equalsIgnoreCase("options")) {
                    toBeDeleted.add(key);
                    optionsApisCount++;
                    continue;
                }else if(!StringUtils.isEmpty(url) && !url.startsWith("/") && SingleTypeInfo.doesNotStartWithSuperType(url) && !url.startsWith("http")){
                    toBeMoved.add(key);
                    slashApisCount++;
                    continue;
                }
            }

            if(!deleteAPIsInstantly) {
                response.put(apiCollection.getHostName(), new BasicDBObject()
                        .append("optionsApisCount", optionsApisCount)
                        .append("slashApisCount", slashApisCount)); 
            }else{
                if (!toBeDeleted.isEmpty() || !toBeMoved.isEmpty()) {
                    if(!toBeMoved.isEmpty()) {
                        CleanInventory.moveApisFromSampleData(toBeMoved, false);
                        deleteApis(toBeMoved);
                    }else if(!toBeDeleted.isEmpty()) {
                        deleteApis(toBeDeleted);
                    }
                    
                }
            }
        }

        return SUCCESS.toUpperCase();
    }


    public List<YamlTemplate> getTemplatesList() {
        return templatesList;
    }

    public void setYamlContent(String yamlContent) {
        this.yamlContent = yamlContent;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public void setInactive(boolean inactive) {
        this.inactive = inactive;
    }

    public void setDeleteAPIsInstantly(boolean deleteAPIsInstantly) {
        this.deleteAPIsInstantly = deleteAPIsInstantly;
    }

    
}
