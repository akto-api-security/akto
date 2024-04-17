package com.akto.runtime.policies;

import com.akto.MongoBasedTest;
import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.type.*;
import com.akto.runtime.APICatalogSync;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.util.*;

import static org.junit.Assert.*;

public class TestAktoPolicy extends MongoBasedTest {
    private static int currAccountId = 0;

    @Before
    public void changeAccountId() {
        Context.accountId.set(currAccountId);
        currAccountId += 1;
    }

    public HttpResponseParams generateHttpResponseParams(String url, URLMethods.Method method, int aci,
                                                         List<ApiInfo.AuthType> authTypes, boolean isPublic) {
        HttpResponseParams hrp = new HttpResponseParams();
        hrp.requestParams = new HttpRequestParams();
        hrp.requestParams.setApiCollectionId(aci);
        hrp.requestParams.url = url;
        hrp.requestParams.method = method.name();
        hrp.statusCode = 200;
        hrp.requestParams.setHeaders(new HashMap<>());
        hrp.requestParams.getHeaders().put("content-type", Collections.singletonList("application/json"));
        hrp.setOrig("BBB");
        hrp.accountId = "0";

        // authType
        for (ApiInfo.AuthType authType: authTypes) {
            switch (authType) {
                case UNAUTHENTICATED:
                    break;
                case BASIC:
                    hrp.requestParams.getHeaders().put("authorization", Collections.singletonList("Basic somerandom"));
                    break;
                case AUTHORIZATION_HEADER:
                    hrp.requestParams.getHeaders().put("authorization", Collections.singletonList("somerandom"));
                    break;
                case JWT:
                    hrp.requestParams.getHeaders().put("akto-token123", Collections.singletonList("eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoicmVmcmVzaFRva2VuIiwic2lnbmVkVXAiOiJ0cnVlIiwidXNlcm5hbWUiOiJhbmtpdGFAZ21haWwuY29tIiwiaWF0IjoxNjM0OTcxMTMxLCJleHAiOjE2MzUwNTc1MzF9.Ph4Jv-fdggwvnbdVViD9BWUReYL0dVfVGuMRz4d2oZNnYzWV0JCmjpB68p6k0yyPPua_yagIWVZf_oYH9PUgS7EuaPYR-Vg6uxKR1HuXRA6wb8Xf4RPoFjJYkhWoYmv38V9Cz2My9U85wgGHGZXEufu8ubrFmIfOP6-A39M4meNGw48f5oOz8V337SX45uPc6jE0EfmM4l9EbqFFCF0lRXbMMzn-ijsyXxLkI5npWnqtW3PAHC2Rs3FV40tkRqHYF-WM6SzyHLBh6bVeyeOsFRBoEjv-zFh8yrYnT6OvCa6jII2A6uj4MQ2k11-5bDBhfVPVc4hEQz37H_DWwtf23g"));
                    break;
                case API_TOKEN:
                    break;
                case BEARER:
                    hrp.requestParams.getHeaders().put("authorization", Collections.singletonList("Bearer somerandom"));
                    break;
            }
        }

        // public/private
        if (isPublic) {
            hrp.requestParams.getHeaders().put(ApiAccessTypePolicy.X_FORWARDED_FOR, Arrays.asList("3.109.56.64", "118.185.162.194"));
        } else {
            hrp.requestParams.getHeaders().put(ApiAccessTypePolicy.X_FORWARDED_FOR, Arrays.asList("172.31.8.188", "172.31.255.255"));
        }
        return hrp;
    }

    private static void dropCollections() {
        AccountSettingsDao.instance.getMCollection().drop();
        FilterSampleDataDao.instance.getMCollection().drop();
        ApiInfoDao.instance.getMCollection().drop();
    }

    @Test
    public void testFilterSampleDataGetId() {
        dropCollections();

        FilterSampleData filterSampleData1 = new FilterSampleData(new ApiInfo.ApiInfoKey(0,"/api/books", URLMethods.Method.GET), 0);
        FilterSampleData filterSampleData2 = new FilterSampleData(new ApiInfo.ApiInfoKey(0,"/api/books", URLMethods.Method.POST), 0);
        for (String x: Arrays.asList("1", "2")) {
            filterSampleData2.getSamples().add(x);
        }
        FilterSampleData filterSampleData3 = new FilterSampleData(new ApiInfo.ApiInfoKey(0,"/api/toys", URLMethods.Method.PUT), 1);
        for (String x: Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")) {
            filterSampleData3.getSamples().add(x);
        }

        FilterSampleDataDao.instance.insertMany(Arrays.asList(filterSampleData1, filterSampleData2, filterSampleData3));
        List<ApiInfo.ApiInfoKey> filterSampleDataIdList = FilterSampleDataDao.instance.getApiInfoKeys();

        Assertions.assertEquals(3, filterSampleDataIdList.size());
        Assertions.assertNotNull(filterSampleDataIdList.get(0));
        Assertions.assertNotNull(filterSampleDataIdList.get(1));
        Assertions.assertNotNull(filterSampleDataIdList.get(2));
    }

    @Test
    public void testConstructorInitialisation() {
        dropCollections();

        ApiInfo apiInfo = new ApiInfo(0, "/url1", URLMethods.Method.GET);
        ApiInfoDao.instance.insertOne(apiInfo);

        FilterSampleData filterSampleData = new FilterSampleData(new ApiInfo.ApiInfoKey(0, "/url1", URLMethods.Method.GET), 0);
        FilterSampleDataDao.instance.insertOne(filterSampleData);

        APICatalogSync apiCatalogSync = new APICatalogSync("", 0, true);
        Map<Integer, APICatalog> dbState = new HashMap<>();
        Map<URLStatic, RequestTemplate> strictURLToMethods = new HashMap<>();
        strictURLToMethods.put(new URLStatic("/url1", URLMethods.Method.GET), new RequestTemplate());
        Map<URLTemplate,RequestTemplate> templateURLToMethods = new HashMap<>();
        dbState.put(0, new APICatalog(0, strictURLToMethods, templateURLToMethods));

        apiCatalogSync.dbState = dbState;

        AktoPolicyNew aktoPolicy = apiCatalogSync.aktoPolicyNew;
        Map<Integer, ApiInfoCatalog> apiInfoCatalogMap = aktoPolicy.getApiInfoCatalogMap();

        Map<URLStatic, PolicyCatalog> strictPolicyMap = apiInfoCatalogMap.get(0).getStrictURLToMethods();
        assertEquals(strictPolicyMap.size(), 1);
        assertNotNull(strictPolicyMap.get(new URLStatic("/url1", URLMethods.Method.GET)));

        Map<URLTemplate, PolicyCatalog> templatePolicyCatalogMap = apiInfoCatalogMap.get(0).getTemplateURLToMethods();
        assertEquals(templatePolicyCatalogMap.size(), 0);
    }
}