package com.akto.runtime.policies;

import com.akto.DaoInit;
import com.akto.MongoBasedTest;
import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.type.*;
import com.akto.runtime.APICatalogSync;
import com.mongodb.BasicDBObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.util.*;

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
        hrp.setOrig("BBB");

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
    public void test1() throws Exception {
        AccountSettings accountSettings = new AccountSettings(0, Collections.singletonList("172.31.0.0/16"));
        AccountSettingsDao.instance.insertOne(accountSettings);
        RuntimeFilterDao.instance.initialiseFilters();

        APICatalogSync apiCatalogSync = new APICatalogSync("", 10);
        AktoPolicy aktoPolicy = new AktoPolicy(apiCatalogSync);

        URLStatic urlStatic1 = new URLStatic("/api/books", URLMethods.Method.GET);
        HttpResponseParams hrp1 = generateHttpResponseParams(urlStatic1.getUrl(), urlStatic1.getMethod(),0, Collections.singletonList(ApiInfo.AuthType.JWT), true) ;
        URLStatic urlStatic2 = new URLStatic("/api/books", URLMethods.Method.POST);
        HttpResponseParams hrp2 = generateHttpResponseParams(urlStatic2.getUrl(), urlStatic2.getMethod(),0,Collections.singletonList(ApiInfo.AuthType.JWT), true);
        URLStatic urlStatic3 = new URLStatic("/api/cars", URLMethods.Method.GET);
        HttpResponseParams hrp3 = generateHttpResponseParams(urlStatic3.getUrl(), urlStatic3.getMethod(),1,Collections.singletonList(ApiInfo.AuthType.JWT), false);
        URLStatic urlStatic4 = new URLStatic("/api/toys", URLMethods.Method.PUT);
        HttpResponseParams hrp4 = generateHttpResponseParams(urlStatic4.getUrl(), urlStatic4.getMethod(),0,Collections.singletonList(ApiInfo.AuthType.JWT), false);
        URLStatic urlStatic5 = new URLStatic("/api/new/books", URLMethods.Method.GET);
        HttpResponseParams hrp5 = generateHttpResponseParams(urlStatic5.getUrl(), urlStatic5.getMethod(),0,Collections.singletonList(ApiInfo.AuthType.JWT), false);
        URLStatic urlStatic6 = new URLStatic("/api/toys/1", URLMethods.Method.PUT);
        HttpResponseParams hrp6 = generateHttpResponseParams(urlStatic6.getUrl(), urlStatic6.getMethod(),0,Collections.singletonList(ApiInfo.AuthType.JWT), false);
        URLStatic urlStatic9 = new URLStatic("/api/something", URLMethods.Method.GET);
        HttpResponseParams hrp9 = generateHttpResponseParams(urlStatic9.getUrl(), urlStatic9.getMethod(),0,Collections.singletonList(ApiInfo.AuthType.BEARER), false);
        URLStatic urlStatic10 = new URLStatic("/api/something/1", URLMethods.Method.GET);
        HttpResponseParams hrp10 = generateHttpResponseParams(urlStatic10.getUrl(), urlStatic10.getMethod(),0,Collections.singletonList(ApiInfo.AuthType.BASIC), false);

        // sending a couple of requests to akto policy initially
        List<HttpResponseParams> hrpList = Arrays.asList(hrp1, hrp2, hrp3, hrp4, hrp5, hrp6);
        aktoPolicy.main(hrpList, null);

        // since no data initially so will be stored in reserves
        Assertions.assertEquals(aktoPolicy.getReserveApiInfoMap().keySet().size(), hrpList.size());
        Assertions.assertEquals(aktoPolicy.getReserveFilterSampleDataMap().keySet().size(), 0);


        // After some threshold is reached httpCallParser will sync with db and send apiCatalogSync to aktoPolicy
        apiCatalogSync.delta = new HashMap<>();
        Map<URLStatic, RequestTemplate> strictUrlToMethods = new HashMap<>();
        strictUrlToMethods.put(urlStatic1, null);
        strictUrlToMethods.put(urlStatic2, null);
        strictUrlToMethods.put(urlStatic4, null);
        strictUrlToMethods.put(urlStatic5, null);
        strictUrlToMethods.put(urlStatic6, null);

        // urlStatic3 has different apiCollectionId
        Map<URLStatic, RequestTemplate> strictUrlToMethods1 = new HashMap<>();
        strictUrlToMethods1.put(urlStatic3, null);


        APICatalog apiCatalog = new APICatalog(0,strictUrlToMethods, new HashMap<>());
        APICatalog apiCatalog1 = new APICatalog(0,strictUrlToMethods1, new HashMap<>());
        apiCatalogSync.delta.put(0, apiCatalog);
        apiCatalogSync.delta.put(1, apiCatalog1);

        aktoPolicy.main(Collections.singletonList(hrp1), apiCatalogSync);
        Assertions.assertEquals(aktoPolicy.getReserveApiInfoMap().keySet().size(), 0);
        Assertions.assertEquals(aktoPolicy.getApiInfoCatalogMap().keySet().size(), 2);
        Assertions.assertEquals(aktoPolicy.getApiInfoCatalogMap().get(0).getStrictURLToMethods().size(), hrpList.size() -1 ); // because one hrp is of different collection
        Assertions.assertEquals(aktoPolicy.getApiInfoCatalogMap().get(0).getTemplateURLToMethods().size(), 0 );
        Assertions.assertEquals(aktoPolicy.getApiInfoCatalogMap().get(1).getStrictURLToMethods().size(),1);
        Assertions.assertEquals(aktoPolicy.getApiInfoCatalogMap().get(1).getTemplateURLToMethods().size(),0);

        // nothing in db because in this cycle it moves from reserve to apiCatalogMap
        List<ApiInfo> apiInfoList = ApiInfoDao.instance.findAll(new BasicDBObject());
        List<FilterSampleData> filterSampleDataList = FilterSampleDataDao.instance.findAll(new BasicDBObject());
        Assertions.assertEquals(apiInfoList.size(), 0);
        Assertions.assertEquals(filterSampleDataList.size(), 0);

        // now it moves to db
        aktoPolicy.main(Collections.singletonList(hrp1), apiCatalogSync);

        apiInfoList = ApiInfoDao.instance.findAll(new BasicDBObject());
        Assertions.assertEquals(apiInfoList.size(), hrpList.size());
        filterSampleDataList = FilterSampleDataDao.instance.findAll(new BasicDBObject());
        Assertions.assertEquals(filterSampleDataList.size(), 0);


        URLStatic urlStatic7 = new URLStatic("/api/toys/2", URLMethods.Method.PUT);
        HttpResponseParams hrp7 = generateHttpResponseParams(urlStatic7.getUrl(), urlStatic7.getMethod(),0, Collections.singletonList(ApiInfo.AuthType.UNAUTHENTICATED),true);

        aktoPolicy.main(Collections.singletonList(hrp7), null);
        Assertions.assertEquals(aktoPolicy.getReserveApiInfoMap().keySet().size(), 1);
        Assertions.assertEquals(aktoPolicy.getReserveFilterSampleDataMap().keySet().size(), 1);

        // next cycle api/toys/INTEGER merge
        apiCatalogSync.delta.get(0).getStrictURLToMethods().remove(urlStatic6);
        apiCatalogSync.delta.get(0).getStrictURLToMethods().put(urlStatic9, null);
        apiCatalogSync.delta.get(0).getStrictURLToMethods().put(urlStatic10, null);
        apiCatalogSync.delta.get(0).getTemplateURLToMethods().put(APICatalogSync.createUrlTemplate("api/toys/INTEGER", urlStatic6.getMethod()), null);

        HttpResponseParams hrp8 = generateHttpResponseParams(urlStatic1.getUrl(), urlStatic1.getMethod(),0, Collections.singletonList(ApiInfo.AuthType.BEARER),false) ;

        aktoPolicy.main(Arrays.asList(hrp8,hrp9,hrp10), apiCatalogSync);
        Assertions.assertEquals(aktoPolicy.getReserveApiInfoMap().keySet().size(), 0);
        Assertions.assertEquals(aktoPolicy.getReserveFilterSampleDataMap().keySet().size(), 0);
        Assertions.assertEquals(aktoPolicy.getApiInfoCatalogMap().get(0).getDeletedInfo().size(), 1 );
        Assertions.assertEquals(aktoPolicy.getApiInfoCatalogMap().get(0).getDeletedInfo().get(0), new ApiInfo.ApiInfoKey(0, urlStatic6.getUrl(), urlStatic6.getMethod()));
        Assertions.assertEquals(aktoPolicy.getApiInfoCatalogMap().get(0).getStrictURLToMethods().size(), 6 ); // because one hrp is of different collection
        Assertions.assertEquals(aktoPolicy.getApiInfoCatalogMap().get(0).getTemplateURLToMethods().size(), 1 );

        System.out.println("***");
        System.out.println(aktoPolicy.getApiInfoCatalogMap().get(0).getTemplateURLToMethods().get(APICatalogSync.createUrlTemplate("api/toys/INTEGER", urlStatic6.getMethod())).getApiInfo());
        System.out.println(aktoPolicy.getApiInfoCatalogMap().get(0).getTemplateURLToMethods().get(APICatalogSync.createUrlTemplate("api/toys/INTEGER", urlStatic6.getMethod())).getFilterSampleDataMap());
        System.out.println("***");

        // delete will happen in next cycle
        apiInfoList = ApiInfoDao.instance.findAll(new BasicDBObject());
        Assertions.assertEquals(apiInfoList.size(), hrpList.size());
        filterSampleDataList = FilterSampleDataDao.instance.findAll(new BasicDBObject());
        Assertions.assertEquals(filterSampleDataList.size(), 0);

        aktoPolicy.main(Collections.singletonList(hrp1), apiCatalogSync);
        apiInfoList = ApiInfoDao.instance.findAll(new BasicDBObject());
        Assertions.assertEquals(apiInfoList.size(), hrpList.size()+2);
        filterSampleDataList = FilterSampleDataDao.instance.findAll(new BasicDBObject());
        Assertions.assertEquals(filterSampleDataList.size(), 1);

        Map<ApiInfo.ApiInfoKey, ApiInfo> apiInfoMap = new HashMap<>();
        for (ApiInfo apiInfo: apiInfoList) {
            apiInfoMap.put(apiInfo.getId(), apiInfo);
        }

        ApiInfo.ApiInfoKey toysIntegerApiInfoKey = new ApiInfo.ApiInfoKey(0,"api/toys/INTEGER", urlStatic6.getMethod());
        Assertions.assertTrue(apiInfoMap.get(toysIntegerApiInfoKey).getApiAccessTypes().containsAll(Arrays.asList(ApiInfo.ApiAccessType.PRIVATE, ApiInfo.ApiAccessType.PUBLIC)));
        Set<ApiInfo.AuthType> authTypes = new HashSet<>();
        authTypes.add(ApiInfo.AuthType.UNAUTHENTICATED);
        Assertions.assertTrue(apiInfoMap.get(toysIntegerApiInfoKey).getAllAuthTypesFound().contains(authTypes));
        authTypes = new HashSet<>();
        authTypes.add(ApiInfo.AuthType.JWT);
        Assertions.assertTrue(apiInfoMap.get(toysIntegerApiInfoKey).getAllAuthTypesFound().contains(authTypes));

        ApiInfo.ApiInfoKey booksGet = new ApiInfo.ApiInfoKey(0, urlStatic1.getUrl(), urlStatic1.getMethod());
        Assertions.assertTrue(apiInfoMap.get(booksGet).getApiAccessTypes().containsAll(Arrays.asList(ApiInfo.ApiAccessType.PRIVATE, ApiInfo.ApiAccessType.PUBLIC)));
        authTypes = new HashSet<>();
        authTypes.add(ApiInfo.AuthType.BEARER);
        Assertions.assertTrue(apiInfoMap.get(booksGet).getAllAuthTypesFound().contains(authTypes));
        authTypes = new HashSet<>();
        authTypes.add(ApiInfo.AuthType.JWT);
        Assertions.assertTrue(apiInfoMap.get(booksGet).getAllAuthTypesFound().contains(authTypes));

        HttpResponseParams hrp11 = generateHttpResponseParams(urlStatic10.getUrl(), urlStatic10.getMethod(),0, Collections.singletonList(ApiInfo.AuthType.UNAUTHENTICATED),true) ;

        apiCatalogSync.delta.get(0).getStrictURLToMethods().remove(urlStatic10);
        apiCatalogSync.delta.get(0).getTemplateURLToMethods().put(APICatalogSync.createUrlTemplate("api/something/INTEGER", urlStatic10.getMethod()), null);

        aktoPolicy.main(Arrays.asList(hrp8,hrp9,hrp10,hrp11), apiCatalogSync);
        Assertions.assertEquals(aktoPolicy.getReserveApiInfoMap().keySet().size(), 0);
        Assertions.assertEquals(aktoPolicy.getApiInfoCatalogMap().get(0).getDeletedInfo().size(), 1 );
        Assertions.assertEquals(aktoPolicy.getApiInfoCatalogMap().get(0).getDeletedInfo().get(0), new ApiInfo.ApiInfoKey(0, urlStatic10.getUrl(), urlStatic10.getMethod()));
        Assertions.assertEquals(aktoPolicy.getApiInfoCatalogMap().get(0).getStrictURLToMethods().size(), 5 ); // because one hrp is of different collection
        Assertions.assertEquals(aktoPolicy.getApiInfoCatalogMap().get(0).getTemplateURLToMethods().size(), 2 );

        aktoPolicy.main(Collections.singletonList(hrp1), apiCatalogSync);

        apiInfoMap = new HashMap<>();
        apiInfoList = ApiInfoDao.instance.findAll(new BasicDBObject());
        for (ApiInfo apiInfo: apiInfoList) {
            apiInfoMap.put(apiInfo.getId(), apiInfo);
        }

        ApiInfo.ApiInfoKey somethingIntegerApiInfoKey = new ApiInfo.ApiInfoKey(0,"api/something/INTEGER", urlStatic10.getMethod());
        Assertions.assertTrue(apiInfoMap.get(somethingIntegerApiInfoKey).getApiAccessTypes().containsAll(Arrays.asList(ApiInfo.ApiAccessType.PRIVATE, ApiInfo.ApiAccessType.PUBLIC)));
        authTypes = new HashSet<>();
        authTypes.add(ApiInfo.AuthType.UNAUTHENTICATED);
        Assertions.assertTrue(apiInfoMap.get(somethingIntegerApiInfoKey).getAllAuthTypesFound().contains(authTypes));
        authTypes = new HashSet<>();
        authTypes.add(ApiInfo.AuthType.BASIC);
        Assertions.assertTrue(apiInfoMap.get(somethingIntegerApiInfoKey).getAllAuthTypesFound().contains(authTypes));

        filterSampleDataList = FilterSampleDataDao.instance.findAll(new BasicDBObject());
        Assertions.assertEquals(filterSampleDataList.size(), 2);
    }

    @Test
    public void testFilterSampleDataGetId() {
        FilterSampleData filterSampleData1 = new FilterSampleData(new ApiInfo.ApiInfoKey(0,"/api/books", URLMethods.Method.GET), 0);
        FilterSampleData filterSampleDataRemove = new FilterSampleData(new ApiInfo.ApiInfoKey(0,"/api/delete", URLMethods.Method.GET), 0);
        FilterSampleData filterSampleData2 = new FilterSampleData(new ApiInfo.ApiInfoKey(0,"/api/books", URLMethods.Method.POST), 0);
        for (String x: Arrays.asList("1", "2")) {
            filterSampleData2.getSamples().add(x);
        }
        FilterSampleData filterSampleData3 = new FilterSampleData(new ApiInfo.ApiInfoKey(0,"/api/toys", URLMethods.Method.PUT), 1);
        for (String x: Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")) {
            filterSampleData3.getSamples().add(x);
        }

        FilterSampleDataDao.instance.insertMany(Arrays.asList(filterSampleData1, filterSampleData2, filterSampleData3, filterSampleDataRemove));
        List<ApiInfo.ApiInfoKey> filterSampleDataIdList = FilterSampleDataDao.instance.getApiInfoKeys();

        Assertions.assertEquals(filterSampleDataIdList.size(),4);
        Assertions.assertNotNull(filterSampleDataIdList.get(0));
        Assertions.assertNotNull(filterSampleDataIdList.get(1));
        Assertions.assertNotNull(filterSampleDataIdList.get(2));
        Assertions.assertNotNull(filterSampleDataIdList.get(3));
    }
}
