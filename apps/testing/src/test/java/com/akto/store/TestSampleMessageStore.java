package com.akto.store;

import com.akto.MongoBasedTest;
import com.akto.dao.SampleDataDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.RawApi;
import com.akto.dto.testing.*;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

public class TestSampleMessageStore extends MongoBasedTest {

    @Test
    public void testFetchSampleMessages() {
        SampleDataDao.instance.getMCollection().drop();
        SampleData sampleData1 = new SampleData(new Key(0, "url1", URLMethods.Method.GET,0,0,0), null);
        SampleData sampleData2 = new SampleData(new Key(0, "url2", URLMethods.Method.GET,0,0,0), Arrays.asList("m1", "m2"));
        SampleData sampleData3 = new SampleData(new Key(0, "url3", URLMethods.Method.GET,0,0,0), Collections.emptyList());
        SampleData sampleData4 = new SampleData(new Key(1, "url1", URLMethods.Method.GET,0,0,0), Arrays.asList("m3", "m4", "m5"));
        SampleDataDao.instance.insertMany(Arrays.asList(sampleData1, sampleData2, sampleData3, sampleData4));

        SampleMessageStore sample =  SampleMessageStore.create();
        Set<Integer> apiCollectionIds = new HashSet<>();
        apiCollectionIds.add(0);
        apiCollectionIds.add(1);
        sample.fetchSampleMessages(apiCollectionIds);
        Map<ApiInfo.ApiInfoKey, List<String>> sampleDataMap =  sample.getSampleDataMap();

        assertEquals(sampleDataMap.size(), 3);
        List<String> messages = sampleDataMap.get(new ApiInfo.ApiInfoKey(0, "url2", URLMethods.Method.GET));
        assertEquals(messages.size(), 1);

        messages = sampleDataMap.get(new ApiInfo.ApiInfoKey(1, "url1", URLMethods.Method.GET));
        assertEquals(messages.size(), 1);

        SampleDataDao.instance.getMCollection().drop();
        sampleData2 = new SampleData(new Key(0, "url2", URLMethods.Method.GET,0,0,0), Arrays.asList("m1", "m2", "m3"));
        SampleDataDao.instance.insertMany(Arrays.asList(sampleData1, sampleData2));
        sample.fetchSampleMessages(apiCollectionIds);
        sampleDataMap =  sample.getSampleDataMap();
        assertEquals(sampleDataMap.size(), 1);
        messages = sampleDataMap.get(new ApiInfo.ApiInfoKey(0, "url2", URLMethods.Method.GET));
        assertEquals(messages.size(), 1);

    }

    @Test
    public void testFilterMessagesWithAuthToken() {
        AuthMechanism authMechanism = new AuthMechanism(
                Collections.singletonList(new HardcodedAuthParam(AuthParam.Location.HEADER, "akto", "something", true)), null, null, null
        );

        List<RawApi> filteredList = SampleMessageStore.filterMessagesWithAuthToken(new ArrayList<>() , authMechanism);
        assertEquals(0, filteredList.size());

        List<RawApi> values = new ArrayList<>();
        // both values don't contain auth token
        values.add(RawApi.buildFromMessage("{ \"method\": \"GET\", \"requestPayload\": \"{}\", \"responsePayload\": \"{\\\"id\\\":2,\\\"category\\\":{\\\"id\\\":0},\\\"name\\\":\\\"teste\\\",\\\"photoUrls\\\":[],\\\"tags\\\":[]}\", \"ip\": \"null\", \"source\": \"HAR\", \"type\": \"HTTP/2\", \"akto_vxlan_id\": \"1661807253\", \"path\": \"https://petstore.swagger.io/v2/pet/2\", \"requestHeaders\": \"{\\\"TE\\\":\\\"trailers\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\", \\\"Origin\\\" : \\\"dddd\\\"}\", \"responseHeaders\": \"{\\\"date\\\":\\\"Tue, 04 Jan 2022 20:12:27 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\", \"time\": \"1641327147\", \"contentType\": \"application/json\", \"akto_account_id\": \"1000000\", \"statusCode\": \"200\", \"status\": \"OK\" }"));
        values.add(RawApi.buildFromMessage("{ \"method\": \"GET\", \"requestPayload\": \"{}\", \"responsePayload\": \"{\\\"id\\\":2,\\\"category\\\":{\\\"id\\\":0},\\\"name\\\":\\\"teste\\\",\\\"photoUrls\\\":[],\\\"tags\\\":[]}\", \"ip\": \"null\", \"source\": \"HAR\", \"type\": \"HTTP/2\", \"akto_vxlan_id\": \"1661807253\", \"path\": \"https://petstore.swagger.io/v2/pet/2\", \"requestHeaders\": \"{\\\"TE\\\":\\\"trailers\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\", \\\"Origin\\\" : \\\"dddd\\\"}\", \"responseHeaders\": \"{\\\"date\\\":\\\"Tue, 04 Jan 2022 20:12:27 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\", \"time\": \"1641327147\", \"contentType\": \"application/json\", \"akto_account_id\": \"1000000\", \"statusCode\": \"200\", \"status\": \"OK\" }"));

        filteredList = SampleMessageStore.filterMessagesWithAuthToken(values, authMechanism);
        assertEquals(0, filteredList.size());

        // this value contains auth token so no errors
        values.add(RawApi.buildFromMessage("{ \"method\": \"GET\", \"requestPayload\": \"{}\", \"responsePayload\": \"{\\\"id\\\":2,\\\"category\\\":{\\\"id\\\":0},\\\"name\\\":\\\"teste\\\",\\\"photoUrls\\\":[],\\\"tags\\\":[]}\", \"ip\": \"null\", \"source\": \"HAR\", \"type\": \"HTTP/2\", \"akto_vxlan_id\": \"1661807253\", \"path\": \"https://petstore.swagger.io/v2/pet/2\", \"requestHeaders\": \"{\\\"TE\\\":\\\"trailers\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\", \\\"Origin\\\" : \\\"dddd\\\", \\\"akto\\\" : \\\"blah\\\"}\", \"responseHeaders\": \"{\\\"date\\\":\\\"Tue, 04 Jan 2022 20:12:27 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\", \"time\": \"1641327147\", \"contentType\": \"application/json\", \"akto_account_id\": \"1000000\", \"statusCode\": \"200\", \"status\": \"OK\" }"));

        filteredList = SampleMessageStore.filterMessagesWithAuthToken(values, authMechanism);
        assertEquals(1, filteredList.size());

    }
}
