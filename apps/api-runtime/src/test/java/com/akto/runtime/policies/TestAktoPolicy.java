package com.akto.runtime.policies;

import com.akto.MongoBasedTest;
import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.type.*;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.APICatalogSync;
import com.akto.runtime.merge.MergeSimilarUrls;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
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

    @Test
    public void testRestart() throws Exception {
        dropCollections();
        initialiseAccountSettings();

        HttpResponseParams hrp1 = generateHttpResponseParams("url1", URLMethods.Method.GET, 0,new ArrayList<>(),false) ;
        HttpResponseParams hrp2 = generateHttpResponseParams("url2", URLMethods.Method.GET, 0,new ArrayList<>(),false) ;
        HttpResponseParams hrp3 = generateHttpResponseParams("url3", URLMethods.Method.GET, 0,new ArrayList<>(),false) ;

        HttpResponseParams hrpMerge1 = generateHttpResponseParams("/api/books/1", URLMethods.Method.GET, 0,new ArrayList<>(),false) ;
        HttpResponseParams hrpMerge2 = generateHttpResponseParams("/api/books/2", URLMethods.Method.GET, 0,new ArrayList<>(),false) ;

        List<HttpResponseParams> responseParams = Arrays.asList(hrp1, hrp2, hrp3, hrpMerge1, hrpMerge2);

        HttpCallParser httpCallParser = new HttpCallParser("user", 1, 1,1, true);
        httpCallParser.syncFunction(responseParams, false, true);
        httpCallParser.apiCatalogSync.syncWithDB(false, true);

        List<ApiInfo> apiInfoList = ApiInfoDao.instance.findAll(Filters.eq("_id.apiCollectionId", 0));
        Assertions.assertEquals(5,apiInfoList.size());
        List<FilterSampleData> filterSampleDataList = FilterSampleDataDao.instance.findAll(new BasicDBObject());
        Assertions.assertEquals(0, filterSampleDataList.size());

        // restart server means new httpCallParser and aktoPolicy
        HttpCallParser httpCallParser1 = new HttpCallParser("user", 1, 1,1, true);

        apiInfoList = ApiInfoDao.instance.findAll(Filters.eq("_id.apiCollectionId", 0));
        Assertions.assertEquals(5,apiInfoList.size());

        httpCallParser1.syncFunction(responseParams.subList(0,1), false, true);
        httpCallParser1.apiCatalogSync.syncWithDB(false, true);

        apiInfoList = ApiInfoDao.instance.findAll(Filters.eq("_id.apiCollectionId", 0));
        Assertions.assertEquals(5,apiInfoList.size());
        filterSampleDataList = FilterSampleDataDao.instance.findAll(Filters.eq("_id.apiInfoKey.apiCollectionId", 0));
        Assertions.assertEquals(0, filterSampleDataList.size());
    }


    @Test
    public void test1() throws Exception {
        dropCollections();
        initialiseAccountSettings();

        APICatalogSync apiCatalogSync = new APICatalogSync("", 10, true);

        URLStatic urlStatic1 = new URLStatic("/api/books", URLMethods.Method.GET);
        HttpResponseParams hrp1 = generateHttpResponseParams(urlStatic1.getUrl(), urlStatic1.getMethod(),0, Collections.singletonList(ApiInfo.AuthType.JWT), true) ;
        URLStatic urlStatic2 = new URLStatic("/api/books", URLMethods.Method.POST);
        HttpResponseParams hrp2 = generateHttpResponseParams(urlStatic2.getUrl(), urlStatic2.getMethod(),0,Collections.singletonList(ApiInfo.AuthType.UNAUTHENTICATED), true);
        URLStatic urlStatic3 = new URLStatic("/api/cars", URLMethods.Method.GET);
        HttpResponseParams hrp3 = generateHttpResponseParams(urlStatic3.getUrl(), urlStatic3.getMethod(),1,Collections.singletonList(ApiInfo.AuthType.JWT), false);
        URLStatic urlStatic4 = new URLStatic("/api/toys", URLMethods.Method.PUT);
        HttpResponseParams hrp4 = generateHttpResponseParams(urlStatic4.getUrl(), urlStatic4.getMethod(),0,Collections.singletonList(ApiInfo.AuthType.JWT), false);
        URLStatic urlStatic5 = new URLStatic("/api/new/books", URLMethods.Method.GET);
        HttpResponseParams hrp5 = generateHttpResponseParams(urlStatic5.getUrl(), urlStatic5.getMethod(),0,Collections.singletonList(ApiInfo.AuthType.JWT), false);
        URLStatic urlStatic6 = new URLStatic("/api/toys/1", URLMethods.Method.PUT);
        HttpResponseParams hrp6 = generateHttpResponseParams(urlStatic6.getUrl(), urlStatic6.getMethod(),0,Collections.singletonList(ApiInfo.AuthType.UNAUTHENTICATED), false);
        URLStatic urlStatic7 = new URLStatic("/api/toys/2", URLMethods.Method.PUT);
        HttpResponseParams hrp7 = generateHttpResponseParams(urlStatic7.getUrl(), urlStatic7.getMethod(),0,Collections.singletonList(ApiInfo.AuthType.UNAUTHENTICATED), false);

        // sending a couple of requests to akto policy initially
        List<HttpResponseParams> hrpList = Arrays.asList(hrp1, hrp2, hrp3, hrp4, hrp5, hrp6, hrp7);
        apiCatalogSync.aktoPolicyNew.main(hrpList);

        apiCatalogSync.aktoPolicyNew.main(Collections.singletonList(hrp1));
        apiCatalogSync.syncWithDB(true, true);

        List<ApiInfo> apiInfoList = ApiInfoDao.instance.findAll(new BasicDBObject());
        assertEquals(hrpList.size(), apiInfoList.size());

        List<FilterSampleData> filterSampleDataList = FilterSampleDataDao.instance.findAll(new BasicDBObject());
        assertEquals(0, filterSampleDataList.size());

        // created a dummy AktoPolicy to use buildFromDb without touching the original AktoPolicy
        AktoPolicyNew dummyAktoPolicy = new AktoPolicyNew();
        dummyAktoPolicy.buildFromDb(true);

        Assertions.assertEquals(dummyAktoPolicy.getApiInfoCatalogMap().keySet().size(), 2);
        Assertions.assertEquals(dummyAktoPolicy.getApiInfoCatalogMap().get(0).getStrictURLToMethods().size(), hrpList.size() -1 ); // because 1 hrp is of different collection
        Assertions.assertEquals(dummyAktoPolicy.getApiInfoCatalogMap().get(0).getTemplateURLToMethods().size(), 0 );
        Assertions.assertEquals(dummyAktoPolicy.getApiInfoCatalogMap().get(1).getStrictURLToMethods().size(),1);
        Assertions.assertEquals(dummyAktoPolicy.getApiInfoCatalogMap().get(1).getTemplateURLToMethods().size(),0);


        // next time data comes some urls got merged
        String mergedUrl = "/api/toys/INTEGER";
        Set<String> toMergeUrls = new HashSet<>(Arrays.asList("/api/toys/1", "/api/toys/2"));
        MergeSimilarUrls.mergeApiInfo(mergedUrl, toMergeUrls, 0, URLMethods.Method.PUT);
        MergeSimilarUrls.mergeFilterSampleData(mergedUrl, toMergeUrls, 0, URLMethods.Method.PUT);

        apiCatalogSync.aktoPolicyNew.buildFromDb(true);
        apiInfoList = ApiInfoDao.instance.findAll(new BasicDBObject());
        assertEquals(hrpList.size() - 1, apiInfoList.size()); // 2 urls got merged to 1
        filterSampleDataList = FilterSampleDataDao.instance.findAll(new BasicDBObject());
        assertEquals(0, filterSampleDataList.size()); // 2 urls got merged to 1
        dummyAktoPolicy.buildFromDb(true);
        Assertions.assertEquals(dummyAktoPolicy.getApiInfoCatalogMap().get(0).getTemplateURLToMethods().size(), 1 );


        URLStatic urlStatic8 = new URLStatic("/api/toys/3", URLMethods.Method.PUT);
        HttpResponseParams hrp8 = generateHttpResponseParams(urlStatic8.getUrl(), urlStatic8.getMethod(),0, Collections.singletonList(ApiInfo.AuthType.UNAUTHENTICATED),false) ;
        apiCatalogSync.aktoPolicyNew.main(Collections.singletonList(hrp8));
        apiCatalogSync.syncWithDB(true, true);

        apiInfoList = ApiInfoDao.instance.findAll(new BasicDBObject());
        assertEquals(hrpList.size() - 1, apiInfoList.size()); // 2 urls got merged to 1
        filterSampleDataList = FilterSampleDataDao.instance.findAll(new BasicDBObject());
        assertEquals(0, filterSampleDataList.size()); // 2 urls got merged to 1

        FilterSampleData filterSampleData = FilterSampleDataDao.instance.findOne(
                Filters.and(
                        Filters.eq("_id.apiInfoKey.apiCollectionId", 0),
                        Filters.eq("_id.apiInfoKey.url", "/api/toys/INTEGER"),
                        Filters.eq("_id.apiInfoKey.method", urlStatic6.getMethod().name())
                )
        );

        assertNull(filterSampleData);

        HttpResponseParams hrp10 = generateHttpResponseParams(urlStatic6.getUrl(), urlStatic6.getMethod(),0,Collections.singletonList(ApiInfo.AuthType.JWT), false);

        apiCatalogSync.aktoPolicyNew.main(Collections.singletonList(hrp10));
        apiCatalogSync.syncWithDB(true, true);

        ApiInfo apiInfo = ApiInfoDao.instance.findOne(
                Filters.and(
                        Filters.eq("_id.apiCollectionId", 0),
                        Filters.eq("_id.url", "/api/toys/INTEGER"),
                        Filters.eq("_id.method", urlStatic6.getMethod().name())
                )
        );

        assertEquals(2, apiInfo.getAllAuthTypesFound().size());
        assertTrue(apiInfo.getAllAuthTypesFound().contains(new HashSet<>(Collections.singletonList(ApiInfo.AuthType.UNAUTHENTICATED))));
        assertTrue(apiInfo.getAllAuthTypesFound().contains(new HashSet<>(Collections.singletonList(ApiInfo.AuthType.JWT))));

    }


    private static void dropCollections() {
        AccountSettingsDao.instance.getMCollection().drop();
        FilterSampleDataDao.instance.getMCollection().drop();
        ApiInfoDao.instance.getMCollection().drop();
    }

    private static void initialiseAccountSettings() {
        AccountSettings accountSettings = new AccountSettings(0, Collections.singletonList("172.31.0.0/16"), false, AccountSettings.SetupType.STAGING);
        AccountSettingsDao.instance.insertOne(accountSettings);
        RuntimeFilterDao.instance.initialiseFilters();
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