package com.akto.analyser;

import com.akto.MongoBasedTest;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.AktoDataType;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.IgnoreData;
import com.akto.dto.type.AccountDataTypesInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.APICatalogSync;
import com.mongodb.BasicDBObject;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

public class ResourceAnalyserTest extends MongoBasedTest {

    private HttpResponseParams generateHttpResponse(String ip, String url, String reqPayload, int apiCollectionId) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-forwarded-for", Collections.singletonList(ip));
        HttpRequestParams httpRequestParams = new HttpRequestParams("GET", url, "", headers, reqPayload, apiCollectionId);
        return new HttpResponseParams("", 200, "", new HashMap<>(), "{}", httpRequestParams, 0,"1000000", false, HttpResponseParams.Source.MIRRORING, "", "");
    }

    private static String generateReqPayload(String name, String company) {
        BasicDBObject payload = new BasicDBObject();
        BasicDBObject ob = new BasicDBObject();
        ob.put("name", name);
        ob.put("company", company);
        payload.put("user", ob);
        return payload.toJson();
    }
    
    public void testInitializer(){
        Map<String, AktoDataType> aktoDataTypeMap = new HashMap<>();
        aktoDataTypeMap.put("JWT", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("PHONE_NUMBER", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("CREDIT_CARD", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("IP_ADDRESS", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("EMAIL", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("SSN", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("UUID", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("URL", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        AccountDataTypesInfo info = SingleTypeInfo.getAccountToDataTypesInfo().get(ACCOUNT_ID);
        if (info == null) {
            info = new AccountDataTypesInfo();
        }
        info.setAktoDataTypeMap(aktoDataTypeMap);
        SingleTypeInfo.getAccountToDataTypesInfo().put(ACCOUNT_ID, info);
    }

    @Test
    public void testEndToEnd() {
        testInitializer();
        SingleTypeInfoDao.instance.getMCollection().drop();
        String userIp1 = "192.0.0.1";
        String userIp2 = "192.0.0.2";
        String userIp3 = "192.0.0.3";
        String userIp4 = "192.0.0.4";

        String company1 = "company1";
        String company2 = "company2";

        String url1 = "/api/books";
        String url2 = "/api/cars";
        String url3 = "/api/books/1";
        String url4 = "/api/books/2";
        String urlMerged = "api/books/INTEGER";

        HttpResponseParams resp1 = generateHttpResponse(userIp1, url1, generateReqPayload(userIp1, company1),0);
        HttpResponseParams resp2 = generateHttpResponse(userIp1, url1, generateReqPayload(userIp1, company2),0);
        HttpResponseParams resp3 = generateHttpResponse(userIp2, url1, generateReqPayload(userIp2, company1),0);
        HttpResponseParams resp4 = generateHttpResponse(userIp2, url1, generateReqPayload(userIp2, company2),0);
        HttpResponseParams resp5 = generateHttpResponse(userIp3, url1, generateReqPayload(userIp3, company1),0);
        HttpResponseParams resp6 = generateHttpResponse(userIp3, url1, generateReqPayload(userIp3, company2),0);
        HttpResponseParams resp7 = generateHttpResponse(userIp4, url1, generateReqPayload(userIp4, company1),0);
        HttpResponseParams resp8 = generateHttpResponse(userIp4, url1, generateReqPayload(userIp4, company2),0);

        HttpResponseParams resp9 = generateHttpResponse(userIp1, url2, generateReqPayload(userIp1, company1),0);
        HttpResponseParams resp10 = generateHttpResponse(userIp1, url2, generateReqPayload(userIp1, company2),0);

        HttpResponseParams resp11 = generateHttpResponse(userIp1, url1, generateReqPayload(userIp1, company1),1);
        HttpResponseParams resp12 = generateHttpResponse(userIp1, url1, generateReqPayload(userIp1, company2),1);

        HttpResponseParams resp13 = generateHttpResponse(userIp1, url3, generateReqPayload(userIp1, company1),0);
        HttpResponseParams resp14 = generateHttpResponse(userIp1, url4, generateReqPayload(userIp1, company2),0);

        // doubled the response but unique and public count won't increase
        List<HttpResponseParams> responseParams = Arrays.asList(resp1, resp2, resp3, resp4, resp5, resp6, resp7, resp8,
                resp9, resp10, resp11, resp12, resp1, resp2, resp3, resp4, resp5, resp6, resp7, resp8,
                resp9, resp10, resp11, resp12, resp13, resp14);

        Collections.shuffle(responseParams);

        HttpCallParser httpCallParser = new HttpCallParser("", 0, 0, 0, true);

        // to add new single type info of urlParams
        httpCallParser.syncFunction(responseParams, true, true, null);
        APICatalogSync.mergeUrlsAndSave(0, true, false, httpCallParser.apiCatalogSync.existingAPIsInDb, false, false);
        APICatalogSync.mergeUrlsAndSave(1, true, false, httpCallParser.apiCatalogSync.existingAPIsInDb, false, false);

        List<SingleTypeInfo> singleTypeInfoList = SingleTypeInfoDao.instance.fetchAll();
        assertEquals(13, singleTypeInfoList.size()); // 3 per api (url3 and url4 merged) and 1 url param

        ResourceAnalyser resourceAnalyser = new ResourceAnalyser(10_000, 0.01, 10_000, 0.01);
        for (HttpResponseParams resp: responseParams) {
            resourceAnalyser.analyse(resp);
        }

        assertEquals(resourceAnalyser.countMap.size(), 9); // headers not included so 2 per api
        resourceAnalyser.syncWithDb();

        singleTypeInfoList = SingleTypeInfoDao.instance.fetchAll();
        assertEquals(13, singleTypeInfoList.size());

        for (SingleTypeInfo singleTypeInfo: singleTypeInfoList) {
            if (singleTypeInfo.getApiCollectionId() == 0 && singleTypeInfo.getUrl().equals(url1) && singleTypeInfo.getResponseCode() == -1 && !singleTypeInfo.isIsHeader()) {
                if (singleTypeInfo.getParam().equals("user#name")) {
                    assertEquals(4, singleTypeInfo.getUniqueCount());
                    assertEquals(0, singleTypeInfo.getPublicCount());
                } else if (singleTypeInfo.getParam().equals("user#company")) {
                    assertEquals(2, singleTypeInfo.getUniqueCount());
                    assertEquals(2, singleTypeInfo.getPublicCount());
                } else {
                    System.out.println("*************************************");
                    System.out.println(singleTypeInfo);
                    assertEquals(1,2);
                    System.out.println("*************************************");
                }
            }
            if (singleTypeInfo.getApiCollectionId() == 0 && singleTypeInfo.getUrl().equals(urlMerged) && singleTypeInfo.getResponseCode() == -1 && !singleTypeInfo.isIsHeader()) {
                if (singleTypeInfo.getParam().equals("user#name")) {
                    assertEquals(1, singleTypeInfo.getUniqueCount());
                    assertEquals(0, singleTypeInfo.getPublicCount());
                } else if (singleTypeInfo.getParam().equals("user#company")) {
                    assertEquals(2, singleTypeInfo.getUniqueCount());
                    assertEquals(0, singleTypeInfo.getPublicCount());
                } else if (singleTypeInfo.getParam().equals("2") && singleTypeInfo.getIsUrlParam()) {
                    assertEquals(2, singleTypeInfo.getUniqueCount());
                    assertEquals(0, singleTypeInfo.getPublicCount());
                } else {
                    System.out.println("*************************************");
                    System.out.println(singleTypeInfo);
                    assertEquals(1,2);
                    System.out.println("*************************************");
                }
            }
        }

    }
}
