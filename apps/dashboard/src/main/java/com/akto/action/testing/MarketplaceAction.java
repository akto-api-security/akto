package com.akto.action.testing;

import java.util.List;

import com.akto.dao.testing.sources.TestSourceConfigsDao;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;

import org.bson.conversions.Bson;

public class MarketplaceAction {
    
    List<TestSourceConfig> testSourceConfigs;
    public String fetchAllMarketplaceSubcategories() {
        this.testSourceConfigs = TestSourceConfigsDao.instance.findAll(new BasicDBObject());
        return Action.SUCCESS.toUpperCase();
    }


    boolean defaultCreator;
    String subcategory;
    public String fetchTestingSources() {
        Bson creatorQ = 
            defaultCreator ? Filters.eq(TestSourceConfig.CREATOR, "default") : Filters.ne(TestSourceConfig.CREATOR, "default");
        
        Bson subcategoryQ = Filters.eq(TestSourceConfig.SUBCATEGORY, subcategory);
        
        Bson filterQ = Filters.and(creatorQ, subcategoryQ);
        this.testSourceConfigs = TestSourceConfigsDao.instance.findAll(filterQ);
        return Action.SUCCESS.toUpperCase();
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
