package com.akto.action.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.sources.TestSourceConfigsDao;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.GlobalEnums.TestCategory;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;

import org.bson.conversions.Bson;

public class MarketplaceAction extends UserAction {
    
    List<TestSourceConfig> testSourceConfigs;
    public String fetchAllMarketplaceSubcategories() {
        this.testSourceConfigs = TestSourceConfigsDao.instance.findAll(new BasicDBObject());
        return Action.SUCCESS.toUpperCase();
    }


    boolean defaultCreator;
    String subcategory;
    public String fetchTestingSources() {
        Bson creatorQ = Filters.ne(TestSourceConfig.CREATOR, "default");
        Bson subcategoryQ = Filters.eq(TestSourceConfig.SUBCATEGORY, subcategory);
        Bson filterQ = defaultCreator ? subcategoryQ : Filters.and(creatorQ, subcategoryQ);
        this.testSourceConfigs = TestSourceConfigsDao.instance.findAll(filterQ);
        return Action.SUCCESS.toUpperCase();
    }   

    String url;
    TestCategory category;
    Severity severity;
    String description;
    List<String> tags;
    String searchText;
    List<TestSourceConfig> searchResults = new ArrayList<>();
    private List<BasicDBObject> inbuiltTests;
    private TestCategory[] categories;

    public String addCustomTest() {
        TestSourceConfig alreadyExists = TestSourceConfigsDao.instance.findOne("_id", url);
        if (alreadyExists != null) {
            addActionError("This test file has already been added");
            return ERROR.toUpperCase();            
        }

        TestSourceConfig elem = new TestSourceConfig(url, category, subcategory, severity, description, getSUser().getLogin(), Context.now(),tags);
        TestSourceConfigsDao.instance.insertOne(elem);
        return Action.SUCCESS.toUpperCase();
    }

    private void searchUtilityFunction(){
        this.inbuiltTests = new ArrayList<>();
        this.categories = GlobalEnums.TestCategory.values();
        Bson filters = Filters.empty();

        Map<String, TestConfig> testConfigMap  = YamlTemplateDao.instance.fetchTestConfigMap(false);
        this.searchText = this.searchText.toLowerCase();
        
        for (Map.Entry<String, TestConfig> entry : testConfigMap.entrySet()) {
            Info info = entry.getValue().getInfo();
            if (info.getName().equals("FUZZING")) {
                continue;
            }
            BasicDBObject infoObj = new BasicDBObject();
            BasicDBObject superCategory = new BasicDBObject();
            BasicDBObject severity = new BasicDBObject();
            infoObj.put("issueDescription", info.getDescription());
            infoObj.put("issueDetails", info.getDetails());
            infoObj.put("issueImpact", info.getImpact());
            infoObj.put("issueTags", info.getTags());
            infoObj.put("testName", info.getName());
            infoObj.put("references", info.getReferences());
            infoObj.put("name", entry.getValue().getId());
            infoObj.put("_name", entry.getValue().getId());

            superCategory.put("displayName", info.getCategory().getDisplayName());
            superCategory.put("name", info.getCategory().getName());
            superCategory.put("shortName", info.getCategory().getShortName());

            severity.put("_name",info.getSeverity());
            superCategory.put("severity", severity);
            infoObj.put("superCategory", superCategory);

            String testCategory = info.getCategory().getDisplayName().toLowerCase();
            String testSeverity = info.getSeverity().toLowerCase();
            String businessTags = "";
            List<String> tagInfo = info.getTags();
            for (String tag: tagInfo) {
                businessTags = businessTags + tag.toLowerCase();
            }

            String matchedString = info.getDescription().toLowerCase() + " " + info.getName() + " " + testCategory  + " " + testSeverity + " " + businessTags;
            if(this.searchText == null || this.searchText.trim().length() == 0 || matchedString.matches("(.*)" + this.searchText + "(.*)")){
                this.inbuiltTests.add(infoObj);
            }

        }
        if(this.searchText != null){
            filters = Filters.or(
                Filters.regex("severity", this.searchText, "i"),
                Filters.regex("category", this.searchText, "i"),
                Filters.regex("tags", this.searchText, "i"),
                Filters.regex("description", this.searchText, "i"),
                Filters.regex("subcategory", this.searchText, "i")
            );
        }
        this.searchResults = TestSourceConfigsDao.instance.findAll(filters);
    }

    public String searchTestResults(){
        this.searchUtilityFunction();
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

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public TestCategory getCategory() {
        return this.category;
    }

    public void setCategory(TestCategory category) {
        this.category = category;
    }

    public Severity getSeverity() {
        return this.severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTags() {
        return this.tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<TestSourceConfig> getSearchResults() {
        return this.searchResults;
    }

    public void setSearchResults(List<TestSourceConfig> searchResults) {
        this.searchResults = searchResults;
    }

    public String getSearchText() {
        return this.searchText;
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    public List<BasicDBObject> getinbuiltTests() {
        return this.inbuiltTests;
    }

    public TestCategory[] getCategories() {
        return categories;
    }

    public void setCategories(TestCategory[] categories) {
        this.categories = categories;
    }
    
}