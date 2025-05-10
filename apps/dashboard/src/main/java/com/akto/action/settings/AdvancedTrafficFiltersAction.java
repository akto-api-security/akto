package com.akto.action.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bson.conversions.Bson;

import com.akto.action.UserAction;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.FilterConfigYamlParser;
import com.akto.dao.runtime_filters.AdvancedTrafficFiltersDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.usage.UsageMetric;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.Constants;
import com.akto.utils.TrafficFilterUtil;
import com.akto.utils.jobs.CleanInventory;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

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

            Set<Integer> deactivatedCollections = UsageMetricCalculator.getDeactivated();
            deactivatedCollections.add(0);

            List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(
                Filters.nin(Constants.ID, deactivatedCollections), Projections.include(ApiCollection.HOST_NAME, ApiCollection.NAME));
            YamlTemplate yamlTemplate = new YamlTemplate(filterConfig.getId(), Context.now(), getSUser().getLogin(), Context.now(), this.yamlContent, null, null);
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

            deactivatedCollections.remove(0);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            e.printStackTrace();
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }
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
