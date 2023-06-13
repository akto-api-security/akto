package com.akto.dao.demo;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.demo.VulnerableRequestForTemplate;
import com.akto.util.enums.MongoDBEnums;

import java.util.*;

public class VulnerableRequestForTemplateDao extends AccountsContextDao<VulnerableRequestForTemplate> {
    private static final Map<String, List<String>> apiVsTemplateMap = new HashMap<>();
    static {
        apiVsTemplateMap.put("GET https://juiceshop.akto.io/rest/products/search", Collections.singletonList("ADD_DELETE_METHOD_IN_PARAMETER"));
        apiVsTemplateMap.put("GET https://juiceshop.akto.io/rest/captcha/", Arrays.asList("ADD_POST_METHOD_IN_PARAMETER", "ADD_PUT_METHOD_IN_PARAMETER", "REMOVE_TOKENS"));
        apiVsTemplateMap.put("GET https://juiceshop.akto.io/api/Challenges/", Collections.singletonList("ADD_POST_METHOD_IN_PARAMETER"));
        apiVsTemplateMap.put("GET https://juiceshop.akto.io/", Arrays.asList("CHANGE_METHOD_TO_DELETE","CHANGE_METHOD_TO_PATCH","CHANGE_METHOD_TO_POST","CHANGE_METHOD_TO_PUT"));
        apiVsTemplateMap.put("GET https://juiceshop.akto.io/rest/products/INTEGER/reviews", Arrays.asList("CHANGE_METHOD_TO_PUT","REMOVE_TOKENS"));
        apiVsTemplateMap.put("PATCH https://juiceshop.akto.io/rest/products/reviews", Arrays.asList("JWT_NONE_ALGO", "REPLACE_AUTH_TOKEN"));
        apiVsTemplateMap.put("GET https://juiceshop.akto.io/socket.io/", Arrays.asList("ADD_DELETE_METHOD_OVERRIDE_HEADERS", "ADD_PATCH_METHOD_OVERRIDE_HEADERS", "ADD_PUT_METHOD_OVERRIDE_HEADERS"));
    }

    public static Map<String, List<String>> getApiVsTemplateMap() {
        return apiVsTemplateMap;
    }
    @Override
    public String getCollName() {
        return MongoDBEnums.Collection.DEMO_REQUEST_FOR_TEMPLATE.getCollectionName();
    }

    public static final VulnerableRequestForTemplateDao instance = new VulnerableRequestForTemplateDao();

    private VulnerableRequestForTemplateDao() {
        super();
    }

    @Override
    public Class<VulnerableRequestForTemplate> getClassT() {
        return VulnerableRequestForTemplate.class;
    }
}