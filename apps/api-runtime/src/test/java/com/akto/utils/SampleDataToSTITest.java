package com.akto.utils;

import static org.junit.Assert.assertEquals;

import java.util.*;

import com.akto.dto.type.AccountDataTypesInfo;
import org.checkerframework.checker.units.qual.A;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.SampleDataDao;
import com.akto.dto.AktoDataType;
import com.akto.dto.IgnoreData;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.mongodb.BasicDBObject;

public class SampleDataToSTITest extends MongoBasedTest{
    
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
    public void test1(){
        testInitializer();
        List<String> sampleList = new ArrayList<>();
        sampleList.add("{\"method\":\"POST\",\"requestPayload\":\"{}\",\"responsePayload\":\"{\\\"active\\\":false,\\\"createNew\\\":false,\\\"keyConditionFromUsers\\\":null,\\\"keyOperator\\\":null,\\\"name\\\":null,\\\"tagConfig\\\":null,\\\"tagConfigs\\\":{\\\"tagConfigs\\\":[],\\\"usersMap\\\":{\\\"1661879740\\\":\\\"shivansh@akto.io\\\"}}}\",\"ip\":\"null\",\"source\":\"HAR\",\"type\":\"HTTP/1.1\",\"akto_vxlan_id\":\"1661880070\",\"path\":\"http://localhost:8080/api/fetchTagConfigs\",\"requestHeaders\":\"{\\\"Cookie\\\":\\\"JSESSIONID=node0e7rms6jdk2u41w0drvjv8hkoo0.node0; mp_c403d0b00353cc31d7e33d68dc778806_mixpanel=%7B%22distinct_id%22%3A%20%22182edbc00381d9-063588c46d5c5e-26021d51-144000-182edbc0039615%22%2C%22%24device_id%22%3A%20%22182edbc00381d9-063588c46d5c5e-26021d51-144000-182edbc0039615%22%2C%22%24initial_referrer%22%3A%20%22%24direct%22%2C%22%24initial_referring_domain%22%3A%20%22%24direct%22%7D\\\",\\\"Origin\\\":\\\"http://localhost:8080\\\",\\\"Accept\\\":\\\"application/json, text/plain, */*\\\",\\\"Access-Control-Allow-Origin\\\":\\\"*\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Referer\\\":\\\"http://localhost:8080/dashboard/settings\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"localhost:8080\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"access-token\\\":\\\"eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoibG9naW4iLCJzaWduZWRVcCI6InRydWUiLCJ1c2VybmFtZSI6InNoaXZhbnNoQGFrdG8uaW8iLCJpYXQiOjE2NjE4ODAwNTYsImV4cCI6MTY2MTg4MDk1Nn0.wxDbUhIfhX6i8tITykZcdztg8CZUcrBvdqbLgiZJN0Q4QkGOvhHozZ6lwgFzQe3hTOxuFOv8wxg4E_vzruLMgSRmapHGuTi57qTYFWIJNb-VSUa_Nz__t6aXOaXYckO2nvzN2rp1qeTIEKrhLaC_nV5gZpOB2fnBC2Yr1KasERpdDO7I0xc4dqdLQXQRxrWgP6lKlkGKHziCrkvLEWqC7mXrRsS23m-qv4pELm0MikIqf-fl4wmwj7g42769APwAuoQdIgMnUOx2rT1ewkcW72py3wveX96oomdDyvIM6_y5uYALsTymc0xxr1yZOT9Gseypbjm-sa7byVaSbw2s9g\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"sec-ch-ua\\\":\\\"\\\\\\\"Chromium\\\\\\\";v=\\\\\\\"104\\\\\\\", \\\\\\\" Not A;Brand\\\\\\\";v=\\\\\\\"99\\\\\\\", \\\\\\\"Google Chrome\\\\\\\";v=\\\\\\\"104\\\\\\\"\\\",\\\"sec-ch-ua-mobile\\\":\\\"?0\\\",\\\"sec-ch-ua-platform\\\":\\\"\\\\\\\"Windows\\\\\\\"\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.9\\\",\\\"Content-Length\\\":\\\"2\\\",\\\"account\\\":\\\"1000000\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"X-Frame-Options\\\":\\\"deny\\\",\\\"Cache-Control\\\":\\\"no-cache, no-store, must-revalidate, pre-check=0, post-check=0\\\",\\\"Server\\\":\\\"AKTO server\\\",\\\"X-Content-Type-Options\\\":\\\"nosniff\\\",\\\"Content-Encoding\\\":\\\"gzip\\\",\\\"Vary\\\":\\\"Accept-Encoding, User-Agent\\\",\\\"Content-Length\\\":\\\"150\\\",\\\"X-XSS-Protection\\\":\\\"1\\\",\\\"Content-Language\\\":\\\"en-US\\\",\\\"Date\\\":\\\"Tue, 30 Aug 2022 17:22:40 GMT\\\",\\\"Content-Type\\\":\\\"application/json;charset=utf-8\\\"}\",\"time\":\"1661880160\",\"contentType\":\"application/json;charset=utf-8\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}");
        Key sampleKey = new Key(0, "http://localhost:8080/api/fetchTagConfigs", URLMethods.Method.POST,0,0,0);
        SampleData sampleData = new SampleData(sampleKey,sampleList);
        SampleDataDao.instance.getMCollection().drop();
        SampleDataDao.instance.insertOne(sampleData);

        SampleDataToSTI sampleDataToSTI = new SampleDataToSTI();
        List<SampleData> sampleDataDB = SampleDataDao.instance.findAll(new BasicDBObject());
        sampleDataToSTI.setSampleDataToSTI(sampleDataDB);

        assertEquals(sampleDataToSTI.getSingleTypeList().size(), 38);
        assertEquals(sampleDataToSTI.getSingleTypeInfoMap().size(), 1);

    }
}
