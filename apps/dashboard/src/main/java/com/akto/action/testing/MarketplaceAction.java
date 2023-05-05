package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.testing.sources.TestSourceConfigsDao;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.GlobalEnums.TestCategory;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;

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
//    private List<TestSubCategory> inbuiltTests;
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
//        this.inbuiltTests = new ArrayList<>();
        this.categories = GlobalEnums.TestCategory.values();
        Bson filters = Filters.empty();
        if(this.searchText == null){
//            for(TestSubCategory tsc : GlobalEnums.TestSubCategory.getValuesArray()){
//                this.inbuiltTests.add(tsc);
//            }
        }

        //fill from Updated test-source-config collection in mongodb
        else{
            filters = Filters.or(
                Filters.regex("severity", this.searchText, "i"),
                Filters.regex("category", this.searchText, "i"),
                Filters.regex("tags", this.searchText, "i"),
                Filters.regex("description", this.searchText, "i"),
                Filters.regex("subcategory", this.searchText, "i")
            );

            this.searchText = this.searchText.toLowerCase();
            //todo: shivam remove these comment out
            //fill from akto tests in global enums
//            for(TestSubCategory tsc : GlobalEnums.TestSubCategory.getValuesArray()){
//                String testCategory = tsc.getSuperCategory().getDisplayName().toLowerCase();
//                String testSeverity = tsc.getSuperCategory().getSeverity().toString().toLowerCase();
//                String businessTags = "";
//                IssueTags[] issueTags  = tsc.getIssueTags();
//                for(IssueTags tag : issueTags){
//                    businessTags = businessTags + tag.getName().toLowerCase();
//                }
//
//                String matchedString = tsc.getIssueDescription().toLowerCase() + " " + tsc.getTestName().toLowerCase() + " " + testCategory  + " " + testSeverity + " " + businessTags;
//                if(matchedString.matches("(.*)" + this.searchText + "(.*)")){
//                    this.inbuiltTests.add(tsc);
//                }
//
//            }
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

    public TestCategory[] getCategories() {
        return categories;
    }

    public void setCategories(TestCategory[] categories) {
        this.categories = categories;
    }
    
}