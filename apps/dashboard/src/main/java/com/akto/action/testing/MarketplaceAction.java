package com.akto.action.testing;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dao.testing.sources.TestSourceConfigsDao;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.opensymphony.xwork2.Action;

import org.bson.conversions.Bson;

public class MarketplaceAction {
    
    Map<String, String> subcategories;
    public String fetchAllSubcategories() {
        Bson projQ = Projections.include(TestSourceConfig.SUBCATEGORY, TestSourceConfig.CREATOR);
        List<TestSourceConfig> tests = TestSourceConfigsDao.instance.findAll(new BasicDBObject(), projQ);
        
        this.subcategories = new HashMap<>();
        for (TestSourceConfig test: tests) {
            this.subcategories.put(test.getSubcategory(), test.getCreator());
        }

        return Action.SUCCESS.toUpperCase();
    }


    boolean defaultCreator;
    String subcategory;
    List<TestSourceConfig> testSourceConfigs;
    public String fetchTestingSources() {
        Bson creatorQ = 
            defaultCreator ? Filters.eq(TestSourceConfig.CREATOR, "default") : Filters.ne(TestSourceConfig.CREATOR, "default");
        
        Bson subcategoryQ = Filters.eq(TestSourceConfig.SUBCATEGORY, subcategory);
        
        Bson filterQ = Filters.and(creatorQ, subcategoryQ);
        this.testSourceConfigs = TestSourceConfigsDao.instance.findAll(filterQ);
        return Action.SUCCESS.toUpperCase();
    }   

    public Map<String, String> getSubcategories() {
        return this.subcategories;
    }

    public void setSubcategories(Map<String, String> subcategories) {
        this.subcategories = subcategories;
    }


    public boolean isDefaultCreator() {
        return this.defaultCreator;
    }

    public boolean getDefaultCreator() {
        return this.defaultCreator;
    }

    public void setDefaultCreator(boolean defaultCreator) {
        this.defaultCreator = defaultCreator;
    }

    public String getSubcategory() {
        return this.subcategory;
    }

    public void setSubcategory(String subcategory) {
        this.subcategory = subcategory;
    }

    public List<TestSourceConfig> getTestSourceConfigs() {
        return this.testSourceConfigs;
    }

    public void setTestSourceConfigs(List<TestSourceConfig> testSourceConfigs) {
        this.testSourceConfigs = testSourceConfigs;
    }
}
