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

    private static final int LOGIN_APIS_COLLECTION_ID = 1;
    private static final int SESSION_MANAGEMENT_APIS_COLLECTION_ID = 2;
    private static final int PASSWORD_RESET_APIS_COLLECTION_ID = 3;
    private static final int USER_MANAGEMENT_APIS_COLLECTION_ID = 4;
    private static final int SIGNUP_APIS_COLLECTION_ID = 5;

    private static final Map<String, Integer> templateCollectionMap = new HashMap<>();
    private Map<String, Integer> templateGroupsMap = new HashMap<>();   

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

        if(templateCollectionMap.isEmpty()){
            fillCategoriesMap();
        }
        this.templateGroupsMap = templateCollectionMap;

        return Action.SUCCESS.toUpperCase();
    }

    private void fillCategoriesMap(){
        templateCollectionMap.put("AUTH_BYPASS_BLANK_PASSWORD", LOGIN_APIS_COLLECTION_ID);
        templateCollectionMap.put("AUTH_BYPASS_LOCKED_ACCOUNT_TOKEN_ROLE", SESSION_MANAGEMENT_APIS_COLLECTION_ID);
        templateCollectionMap.put("AUTH_BYPASS_MULTI_CREDENTIAL", LOGIN_APIS_COLLECTION_ID);
        templateCollectionMap.put("AUTH_BYPASS_MULTI_CREDENTIAL_SINGLE_PARAM", LOGIN_APIS_COLLECTION_ID);
        templateCollectionMap.put("TEST_PASSWD_CHANGE", SESSION_MANAGEMENT_APIS_COLLECTION_ID);
        templateCollectionMap.put("AUTH_BYPASS_PASSWORD_RESET", PASSWORD_RESET_APIS_COLLECTION_ID);
        templateCollectionMap.put("AUTH_BYPASS_SQL_INJECTION", LOGIN_APIS_COLLECTION_ID);
        templateCollectionMap.put("AUTH_BYPASS_STAGING_URL", LOGIN_APIS_COLLECTION_ID);
        templateCollectionMap.put("2FA_BROKEN_LOGIC_AUTH_TOKEN_TEST", SESSION_MANAGEMENT_APIS_COLLECTION_ID);
        templateCollectionMap.put("BYPASS_2FA_BRUTE_FORCE", SESSION_MANAGEMENT_APIS_COLLECTION_ID);
        templateCollectionMap.put("SSL_ENABLE_CHECK", SESSION_MANAGEMENT_APIS_COLLECTION_ID);
        templateCollectionMap.put("SSL_ENABLE_CHECK_AUTH", SESSION_MANAGEMENT_APIS_COLLECTION_ID);
        templateCollectionMap.put("CSRF_LOGIN_ATTACK", LOGIN_APIS_COLLECTION_ID);
        templateCollectionMap.put("EXPIRES_MAX_AGE_CHECK", SESSION_MANAGEMENT_APIS_COLLECTION_ID);
        templateCollectionMap.put("GRAPHQL_CONTENT_TYPE_CSRF", SESSION_MANAGEMENT_APIS_COLLECTION_ID);
        templateCollectionMap.put("GRAPHQL_HTTP_METHOD_CSRF", SESSION_MANAGEMENT_APIS_COLLECTION_ID);
        templateCollectionMap.put("GRAPHQL_NON_JSON_QUERY_CSRF", SESSION_MANAGEMENT_APIS_COLLECTION_ID);
        templateCollectionMap.put("GRAPHQL_UNAUTHENTICATED_MUTATION", SESSION_MANAGEMENT_APIS_COLLECTION_ID);
        templateCollectionMap.put("JWT_HEADER_PARAM_INJECTION_JWK", SESSION_MANAGEMENT_APIS_COLLECTION_ID);
        templateCollectionMap.put("JWT_HEADER_PARAM_INJECTION_KID", SESSION_MANAGEMENT_APIS_COLLECTION_ID);
        templateCollectionMap.put("ADD_JKU_TO_JWT", SESSION_MANAGEMENT_APIS_COLLECTION_ID);
        templateCollectionMap.put("JWT_INVALID_SIGNATURE", SESSION_MANAGEMENT_APIS_COLLECTION_ID);
        templateCollectionMap.put("JWT_NONE_ALGO", SESSION_MANAGEMENT_APIS_COLLECTION_ID);
        templateCollectionMap.put("AUTH_BYPASS_LDAP", LOGIN_APIS_COLLECTION_ID);
        templateCollectionMap.put("LDAP_ERRORS", LOGIN_APIS_COLLECTION_ID);
        templateCollectionMap.put("LOGOUT_AUTH_TOKEN_TEST", SESSION_MANAGEMENT_APIS_COLLECTION_ID);
        templateCollectionMap.put("REMOVE_TOKENS", SESSION_MANAGEMENT_APIS_COLLECTION_ID);
        templateCollectionMap.put("PASSWD_CHANGE_BRUTE_FORCE", SESSION_MANAGEMENT_APIS_COLLECTION_ID);
        templateCollectionMap.put("PASSWD_RESET_POISON_MIDDLEWARE", PASSWORD_RESET_APIS_COLLECTION_ID);
        templateCollectionMap.put("REMOVE_CSRF", SESSION_MANAGEMENT_APIS_COLLECTION_ID);
        templateCollectionMap.put("REMOVE_XSRF", SESSION_MANAGEMENT_APIS_COLLECTION_ID);
        templateCollectionMap.put("REPLACE_CSRF", SESSION_MANAGEMENT_APIS_COLLECTION_ID);
        templateCollectionMap.put("USER_AGENT_CSRF", SESSION_MANAGEMENT_APIS_COLLECTION_ID);
        templateCollectionMap.put("USER_ENUM_ACCOUNT_LOCK", USER_MANAGEMENT_APIS_COLLECTION_ID);
        templateCollectionMap.put("USER_ENUM_FOLDER_ACCESS", USER_MANAGEMENT_APIS_COLLECTION_ID);
        templateCollectionMap.put("USER_ENUM_PASSWORD_RESET", PASSWORD_RESET_APIS_COLLECTION_ID);
        templateCollectionMap.put("USER_ENUM_REGISTER", SIGNUP_APIS_COLLECTION_ID);
        templateCollectionMap.put("USER_ENUM_RESPONSE_TIME", LOGIN_APIS_COLLECTION_ID);
        templateCollectionMap.put("USER_ENUM_HTTP_CODES", LOGIN_APIS_COLLECTION_ID);
        templateCollectionMap.put("USER_ENUM_RESPONSE_CONTENT", LOGIN_APIS_COLLECTION_ID);
        templateCollectionMap.put("USER_ENUM_INVALID_CAPTCHA", LOGIN_APIS_COLLECTION_ID);
        templateCollectionMap.put("USER_ENUM_REDIRECT_PAGE", LOGIN_APIS_COLLECTION_ID);
    }

    public void setTestCategory(TestCategory testCategory) {
        this.testCategory = testCategory;
    }

    public Map<String, Set<String>> getSubCategoryVsMissingConfigsMap() {
        return subCategoryVsMissingConfigsMap;
    }

    public Map<String, Integer> getTemplateGroupsMap() {
        return templateGroupsMap;
    }

}
