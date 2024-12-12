package com.akto.action;

import com.akto.MongoBasedTest;
import com.akto.action.observe.InventoryAction;
import com.akto.analyser.ResourceAnalyser;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.URLStatic;
import com.akto.dto.type.URLTemplate;
import com.akto.listener.RuntimeListener;
import com.akto.parsers.HttpCallParser;
import com.akto.utils.AccountHTTPCallParserAktoPolicyInfo;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.junit.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertEquals;

public class TestHarAction extends MongoBasedTest{

    @Test
    public void testDemergeAfterReuploadingApis() throws Exception {
        ApiInfoDao.instance.getMCollection().drop();
        Context.userId.set(null);

        HttpCallParser httpCallParser = new HttpCallParser("",0,0,0, true);

        String payload = "{\"method\":\"GET\",\"requestPayload\":\"{\\\"photoUrls\\\":[\\\"string\\\"],\\\"name\\\":\\\"doggie\\\",\\\"id\\\":0,\\\"category\\\":{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"},\\\"tags\\\":[{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"}],\\\"status\\\":\\\"available\\\"}\",\"responsePayload\":\"{\\\"id\\\":9223372036854775807,\\\"category\\\":{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"},\\\"name\\\":\\\"doggie\\\",\\\"photoUrls\\\":[\\\"string\\\"],\\\"tags\\\":[{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"}],\\\"status\\\":\\\"available\\\"}\",\"ip\":\"null\",\"source\":\"MIRRORING\",\"type\":\"HTTP/2\",\"akto_vxlan_id\":\"1661807253\",\"path\":\"https://juice-shop.herokuapp.com/api/Deliverys/ec6d5f9d-94a7-4096-bcf1-7a0818bba867\",\"requestHeaders\":\"{\\\"Origin\\\":\\\"https://petstore.swagger.io\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"TE\\\":\\\"trailers\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Content-Length\\\":\\\"215\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"Tue, 04 Jan 2022 20:11:58 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\",\"time\":\"1641327118\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(payload);
        httpCallParser.syncFunction(Collections.singletonList(httpResponseParams),true, true, null);

        List<ApiInfo> apiInfoList = ApiInfoDao.instance.findAll(Filters.empty());

        assertEquals(1, apiInfoList.size());
        
        int apiCollectionId = apiInfoList.get(0).getId().getApiCollectionId();

        assertEquals("https://juice-shop.herokuapp.com/api/Deliverys/STRING", apiInfoList.get(0).getId().getUrl());

        InventoryAction action = new InventoryAction();
        action.setUrl("https://juice-shop.herokuapp.com/api/Deliverys/STRING");
        action.setMethod("GET");
        action.setApiCollectionId(apiCollectionId);
        action.deMergeApi();

        Map<URLStatic, RequestTemplate> strictURLToMethods = httpCallParser.apiCatalogSync.dbState.get(apiCollectionId).getStrictURLToMethods();
        Map<URLTemplate, RequestTemplate> templateURLToMethods = httpCallParser.apiCatalogSync.dbState.get(apiCollectionId).getTemplateURLToMethods();

        assertEquals(0, strictURLToMethods.size());
        assertEquals(1, templateURLToMethods.size());

        List<ApiInfo> apiInfoList1 = ApiInfoDao.instance.findAll(Filters.empty());
        assertEquals("https://juice-shop.herokuapp.com/api/Deliverys/ec6d5f9d-94a7-4096-bcf1-7a0818bba867", apiInfoList1.get(0).getId().getUrl());

        httpCallParser.syncFunction(Collections.singletonList(httpResponseParams),true, true, null);

        Map<URLStatic, RequestTemplate> strictURLToMethods1 = httpCallParser.apiCatalogSync.dbState.get(apiCollectionId).getStrictURLToMethods();
        Map<URLTemplate, RequestTemplate> templateURLToMethods1 = httpCallParser.apiCatalogSync.dbState.get(apiCollectionId).getTemplateURLToMethods();

        assertEquals(1, strictURLToMethods1.size());
        assertEquals(0, templateURLToMethods1.size());

        List<ApiInfo> apiInfoList2 = ApiInfoDao.instance.findAll(Filters.empty());
        assertEquals("https://juice-shop.herokuapp.com/api/Deliverys/ec6d5f9d-94a7-4096-bcf1-7a0818bba867", apiInfoList2.get(0).getId().getUrl());
    }


    @Test
    public void testHeaderFilter() throws IOException {
        Context.accountId.set(1_000_000);
        ApiCollectionsDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.insertOne(new ApiCollection(0, "Default", 0, new HashSet<>(), null, 0, false, true));

        AccountSettingsDao.instance.getMCollection().drop();
        AccountSettings accountSettings = new AccountSettings(1_000_000, new ArrayList<>(), false, AccountSettings.SetupType.STAGING);
        Map<String, String> filterHeaderMap = new HashMap<>();
        filterHeaderMap.put("Host", "petstore.swagger.io");
        accountSettings.setFilterHeaderValueMap(filterHeaderMap);
        AccountSettingsDao.instance.insertOne(accountSettings);

        AccountHTTPCallParserAktoPolicyInfo info = new AccountHTTPCallParserAktoPolicyInfo();
        info.setHttpCallParser(new HttpCallParser("", 0, 0,0, false));
        info.setResourceAnalyser(new ResourceAnalyser(3000, 0.01, 1000, 0.01));
        RuntimeListener.accountHTTPParserMap.put(1_000_000, info);

        String harString = "{ \"log\": { \"version\": \"1.2\", \"creator\": { \"name\": \"Firefox\", \"version\": \"95.0.1\" }, \"browser\": { \"name\": \"Firefox\", \"version\": \"95.0.1\" }, \"pages\": [ { \"startedDateTime\": \"2022-01-05T01:40:21.384+05:30\", \"id\": \"page_2\", \"title\": \"Swagger UI\", \"pageTimings\": { \"onContentLoad\": -1, \"onLoad\": -1 } } ], \"entries\": [ { \"pageref\": \"page_2\", \"startedDateTime\": \"2022-01-05T01:40:21.384+05:30\", \"request\": { \"bodySize\": 128, \"method\": \"POST\", \"url\": \"/v2/store/order\", \"httpVersion\": \"HTTP/2\", \"headers\": [ { \"name\": \"Host\", \"value\": \"petstore.swagger.io\" }, { \"name\": \"User-Agent\", \"value\": \"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\" }, { \"name\": \"Accept\", \"value\": \"application/json\" }, { \"name\": \"Accept-Language\", \"value\": \"en-US,en;q=0.5\" }, { \"name\": \"Accept-Encoding\", \"value\": \"gzip, deflate, br\" }, { \"name\": \"Referer\", \"value\": \"https://petstore.1234123412341234.swagger.io/\" }, { \"name\": \"Content-Type\", \"value\": \"application/json\" }, { \"name\": \"Origin\", \"value\": \"eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.NHVaYe26MbtOYhSKkoKYdFVomg4i8ZJd8_-RU8VNbftc4TSMb4bXP3l3YlNWACwyXPGffz5aXHc6lty1Y2t4SWRqGteragsVdZufDn5BlnJl9pdR_kdVFUsra2rWKEofkZeIC4yWytE58sMIihvo9H1ScmmVwBcQP6XETqYd0aSHp1gOa9RdUPDvoXQ5oqygTqVtxaDr6wUFKrKItgBMzWIdNZ6y7O9E0DhEPTbE9rfBo6KTFsHAZnMg4k68CDp2woYIaXbmYTWcvbzIuHO7_37GT79XdIwkm95QJ7hYC9RiwrV7mesbY4PAahERJawntho0my942XheVLmGwLMBkQ\" }, { \"name\": \"Content-Length\", \"value\": \"128\" }, { \"name\": \"Connection\", \"value\": \"keep-alive\" }, { \"name\": \"Sec-Fetch-Dest\", \"value\": \"empty\" }, { \"name\": \"Sec-Fetch-Mode\", \"value\": \"cors\" }, { \"name\": \"Sec-Fetch-Site\", \"value\": \"same-origin\" }, { \"name\": \"TE\", \"value\": \"trailers\" } ], \"cookies\": [], \"queryString\": [], \"headersSize\": 472, \"postData\": { \"mimeType\": \"application/json\", \"params\": [], \"text\": \"{\\n \\\"id\\\": 0,\\n \\\"petId\\\": 0,\\n \\\"quantity\\\": 0,\\n \\\"shipDate\\\": \\\"2022-01-04T20:10:16.578Z\\\",\\n \\\"status\\\": \\\"placed\\\",\\n \\\"complete\\\": true\\n}\" } }, \"response\": { \"status\": 200, \"statusText\": \"OK\", \"httpVersion\": \"HTTP/2\", \"headers\": [ { \"name\": \"date\", \"value\": \"Tue, 04 Jan 2022 20:10:21 GMT\" }, { \"name\": \"content-type\", \"value\": \"application/json\" }, { \"name\": \"access-control-allow-origin\", \"value\": \"*\" }, { \"name\": \"access-control-allow-methods\", \"value\": \"GET, POST, DELETE, PUT\" }, { \"name\": \"access-control-allow-headers\", \"value\": \"Content-Type, api_key, Authorization\" }, { \"name\": \"server\", \"value\": \"Jetty(9.2.9.v20150224)\" }, { \"name\": \"X-Firefox-Spdy\", \"value\": \"h2\" } ], \"cookies\": [], \"content\": { \"mimeType\": \"application/json\", \"size\": 125, \"text\": \"{\\\"id\\\":9223372036854772476,\\\"petId\\\":0,\\\"quantity\\\":0,\\\"shipDate\\\":\\\"2022-01-04T20:10:16.578+0000\\\",\\\"status\\\":\\\"placed\\\",\\\"complete\\\":true, \\\"avav\\\": \\\"eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoicmVmcmVzaFRva2VuIiwic2lnbmVkVXAiOiJ0cnVlIiwidXNlcm5hbWUiOiJhbmtpdGFAZ21haWwuY29tIiwiaWF0IjoxNjM0OTcxMTMxLCJleHAiOjE2MzUwNTc1MzF9.Ph4Jv-fdggwvnbdVViD9BWUReYL0dVfVGuMRz4d2oZNnYzWV0JCmjpB68p6k0yyPPua_yagIWVZf_oYH9PUgS7EuaPYR-Vg6uxKR1HuXRA6wb8Xf4RPoFjJYkhWoYmv38V9Cz2My9U85wgGHGZXEufu8ubrFmIfOP6-A39M4meNGw48f5oOz8V337SX45uPc6jE0EfmM4l9EbqFFCF0lRXbMMzn-ijsyXxLkI5npWnqtW3PAHC2Rs3FV40tkRqHYF-WM6SzyHLBh6bVeyeOsFRBoEjv-zFh8yrYnT6OvCa6jII2A6uj4MQ2k11-5bDBhfVPVc4hEQz37H_DWwtf23g\\\"}\" }, \"redirectURL\": \"\", \"headersSize\": 292, \"bodySize\": 417 }, \"cache\": {}, \"timings\": { \"blocked\": 0, \"dns\": 0, \"connect\": 0, \"ssl\": 0, \"send\": 0, \"wait\": 365, \"receive\": 0 }, \"time\": 365, \"_securityState\": \"secure\", \"serverIPAddress\": \"34.235.60.145\", \"connection\": \"443\" }, { \"pageref\": \"page_2\", \"startedDateTime\": \"2022-01-05T01:40:21.384+05:30\", \"request\": { \"bodySize\": 128, \"method\": \"POST\", \"url\": \"/v2/store/country/india\", \"httpVersion\": \"HTTP/2\", \"headers\": [ { \"name\": \"Host\", \"value\": \"akto.io\" }, { \"name\": \"User-Agent\", \"value\": \"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\" }, { \"name\": \"Accept\", \"value\": \"application/json\" }, { \"name\": \"Accept-Language\", \"value\": \"en-US,en;q=0.5\" }, { \"name\": \"Accept-Encoding\", \"value\": \"gzip, deflate, br\" }, { \"name\": \"Referer\", \"value\": \"https://petstore.1234123412341234.swagger.io/\" }, { \"name\": \"Content-Type\", \"value\": \"application/json\" }, { \"name\": \"Origin\", \"value\": \"eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.NHVaYe26MbtOYhSKkoKYdFVomg4i8ZJd8_-RU8VNbftc4TSMb4bXP3l3YlNWACwyXPGffz5aXHc6lty1Y2t4SWRqGteragsVdZufDn5BlnJl9pdR_kdVFUsra2rWKEofkZeIC4yWytE58sMIihvo9H1ScmmVwBcQP6XETqYd0aSHp1gOa9RdUPDvoXQ5oqygTqVtxaDr6wUFKrKItgBMzWIdNZ6y7O9E0DhEPTbE9rfBo6KTFsHAZnMg4k68CDp2woYIaXbmYTWcvbzIuHO7_37GT79XdIwkm95QJ7hYC9RiwrV7mesbY4PAahERJawntho0my942XheVLmGwLMBkQ\" }, { \"name\": \"Content-Length\", \"value\": \"128\" }, { \"name\": \"Connection\", \"value\": \"keep-alive\" }, { \"name\": \"Sec-Fetch-Dest\", \"value\": \"empty\" }, { \"name\": \"Sec-Fetch-Mode\", \"value\": \"cors\" }, { \"name\": \"Sec-Fetch-Site\", \"value\": \"same-origin\" }, { \"name\": \"TE\", \"value\": \"trailers\" } ], \"cookies\": [], \"queryString\": [], \"headersSize\": 472, \"postData\": { \"mimeType\": \"application/json\", \"params\": [], \"text\": \"{\\n \\\"id\\\": 0,\\n \\\"petId\\\": 0,\\n \\\"quantity\\\": 0,\\n \\\"shipDate\\\": \\\"2022-01-04T20:10:16.578Z\\\",\\n \\\"status\\\": \\\"placed\\\",\\n \\\"complete\\\": true\\n}\" } }, \"response\": { \"status\": 200, \"statusText\": \"OK\", \"httpVersion\": \"HTTP/2\", \"headers\": [ { \"name\": \"date\", \"value\": \"Tue, 04 Jan 2022 20:10:21 GMT\" }, { \"name\": \"content-type\", \"value\": \"application/json\" }, { \"name\": \"access-control-allow-origin\", \"value\": \"*\" }, { \"name\": \"access-control-allow-methods\", \"value\": \"GET, POST, DELETE, PUT\" }, { \"name\": \"access-control-allow-headers\", \"value\": \"Content-Type, api_key, Authorization\" }, { \"name\": \"server\", \"value\": \"Jetty(9.2.9.v20150224)\" }, { \"name\": \"X-Firefox-Spdy\", \"value\": \"h2\" } ], \"cookies\": [], \"content\": { \"mimeType\": \"application/json\", \"size\": 125, \"text\": \"{\\\"id\\\":9223372036854772476,\\\"petId\\\":0,\\\"quantity\\\":0,\\\"shipDate\\\":\\\"2022-01-04T20:10:16.578+0000\\\",\\\"status\\\":\\\"placed\\\",\\\"complete\\\":true, \\\"avav\\\": \\\"eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoicmVmcmVzaFRva2VuIiwic2lnbmVkVXAiOiJ0cnVlIiwidXNlcm5hbWUiOiJhbmtpdGFAZ21haWwuY29tIiwiaWF0IjoxNjM0OTcxMTMxLCJleHAiOjE2MzUwNTc1MzF9.Ph4Jv-fdggwvnbdVViD9BWUReYL0dVfVGuMRz4d2oZNnYzWV0JCmjpB68p6k0yyPPua_yagIWVZf_oYH9PUgS7EuaPYR-Vg6uxKR1HuXRA6wb8Xf4RPoFjJYkhWoYmv38V9Cz2My9U85wgGHGZXEufu8ubrFmIfOP6-A39M4meNGw48f5oOz8V337SX45uPc6jE0EfmM4l9EbqFFCF0lRXbMMzn-ijsyXxLkI5npWnqtW3PAHC2Rs3FV40tkRqHYF-WM6SzyHLBh6bVeyeOsFRBoEjv-zFh8yrYnT6OvCa6jII2A6uj4MQ2k11-5bDBhfVPVc4hEQz37H_DWwtf23g\\\"}\" }, \"redirectURL\": \"\", \"headersSize\": 292, \"bodySize\": 417 }, \"cache\": {}, \"timings\": { \"blocked\": 0, \"dns\": 0, \"connect\": 0, \"ssl\": 0, \"send\": 0, \"wait\": 365, \"receive\": 0 }, \"time\": 365, \"_securityState\": \"secure\", \"serverIPAddress\": \"34.235.60.145\", \"connection\": \"443\" }, { \"pageref\": \"page_2\", \"startedDateTime\": \"2022-01-05T01:40:21.384+05:30\", \"request\": { \"bodySize\": 128, \"method\": \"POST\", \"url\": \"/v2/money\", \"httpVersion\": \"HTTP/2\", \"headers\": [ { \"name\": \"User-Agent\", \"value\": \"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\" }, { \"name\": \"Accept\", \"value\": \"application/json\" }, { \"name\": \"Accept-Language\", \"value\": \"en-US,en;q=0.5\" }, { \"name\": \"Accept-Encoding\", \"value\": \"gzip, deflate, br\" }, { \"name\": \"Referer\", \"value\": \"https://petstore.1234123412341234.swagger.io/\" }, { \"name\": \"Content-Type\", \"value\": \"application/json\" }, { \"name\": \"Origin\", \"value\": \"eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.NHVaYe26MbtOYhSKkoKYdFVomg4i8ZJd8_-RU8VNbftc4TSMb4bXP3l3YlNWACwyXPGffz5aXHc6lty1Y2t4SWRqGteragsVdZufDn5BlnJl9pdR_kdVFUsra2rWKEofkZeIC4yWytE58sMIihvo9H1ScmmVwBcQP6XETqYd0aSHp1gOa9RdUPDvoXQ5oqygTqVtxaDr6wUFKrKItgBMzWIdNZ6y7O9E0DhEPTbE9rfBo6KTFsHAZnMg4k68CDp2woYIaXbmYTWcvbzIuHO7_37GT79XdIwkm95QJ7hYC9RiwrV7mesbY4PAahERJawntho0my942XheVLmGwLMBkQ\" }, { \"name\": \"Content-Length\", \"value\": \"128\" }, { \"name\": \"Connection\", \"value\": \"keep-alive\" }, { \"name\": \"Sec-Fetch-Dest\", \"value\": \"empty\" }, { \"name\": \"Sec-Fetch-Mode\", \"value\": \"cors\" }, { \"name\": \"Sec-Fetch-Site\", \"value\": \"same-origin\" }, { \"name\": \"TE\", \"value\": \"trailers\" } ], \"cookies\": [], \"queryString\": [], \"headersSize\": 472, \"postData\": { \"mimeType\": \"application/json\", \"params\": [], \"text\": \"{\\n \\\"id\\\": 0,\\n \\\"petId\\\": 0,\\n \\\"quantity\\\": 0,\\n \\\"shipDate\\\": \\\"2022-01-04T20:10:16.578Z\\\",\\n \\\"status\\\": \\\"placed\\\",\\n \\\"complete\\\": true\\n}\" } }, \"response\": { \"status\": 200, \"statusText\": \"OK\", \"httpVersion\": \"HTTP/2\", \"headers\": [ { \"name\": \"date\", \"value\": \"Tue, 04 Jan 2022 20:10:21 GMT\" }, { \"name\": \"content-type\", \"value\": \"application/json\" }, { \"name\": \"access-control-allow-origin\", \"value\": \"*\" }, { \"name\": \"access-control-allow-methods\", \"value\": \"GET, POST, DELETE, PUT\" }, { \"name\": \"access-control-allow-headers\", \"value\": \"Content-Type, api_key, Authorization\" }, { \"name\": \"server\", \"value\": \"Jetty(9.2.9.v20150224)\" }, { \"name\": \"X-Firefox-Spdy\", \"value\": \"h2\" } ], \"cookies\": [], \"content\": { \"mimeType\": \"application/json\", \"size\": 125, \"text\": \"{\\\"id\\\":9223372036854772476,\\\"petId\\\":0,\\\"quantity\\\":0,\\\"shipDate\\\":\\\"2022-01-04T20:10:16.578+0000\\\",\\\"status\\\":\\\"placed\\\",\\\"complete\\\":true, \\\"avav\\\": \\\"eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoicmVmcmVzaFRva2VuIiwic2lnbmVkVXAiOiJ0cnVlIiwidXNlcm5hbWUiOiJhbmtpdGFAZ21haWwuY29tIiwiaWF0IjoxNjM0OTcxMTMxLCJleHAiOjE2MzUwNTc1MzF9.Ph4Jv-fdggwvnbdVViD9BWUReYL0dVfVGuMRz4d2oZNnYzWV0JCmjpB68p6k0yyPPua_yagIWVZf_oYH9PUgS7EuaPYR-Vg6uxKR1HuXRA6wb8Xf4RPoFjJYkhWoYmv38V9Cz2My9U85wgGHGZXEufu8ubrFmIfOP6-A39M4meNGw48f5oOz8V337SX45uPc6jE0EfmM4l9EbqFFCF0lRXbMMzn-ijsyXxLkI5npWnqtW3PAHC2Rs3FV40tkRqHYF-WM6SzyHLBh6bVeyeOsFRBoEjv-zFh8yrYnT6OvCa6jII2A6uj4MQ2k11-5bDBhfVPVc4hEQz37H_DWwtf23g\\\"}\" }, \"redirectURL\": \"\", \"headersSize\": 292, \"bodySize\": 417 }, \"cache\": {}, \"timings\": { \"blocked\": 0, \"dns\": 0, \"connect\": 0, \"ssl\": 0, \"send\": 0, \"wait\": 365, \"receive\": 0 }, \"time\": 365, \"_securityState\": \"secure\", \"serverIPAddress\": \"34.235.60.145\", \"connection\": \"443\" }] } }";

        HarAction harAction = new HarAction();
        Map<String,Object> session = new HashMap<>();
        User user = new User();
        user.setLogin("test@akto.io");
        session.put("user",user);
        harAction.setSession(session);
        Context.userId.set(null);
        harAction.setHarString(harString);
        harAction.setApiCollectionId(0);

        harAction.executeWithSkipKafka(true);

        Bson filter = SingleTypeInfoDao.filterForHostHeader(0, false);
        long count = SingleTypeInfoDao.instance.getMCollection().countDocuments(filter);
        assertEquals(1, count);
    }
}
