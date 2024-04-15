package com.akto.action.testing;

import com.akto.dao.testing.config.TestCollectionPropertiesDao;
import com.akto.dto.testing.config.TestCollectionProperty;
import com.mongodb.BasicDBObject;
import org.apache.commons.lang3.NotImplementedException;

import java.util.*;

import static com.opensymphony.xwork2.Action.SUCCESS;

public class TestCollectionConfigurationAction {

    public String execute() {
        throw new NotImplementedException("TestCollectionConfigurationAction - default method not implemented");
    }

    int apiCollectionId;

    List<TestCollectionProperty> testCollectionProperties;

    public String fetchTestCollectionConfiguration() {
        this.testCollectionProperties = TestCollectionPropertiesDao.fetchConfigs(apiCollectionId);
        return SUCCESS.toUpperCase();
    }

    Map<String, BasicDBObject> propertyIds;
    public String fetchPropertyIds() {
        this.propertyIds = new HashMap<>();
        for(TestCollectionProperty.Id ee: TestCollectionProperty.Id.values()) {
            BasicDBObject enumProps =
                new BasicDBObject("id", ee.name())
                .append("title", ee.getTitle())
                .append("type", ee.getType())
                .append("impactingCategories", ee.getImpactingCategories());
            propertyIds.put(ee.name(), enumProps);
        }
        return SUCCESS.toUpperCase();
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public List<TestCollectionProperty> getTestCollectionProperties() {
        return testCollectionProperties;
    }

    public Map<String, BasicDBObject> getPropertyIds() {
        return propertyIds;
    }

}
