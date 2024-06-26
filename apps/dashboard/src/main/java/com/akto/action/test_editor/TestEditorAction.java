package com.akto.action.test_editor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.conversions.Bson;

import com.akto.dao.test_editor.TestConfigYamlParser;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.test_editor.FilterNode;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.testing.AuthMechanism;
import com.akto.rules.RequiredConfigs;
import com.akto.store.AuthMechanismStore;
import com.akto.testing.yaml_tests.YamlTestTemplate;
import com.akto.util.enums.GlobalEnums.TestCategory;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;

public class TestEditorAction {
    
    private TestCategory testCategory;
    private Map<String, Set<String>> subCategoryVsMissingConfigsMap = new HashMap<>();

    public String getMissingConfigsForTestCategory(){
        AuthMechanism attackerToken = AuthMechanismStore.create().getAuthMechanism();
        RequiredConfigs.initiate();

        Bson filter = Filters.empty();

        if(this.testCategory != null){
            filter = Filters.and(
                filter,
                Filters.eq(YamlTemplate.CATEGORY_STRING, testCategory)
            );
        }

        List<YamlTemplate> yamlTemplates = YamlTemplateDao.instance.findAll(filter);
        for(YamlTemplate yamlTemplate: yamlTemplates){
            try {
                TestConfig testConfig = TestConfigYamlParser.parseTemplate(yamlTemplate.getContent());

                FilterNode filterNode = testConfig.getApiSelectionFilters().getNode();
                ExecutorNode executorNode = testConfig.getExecute().getNode();
                YamlTestTemplate yamlTestTemplate = new YamlTestTemplate(
                    null,filterNode, null, executorNode,
                    null, null, null, attackerToken, 
                    null, null, null, null);
                
                Set<String> missingConfigList = yamlTestTemplate.requireConfig();
                subCategoryVsMissingConfigsMap.put(yamlTemplate.getId(), missingConfigList);
            } catch (Exception e) {
                e.printStackTrace();
                return Action.ERROR.toUpperCase();
            }
        }
        return Action.SUCCESS.toUpperCase();
    }

    public void setTestCategory(TestCategory testCategory) {
        this.testCategory = testCategory;
    }

    public Map<String, Set<String>> getSubCategoryVsMissingConfigsMap() {
        return subCategoryVsMissingConfigsMap;
    }

}
