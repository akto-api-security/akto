package com.akto.utils;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bson.conversions.Bson;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.AktoDataTypeDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.AktoDataType;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.parsers.HttpCallParser;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;

public class TestUpdatesInCollections extends MongoBasedTest {
    
    public void fillDBValues() throws Exception{
        HttpCallParser httpCallParser = new HttpCallParser("", 100000, 10000, 10000, true);

        // url = http://sampl-aktol-1exannwybqov-67928726.ap-south-1.elb.amazonaws.com/api/college/student-details, GET, EMAIL, Response
        String payload1 = "{\"path\":\"http://sampl-aktol-1exannwybqov-67928726.ap-south-1.elb.amazonaws.com/api/college/student-details\",\"requestHeaders\":\"{\\\"Access-Token\\\":\\\"JWT eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoibG9naW4iLCJzaWduZWRVcCI6InRydWUiLCJ1c2VybmFtZSI6InJpc2hhdi5zb2xhbmtpQGFrdG8uaW8iLCJpYXQiOjE2ODg3MTMzNTUsImV4cCI6MTY4ODcxNDI1NX0.ToSrgQdEWaTVBphY9QMPBmo1zWgaDt_2zRlFb4gLYcgn3x58ClnTciRXN--9 LeoKojWo466S2rDDK8KH3IhR7gTDKk9ihKfLaVoKIg7M7RaHxFgp - vtjWenFcR6IBqLXqYh_kCqBFDH3hjrbD1Qtoaieu_L1rtJFwqz2xoIZP0VEmTPXT4vxT6yoVlbgloROzu1cJFGnoFQm69OUNHpCLf9S_7Qs - 9 eV2V - AlzeClfMnblTqhQP_s4znPit2Ik0ypNIH - mEwgxL - coWVmphuFYy5uG5c2Z4F4te7r_QP9jlOVYFjwB6_9gQSwi1lrm8qKdNml1UKnh4NNizc1878oQ\\\", \\\"Content-Type\\\": \\\"application/json\\\"}\",\"responseHeaders\":\"\",\"method\":\"GET\",\"requestPayload\":\"\",\"responsePayload\":\"{\\\"id\\\": 101, \\\"name\\\": \\\"Stud-101\\\", \\\"email\\\": \\\"stude_101@example.com\\\", \\\"course\\\": \\\"MECH\\\"}\",\"status\":\"OK\",\"statusCode\":\"200\",\"akto_account_id\":\"1000000\",\"ip\":\"null\",\"time\":\"1692973077\",\"type\":\"HTTP/1.1\",\"contentType\":\"application/json\",\"source\":\"HAR\",\"akto_vxlan_id\":1111111111}";
        
        // url = "http://sampl-aktol-1exannwybqov-67928726.ap-south-1.elb.amazonaws.com/api/college/prof/upload-marks, TOKEN, POST, Request
        String payload2 = "{\"path\":\"http://sampl-aktol-1exannwybqov-67928726.ap-south-1.elb.amazonaws.com/api/college/prof/upload-marks\",\"requestHeaders\":\"{\\\"Access-Token\\\":\\\"JWT eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoibG9naW4iLCJzaWduZWRVcCI6InRydWUiLCJ1c2VybmFtZSI6InJpc2hhdi5zb2xhbmtpQGFrdG8uaW8iLCJpYXQiOjE2ODg3MTMzNTUsImV4cCI6MTY4ODcxNDI1NX0.ToSrgQdEWaTVBphY9QMPBmo1zWgaDt_2zRlFb4gLYcgn3x58ClnTciRXN--9 LeoKojWo466S2rDDK8KH3IhR7gTDKk9ihKfLaVoKIg7M7RaHxFgp-vtjWenFcR6IBqLXqYh_kCqBFDH3hjrbD1Qtoaieu_L1rtJFwqz2xoIZP0VEmTPXT4vxT6yoVlbgloROzu1cJFGnoFQm69OUNHpCLf9S_7Qs-9eV2V-AlzeClfMnblTqhQP_s4znPit2Ik0ypNIH-mEwgxL-coWVmphuFYy5uG5c2Z4F4te7r_QP9jlOVYFjwB6_9gQSwi1lrm8qKdNml1UKnh4NNizc1878oQ\\\",\\\"Content-Type\\\": \\\"application/json\\\", \\\"Content-Length\\\": \\\"2\\\"}\",\"responseHeaders\":\"\",\"method\":\"POST\",\"requestPayload\":\"{\\\"fileName\\\": \\\"marks.csv\\\"}\",\"responsePayload\":\"{\\\"message\\\": \\\"Marks Uploaded Successfully\\\"}\",\"status\":\"OK\",\"statusCode\":\"200\",\"akto_account_id\":\"1000000\",\"ip\":\"null\",\"time\":\"1693218235\",\"type\":\"HTTP/1.1\",\"contentType\":\"application/json\",\"source\":\"HAR\",\"akto_vxlan_id\":1111111111}";

        //url = https://petstore.swagger.io/v2/store/order , POST, JWT, Response
        String payload3 = "{\"method\":\"POST\",\"requestPayload\":\"{\\n  \\\"id\\\": 0,\\n  \\\"petId\\\": 0,\\n  \\\"quantity\\\": 0,\\n  \\\"shipDate\\\": \\\"2022-01-04T20:10:16.578Z\\\",\\n  \\\"status\\\": \\\"placed\\\",\\n  \\\"complete\\\": true\\n}\",\"responsePayload\":\"{\\\"id\\\":9223372036854772476,\\\"petId\\\":0,\\\"quantity\\\":0,\\\"shipDate\\\":\\\"2022-01-04T20:10:16.578+0000\\\",\\\"status\\\":\\\"placed\\\",\\\"complete\\\":true, \\\"avav\\\": \\\"eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoicmVmcmVzaFRva2VuIiwic2lnbmVkVXAiOiJ0cnVlIiwidXNlcm5hbWUiOiJhbmtpdGFAZ21haWwuY29tIiwiaWF0IjoxNjM0OTcxMTMxLCJleHAiOjE2MzUwNTc1MzF9.Ph4Jv-fdggwvnbdVViD9BWUReYL0dVfVGuMRz4d2oZNnYzWV0JCmjpB68p6k0yyPPua_yagIWVZf_oYH9PUgS7EuaPYR-Vg6uxKR1HuXRA6wb8Xf4RPoFjJYkhWoYmv38V9Cz2My9U85wgGHGZXEufu8ubrFmIfOP6-A39M4meNGw48f5oOz8V337SX45uPc6jE0EfmM4l9EbqFFCF0lRXbMMzn-ijsyXxLkI5npWnqtW3PAHC2Rs3FV40tkRqHYF-WM6SzyHLBh6bVeyeOsFRBoEjv-zFh8yrYnT6OvCa6jII2A6uj4MQ2k11-5bDBhfVPVc4hEQz37H_DWwtf23g\\\"}\",\"ip\":\"null\",\"source\":\"HAR\",\"type\":\"HTTP/2\",\"akto_vxlan_id\":\"1693373383\",\"path\":\"https://petstore.swagger.io/v2/store/order\",\"requestHeaders\":\"{\\\"Origin\\\":\\\"https://petstore.swagger.io\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"TE\\\":\\\"trailers\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Content-Length\\\":\\\"128\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"Tue, 04 Jan 2022 20:10:21 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\",\"time\":\"1641327021\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";

        // url = https://petstore.swagger.io/v2/pet; method = put
        String payload4 = "{\"method\":\"PUT\",\"requestPayload\":\"{\\\"photoUrls\\\":[\\\"string\\\"],\\\"name\\\":\\\"doggie\\\",\\\"id\\\":0,\\\"category\\\":{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"},\\\"tags\\\":[{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"}],\\\"status\\\":\\\"available\\\"}\",\"responsePayload\":\"{\\\"id\\\":9223372036854775807,\\\"category\\\":{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"},\\\"name\\\":\\\"doggie\\\",\\\"photoUrls\\\":[\\\"string\\\"],\\\"tags\\\":[{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"}],\\\"status\\\":\\\"available\\\"}\",\"ip\":\"null\",\"source\":\"MIRRORING\",\"type\":\"HTTP/2\",\"akto_vxlan_id\":\"1661807253\",\"path\":\"https://petstore.swagger.io/v2/pet\",\"requestHeaders\":\"{\\\"Origin\\\":\\\"https://petstore.swagger.io\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"TE\\\":\\\"trailers\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Content-Length\\\":\\\"215\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"Tue, 04 Jan 2022 20:11:58 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\",\"time\":\"1641327118\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        
        // url = https://petstore.swagger.io/v2/books/1 ; method = post
        String payload5 = "{\"method\":\"POST\",\"requestPayload\":\"{\\\"photoUrls\\\":[\\\"string\\\"],\\\"name\\\":\\\"tommie\\\",\\\"id\\\":0,\\\"category\\\":{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"},\\\"tags\\\":[{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"}],\\\"status\\\":\\\"available\\\"}\",\"responsePayload\":\"{\\\"id\\\":9223372036854775807,\\\"category\\\":{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"},\\\"name\\\":\\\"doggie\\\",\\\"photoUrls\\\":[\\\"string\\\"],\\\"tags\\\":[{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"}],\\\"status\\\":\\\"available\\\"}\",\"ip\":\"null\",\"source\":\"MIRRORING\",\"type\":\"HTTP/2\",\"akto_vxlan_id\":\"1661807253\",\"path\":\"https://petstore.swagger.io/v2/books/1\",\"requestHeaders\":\"{\\\"Origin\\\":\\\"https://petstore.swagger.io\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"TE\\\":\\\"trailers\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Content-Length\\\":\\\"215\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"Tue, 04 Jan 2022 20:11:58 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\",\"time\":\"1641327118\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        
        String[] payloadStrings = {payload1, payload2 , payload3, payload4, payload5} ;
        List<String> payloads = new ArrayList<>();
        payloads.addAll(Arrays.asList(payloadStrings));
        
        List<HttpResponseParams> httpResponseParamsList = new ArrayList<>();

        for(String payload: payloads){
            HttpResponseParams httpResponseParam = HttpCallParser.parseKafkaMessage(payload);
            httpResponseParamsList.add(httpResponseParam);
        }

        httpCallParser.syncFunction(httpResponseParamsList, false, true);
        httpCallParser.apiCatalogSync.syncWithDB(false, true);
    }

    @Test
    public void testSensitiveInfoCron () throws Exception{
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiInfoDao.instance.getMCollection().drop();

        fillDBValues();
        RiskScoreOfCollections riskScoreOfCollections = new RiskScoreOfCollections();
        riskScoreOfCollections.mapSensitiveSTIsInApiInfo(0, 0);
        
        Bson filter = Filters.and(
            Filters.eq("_id.url", "http://sampl-aktol-1exannwybqov-67928726.ap-south-1.elb.amazonaws.com/api/college/student-details"),
            Filters.eq("_id.method", "GET")
        );

        ApiInfo apiInfo = ApiInfoDao.instance.findOne(filter);
        assertEquals(true, apiInfo.getIsSensitive());

        Bson filter2 = Filters.and(
            Filters.eq("_id.url", "http://sampl-aktol-1exannwybqov-67928726.ap-south-1.elb.amazonaws.com/api/college/prof/upload-marks"),
            Filters.eq("_id.method", "POST")
        );

        ApiInfo apiInfo2 = ApiInfoDao.instance.findOne(filter2);
        assertEquals(false, apiInfo2.getIsSensitive());
        
        Bson filter3 = Filters.and(
            Filters.eq("_id.url", "https://petstore.swagger.io/v2/store/order"),
            Filters.eq("_id.method", "POST")
        );

        ApiInfo apiInfo3 = ApiInfoDao.instance.findOne(filter3);
        assertEquals(true, apiInfo3.getIsSensitive());

        AktoDataType dataType = AktoDataTypeDao.instance.findOne("name", "JWT");
        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
        options.returnDocument(ReturnDocument.AFTER);
        options.upsert(false);
        dataType = AktoDataTypeDao.instance.getMCollection().findOneAndUpdate(
            Filters.eq("name", dataType.getName()),
            Updates.combine(
                Updates.set("sensitiveAlways",true),
                Updates.set("sensitivePosition",new ArrayList<>()),
                Updates.set("timestamp",Context.now())
            ),
            options
        );
        riskScoreOfCollections.mapSensitiveSTIsInApiInfo(Context.now() - (2 * 60), 0);
        ApiInfo apiInfo4 = ApiInfoDao.instance.findOne(filter3);
        assertEquals(true, apiInfo4.getIsSensitive());


    }
}
