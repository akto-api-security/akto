package com.akto.runtime.policies;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.FilterSampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.FilterSampleData;
import com.akto.dto.type.URLMethods;
import com.akto.types.CappedList;
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


    @Test
    public void syncWithDbInitialising() {
        Set<String> urls = new HashSet<>();
        urls.add("/api/books GET");
        urls.add("/api/books POST");
        urls.add("/api/cars GET");
        urls.add("/api/toys PUT");
        urls.add("/api/toys/INTEGER PUT");
        ApiCollection apiCollection = new ApiCollection(0,"", 0, urls);
        ApiCollectionsDao.instance.insertOne(apiCollection);

        List<ApiInfo> apiInfoList = new ArrayList<>();
        apiInfoList.add(new ApiInfo(0,"/api/asdf", URLMethods.Method.POST));
        apiInfoList.add(new ApiInfo(1,"/api/toys", URLMethods.Method.PUT));
        apiInfoList.add(new ApiInfo(0,"/api/books", URLMethods.Method.GET));
        apiInfoList.add(new ApiInfo(0,"/api/books", URLMethods.Method.POST));
        apiInfoList.add(new ApiInfo(0,"/api/toys/3", URLMethods.Method.PUT));
        ApiInfoDao.instance.insertMany(apiInfoList);

        List<FilterSampleData> filterSampleDataList = new ArrayList<>();
        filterSampleDataList.add(new FilterSampleData(new ApiInfo.ApiInfoKey(0,"/api/asdf", URLMethods.Method.POST), 0));
        filterSampleDataList.add(new FilterSampleData(new ApiInfo.ApiInfoKey(1,"/api/toys", URLMethods.Method.PUT), 0));
        filterSampleDataList.add(new FilterSampleData(new ApiInfo.ApiInfoKey(0,"/api/books", URLMethods.Method.GET), 0));
        filterSampleDataList.add(new FilterSampleData(new ApiInfo.ApiInfoKey(0,"/api/books", URLMethods.Method.POST), 0));
        filterSampleDataList.add(new FilterSampleData(new ApiInfo.ApiInfoKey(0,"/api/toys/3", URLMethods.Method.PUT), 0));
        FilterSampleDataDao.instance.insertMany(filterSampleDataList);

        AktoPolicy aktoPolicy = new AktoPolicy();

        Assertions.assertEquals(aktoPolicy.getApiInfoMap().keySet().size(), 5);
        Assertions.assertTrue(aktoPolicy.getApiInfoMap().containsKey(new ApiInfo.ApiInfoKey(0, "/api/books", URLMethods.Method.GET)));
        Assertions.assertTrue(aktoPolicy.getApiInfoMap().containsKey(new ApiInfo.ApiInfoKey(0, "/api/books", URLMethods.Method.POST)));

        Assertions.assertEquals(aktoPolicy.getApiInfoRemoveList().size(), 3);
        Assertions.assertTrue(aktoPolicy.getApiInfoRemoveList().contains(new ApiInfo.ApiInfoKey(0, "/api/asdf", URLMethods.Method.POST)));
        Assertions.assertTrue(aktoPolicy.getApiInfoRemoveList().contains(new ApiInfo.ApiInfoKey(1, "/api/toys", URLMethods.Method.PUT)));
        Assertions.assertTrue(aktoPolicy.getApiInfoRemoveList().contains(new ApiInfo.ApiInfoKey(0, "/api/toys/3", URLMethods.Method.PUT)));

        Assertions.assertEquals(aktoPolicy.getSampleDataRemoveList().size(), 3);
        Assertions.assertTrue(aktoPolicy.getSampleDataRemoveList().contains(new ApiInfo.ApiInfoKey(0, "/api/asdf", URLMethods.Method.POST)));
        Assertions.assertTrue(aktoPolicy.getSampleDataRemoveList().contains(new ApiInfo.ApiInfoKey(1, "/api/toys", URLMethods.Method.PUT)));
        Assertions.assertTrue(aktoPolicy.getSampleDataRemoveList().contains(new ApiInfo.ApiInfoKey(0, "/api/toys/3", URLMethods.Method.PUT)));
    }

    @Test
    public void testUpdates() {
        ApiInfo apiInfo1 = new ApiInfo(0,"/api/books", URLMethods.Method.GET);
        apiInfo1.getApiAccessTypes().add(ApiInfo.ApiAccessType.PRIVATE);
        Map<String, Integer> violations1 = new HashMap<>();
        violations1.put("first", Context.now() - 1000);
        apiInfo1.setViolations(violations1);
        Set<Set<ApiInfo.AuthType>> allAuthTypesFound1 = new HashSet<>();
        Set<ApiInfo.AuthType> a1 = new HashSet<>();
        a1.add(ApiInfo.AuthType.BEARER);
        allAuthTypesFound1.add(a1);
        apiInfo1.setAllAuthTypesFound(allAuthTypesFound1);

        ApiInfo apiInfo2 = new ApiInfo(0,"/api/books", URLMethods.Method.POST);
        apiInfo2.getApiAccessTypes().add(ApiInfo.ApiAccessType.PUBLIC);
        Set<Set<ApiInfo.AuthType>> allAuthTypesFound2 = new HashSet<>();
        Set<ApiInfo.AuthType> b1 = new HashSet<>();
        b1.add(ApiInfo.AuthType.UNAUTHENTICATED);
        allAuthTypesFound2.add(b1);
        apiInfo2.setAllAuthTypesFound(allAuthTypesFound2);

        ApiInfo deleteApiInfo1 = new ApiInfo(0, "/api/delete/1", URLMethods.Method.GET);
        ApiInfo deleteApiInfo2 = new ApiInfo(0, "/api/delete/2", URLMethods.Method.GET);

        ApiInfoDao.instance.insertMany(Arrays.asList(apiInfo1,apiInfo2, deleteApiInfo1, deleteApiInfo2));

        List<ApiInfo> apiInfoListFromDb = ApiInfoDao.instance.findAll(new BasicDBObject());
        Assertions.assertEquals(apiInfoListFromDb.size(), 4);

        ApiInfo newApiInfo = new ApiInfo(0, "/api/new", URLMethods.Method.GET);
        newApiInfo.getApiAccessTypes().add(ApiInfo.ApiAccessType.PUBLIC);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.JWT);
        newApiInfo.getAllAuthTypesFound().add(s);

        Map<ApiInfo.ApiInfoKey, ApiInfo> apiInfoMap = new HashMap<>();

        apiInfo1.getApiAccessTypes().add(ApiInfo.ApiAccessType.PUBLIC);
        apiInfo1.getViolations().put("first", Context.now());
        apiInfo1.getViolations().put("second", Context.now());
        Set<ApiInfo.AuthType> bb1 = new HashSet<>();
        bb1.add(ApiInfo.AuthType.BEARER);
        apiInfo1.getAllAuthTypesFound().add(bb1);

        apiInfo2.getApiAccessTypes().add(ApiInfo.ApiAccessType.PRIVATE);
        apiInfo2.getViolations().put("first", Context.now());
        Set<ApiInfo.AuthType> bb2 = new HashSet<>();
        bb2.add(ApiInfo.AuthType.JWT);
        apiInfo2.getAllAuthTypesFound().add(bb2);

        apiInfoMap.put(apiInfo1.getId(),apiInfo1);
        apiInfoMap.put(apiInfo2.getId(),apiInfo2);
        apiInfoMap.put(newApiInfo.getId(), newApiInfo);

        List<ApiInfo.ApiInfoKey> apiInfoRemoveList = Arrays.asList(deleteApiInfo1.getId(), deleteApiInfo2.getId());

        AktoPolicy aktoPolicy = new AktoPolicy();
        aktoPolicy.setApiInfoMap(apiInfoMap);
        aktoPolicy.setApiInfoRemoveList(apiInfoRemoveList);

        aktoPolicy.syncWithDb(false);

        apiInfoListFromDb = ApiInfoDao.instance.findAll(new BasicDBObject());
        Map<ApiInfo.ApiInfoKey, ApiInfo> latestApiInfoMap = new HashMap<>();
        for (ApiInfo apiInfo: apiInfoListFromDb) {
            latestApiInfoMap.put(apiInfo.getId(), apiInfo);
        }
        Assertions.assertEquals(latestApiInfoMap.keySet().size(), 3);
        Assertions.assertFalse(latestApiInfoMap.containsKey(deleteApiInfo1.getId()));
        Assertions.assertFalse(latestApiInfoMap.containsKey(deleteApiInfo2.getId()));

        ApiInfo latestApiInfo1 = latestApiInfoMap.get(apiInfo1.getId());
        Assertions.assertEquals(latestApiInfo1.getApiAccessTypes().size(),2);
        Assertions.assertEquals(latestApiInfo1.getAllAuthTypesFound().size(), 1);
        Assertions.assertEquals(latestApiInfo1.getViolations().size(), 2);

        ApiInfo latestApiInfo2 = latestApiInfoMap.get(apiInfo2.getId());
        Assertions.assertEquals(latestApiInfo2.getApiAccessTypes().size(),2);
        Assertions.assertEquals(latestApiInfo2.getAllAuthTypesFound().size(), 2);
        Assertions.assertEquals(latestApiInfo2.getViolations().size(), 1);

        ApiInfo latestApiInfo3 = latestApiInfoMap.get(newApiInfo.getId());
        Assertions.assertEquals(latestApiInfo3.getApiAccessTypes().size(),1);
        Assertions.assertEquals(latestApiInfo3.getAllAuthTypesFound().size(), 1);
        Assertions.assertEquals(latestApiInfo3.getViolations().size(), 0);

    }


    @Test
    public void testSampleDataUpdates() {
        FilterSampleData filterSampleData1 = new FilterSampleData(new ApiInfo.ApiInfoKey(0,"/api/books", URLMethods.Method.GET), 0);
        FilterSampleData filterSampleDataRemove = new FilterSampleData(new ApiInfo.ApiInfoKey(0,"/api/delete", URLMethods.Method.GET), 0);
        FilterSampleData filterSampleData2 = new FilterSampleData(new ApiInfo.ApiInfoKey(0,"/api/books", URLMethods.Method.POST), 0);
        filterSampleData2.getSamples().addAll(Arrays.asList("1", "2"));
        FilterSampleData filterSampleData3 = new FilterSampleData(new ApiInfo.ApiInfoKey(0,"/api/toys", URLMethods.Method.PUT), 1);
        filterSampleData3.getSamples().addAll(Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));

        FilterSampleDataDao.instance.insertMany(Arrays.asList(filterSampleData1, filterSampleData2, filterSampleData3, filterSampleDataRemove));
        List<FilterSampleData> filterSampleDataList = FilterSampleDataDao.instance.findAll(new BasicDBObject());
        Assertions.assertEquals(filterSampleDataList.size(),4);


        FilterSampleData filterSampleDataNew = new FilterSampleData(new ApiInfo.ApiInfoKey(0,"/api/new", URLMethods.Method.GET), 0);

        Map<ApiInfo.ApiInfoKey, ApiInfo> apiInfoMap = new HashMap<>();
        apiInfoMap.put(filterSampleData1.getId(),null);
        apiInfoMap.put(filterSampleData2.getId(),null);
        apiInfoMap.put(filterSampleData3.getId(),null);
        apiInfoMap.put(filterSampleDataNew.getId(),null);

        AktoPolicy aktoPolicy = new AktoPolicy();
        aktoPolicy.setApiInfoMap(apiInfoMap);

        Map<ApiInfo.ApiInfoKey, Map<Integer, CappedList<String>> > m = new HashMap<>();

        m.put(filterSampleData1.getId(), new HashMap<>());
        m.get(filterSampleData1.getId()).put(0, new CappedList<>(10, true));
        m.get(filterSampleData1.getId()).put(1, new CappedList<>(10, true));
        m.get(filterSampleData1.getId()).get(1).add("1");
        m.get(filterSampleData1.getId()).get(1).add("2");

        m.put(filterSampleData2.getId(), new HashMap<>());
        m.get(filterSampleData2.getId()).put(0, new CappedList<>(10, true));
        m.get(filterSampleData2.getId()).get(0).add("3");
        m.get(filterSampleData2.getId()).get(0).add("4");

        m.put(filterSampleData3.getId(), new HashMap<>());
        m.get(filterSampleData3.getId()).put(1, new CappedList<>(10, true));
        m.get(filterSampleData3.getId()).get(1).add("11");
        m.get(filterSampleData3.getId()).get(1).add("12");

        m.put(filterSampleDataNew.getId(), new HashMap<>());
        m.get(filterSampleDataNew.getId()).put(0, new CappedList<>(10, true));

        aktoPolicy.setSampleMessages(m);
        aktoPolicy.setSampleDataRemoveList(Collections.singletonList(filterSampleDataRemove.getId()));

        aktoPolicy.syncWithDb(false);

        filterSampleDataList = FilterSampleDataDao.instance.findAll(new BasicDBObject());

        Assertions.assertEquals(filterSampleDataList.size(),5);

        Map<ApiInfo.ApiInfoKey, Map<Integer, List<String>> > n = new HashMap<>();
        for (FilterSampleData f: filterSampleDataList) {
            if (!n.containsKey(f.getId())) {
                n.put(f.getId(), new HashMap<>());
            }
            n.get(f.getId()).put(f.getFilterId(), f.getSamples());
        }

        Assertions.assertEquals(n.get(filterSampleData1.getId()).keySet().size(),2);
        Assertions.assertEquals(n.get(filterSampleData1.getId()).get(0).size(),0);
        Assertions.assertEquals(n.get(filterSampleData1.getId()).get(1).size(),2);

        Assertions.assertEquals(n.get(filterSampleData2.getId()).keySet().size(),1);
        Assertions.assertEquals(n.get(filterSampleData2.getId()).get(0).size(),4);

        Assertions.assertEquals(n.get(filterSampleData3.getId()).keySet().size(),1);
        Assertions.assertEquals(n.get(filterSampleData3.getId()).get(1).size(),10);
        Assertions.assertEquals(n.get(filterSampleData3.getId()).get(1), Arrays.asList("3", "4", "5", "6", "7", "8", "9", "10", "11", "12") );

        Assertions.assertEquals(n.get(filterSampleDataNew.getId()).keySet().size(),1);
        Assertions.assertEquals(n.get(filterSampleDataNew.getId()).get(0).size(), 0);

    }

    @Test
    public void testFilterSampleDataGetId() {
        FilterSampleData filterSampleData1 = new FilterSampleData(new ApiInfo.ApiInfoKey(0,"/api/books", URLMethods.Method.GET), 0);
        FilterSampleData filterSampleDataRemove = new FilterSampleData(new ApiInfo.ApiInfoKey(0,"/api/delete", URLMethods.Method.GET), 0);
        FilterSampleData filterSampleData2 = new FilterSampleData(new ApiInfo.ApiInfoKey(0,"/api/books", URLMethods.Method.POST), 0);
        filterSampleData2.getSamples().addAll(Arrays.asList("1", "2"));
        FilterSampleData filterSampleData3 = new FilterSampleData(new ApiInfo.ApiInfoKey(0,"/api/toys", URLMethods.Method.PUT), 1);
        filterSampleData3.getSamples().addAll(Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));

        FilterSampleDataDao.instance.insertMany(Arrays.asList(filterSampleData1, filterSampleData2, filterSampleData3, filterSampleDataRemove));
        List<ApiInfo.ApiInfoKey> filterSampleDataIdList = FilterSampleDataDao.instance.getIds();

        Assertions.assertEquals(filterSampleDataIdList.size(),4);
        Assertions.assertNotNull(filterSampleDataIdList.get(0));
        Assertions.assertNotNull(filterSampleDataIdList.get(1));
        Assertions.assertNotNull(filterSampleDataIdList.get(2));
        Assertions.assertNotNull(filterSampleDataIdList.get(3));
    }
}
