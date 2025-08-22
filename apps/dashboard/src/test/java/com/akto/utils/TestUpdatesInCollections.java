package com.akto.utils;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.action.testing_issues.IssuesAction;
import com.akto.dao.AktoDataTypeDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.CustomDataTypeDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.AktoDataType;
import com.akto.dto.ApiInfo;
import com.akto.dto.BackwardCompatibility;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.HttpResponseParams.Source;
import com.akto.dto.billing.SyncLimit;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.listener.InitializerListener;
import com.akto.parsers.HttpCallParser;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.Severity;
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
        
        // url = https://petstore.swagger.io/v2/books/aryan ; method = post
        String payload5 = "{\"method\":\"POST\",\"requestPayload\":\"{\\\"photoUrls\\\":[\\\"string\\\"],\\\"name\\\":\\\"tommie\\\",\\\"id\\\":0,\\\"category\\\":{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"},\\\"tags\\\":[{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"}],\\\"status\\\":\\\"available\\\"}\",\"responsePayload\":\"{\\\"id\\\":9223372036854775807,\\\"category\\\":{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"},\\\"name\\\":\\\"doggie\\\",\\\"photoUrls\\\":[\\\"string\\\"],\\\"tags\\\":[{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"}],\\\"status\\\":\\\"available\\\"}\",\"ip\":\"null\",\"source\":\"MIRRORING\",\"type\":\"HTTP/2\",\"akto_vxlan_id\":\"1661807253\",\"path\":\"https://petstore.swagger.io/v2/books/aryan\",\"requestHeaders\":\"{\\\"Origin\\\":\\\"https://petstore.swagger.io\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"TE\\\":\\\"trailers\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Content-Length\\\":\\\"215\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"Tue, 04 Jan 2022 20:11:58 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\",\"time\":\"1641327118\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        
        String[] payloadStrings = {payload1, payload2 , payload3, payload4, payload5} ;
        List<String> payloads = new ArrayList<>();
        payloads.addAll(Arrays.asList(payloadStrings));
        
        List<HttpResponseParams> httpResponseParamsList = new ArrayList<>();

        for(String payload: payloads){
            HttpResponseParams httpResponseParam = HttpCallParser.parseKafkaMessage(payload);
            httpResponseParamsList.add(httpResponseParam);
        }

        httpCallParser.syncFunction(httpResponseParamsList, false, true, null);
        httpCallParser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit,
            Source.HAR);
    }

    public TestingRunIssues generateTestResultIssue(String url, String method, Severity severity, String testSubCategory) throws Exception{
        Bson filter = Filters.and(
            Filters.eq("_id.url", url),
            Filters.eq("_id.method", method)
        );
        ApiInfo apiInfo = ApiInfoDao.instance.findOne(filter);
        ApiInfoKey apiInfoKey = apiInfo.getId();

        TestingIssuesId testingIssuesId = new TestingIssuesId(apiInfoKey, GlobalEnums.TestErrorSource.AUTOMATED_TESTING ,testSubCategory);
        TestingRunIssues testingRunIssues = new TestingRunIssues(testingIssuesId, severity, GlobalEnums.TestRunIssueStatus.OPEN, Context.now(), Context.now(), new ObjectId(new Date()),null, Context.now());

        return testingRunIssues;
    }

    @Test
    public void testSensitiveInfoCron () throws Exception{
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiInfoDao.instance.getMCollection().drop();
        AktoDataTypeDao.instance.getMCollection().drop();
        CustomDataTypeDao.instance.getMCollection().drop();
        SampleDataDao.instance.getMCollection().drop();
        Context.userId.set(null);

        InitializerListener.addAktoDataTypes(new BackwardCompatibility());
        SingleTypeInfo.fetchCustomDataTypes(MongoBasedTest.ACCOUNT_ID);

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
                Updates.set("sensitiveAlways",false),
                Updates.set("sensitivePosition",new ArrayList<>()),
                Updates.set("timestamp",Context.now())
            ),
            options
        );
        SingleTypeInfo.fetchCustomDataTypes(MongoBasedTest.ACCOUNT_ID);
        riskScoreOfCollections.mapSensitiveSTIsInApiInfo(Context.now() - (2 * 60), 0);
        ApiInfo apiInfo4 = ApiInfoDao.instance.findOne(filter3);
        assertEquals(false, apiInfo4.getIsSensitive());
    }

    @Test
    public void testSeverityScore() throws Exception{
        // set `ts` every time after runing cron to set last cron timestamp
        // Thread.sleep before running cron to show that some time has passed since running cron
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiInfoDao.instance.getMCollection().drop();
        AktoDataTypeDao.instance.getMCollection().drop();
        CustomDataTypeDao.instance.getMCollection().drop();
        SampleDataDao.instance.getMCollection().drop();
        TestingRunIssuesDao.instance.getMCollection().drop();
        Context.userId.set(null);

        InitializerListener.addAktoDataTypes(new BackwardCompatibility());
        SingleTypeInfo.fetchCustomDataTypes(MongoBasedTest.ACCOUNT_ID);

        int ts = 0;

        fillDBValues();
        String url1 = "https://petstore.swagger.io/v2/store/order";
        String method1 = "POST";

        String url2 = "https://petstore.swagger.io/v2/books/aryan";
        String method2 = "POST";

        String url3 = "http://sampl-aktol-1exannwybqov-67928726.ap-south-1.elb.amazonaws.com/api/college/student-details";
        String method3 = "GET";

        TestingRunIssues issue1 = generateTestResultIssue(url1, "POST", GlobalEnums.Severity.HIGH, "REMOVE_TOKENS");
        TestingRunIssues issue2 =  generateTestResultIssue(url1, "POST",GlobalEnums.Severity.LOW, "MUST_CONTAIN_RESPONSE_HEADERS");
        TestingRunIssues issue3 = generateTestResultIssue(url3, "GET", GlobalEnums.Severity.MEDIUM, "MUST_CONTAIN_RESPONSE_HEADERS");
        TestingRunIssues issue4 = generateTestResultIssue(url3, "GET", GlobalEnums.Severity.MEDIUM, "REMOVE_TOKENS");
        TestingRunIssues issue5 = generateTestResultIssue(url2, "POST", GlobalEnums.Severity.LOW, "REMOVE_TOKENS");
        TestingRunIssues issue6 = generateTestResultIssue(url2, "POST", GlobalEnums.Severity.MEDIUM, "MUST_CONTAIN_RESPONSE_HEADERS");
        
        TestingRunIssuesDao.instance.insertMany(Arrays.asList(issue1, issue2, issue3, issue4, issue5, issue6));
        
        RiskScoreOfCollections riskScoreOfCollections = new RiskScoreOfCollections();
        
        Thread.sleep(1000);
        riskScoreOfCollections.updateSeverityScoreInApiInfo(ts);
        ts = Context.now();

        Bson filter1 = Filters.and(
            Filters.in("_id.url", url1),
            Filters.in("_id.method", method1)
        );

        Bson filter2 = Filters.and(
            Filters.in("_id.url", url2),
            Filters.in("_id.method", method2)
        );

        Bson filter3 = Filters.and(
            Filters.in("_id.url", url3),
            Filters.in("_id.method", method3)
        );

        ApiInfo apiInfo1 = ApiInfoDao.instance.findOne(filter1);
        assertEquals((double) 101, apiInfo1.getSeverityScore(), 0);

        ApiInfo apiInfo2 = ApiInfoDao.instance.findOne(filter2);
        assertEquals((double) 11, apiInfo2.getSeverityScore(), 0);

        ApiInfo apiInfo3 = ApiInfoDao.instance.findOne(filter3);
        assertEquals((double) 20, apiInfo3.getSeverityScore(), 0);

        Bson update = Updates.combine(Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, GlobalEnums.TestRunIssueStatus.FIXED),
            Updates.set(TestingRunIssues.LAST_SEEN, Context.now())
        );
        
        IssuesAction issuesAction = new IssuesAction();
        issuesAction.setIssueId(issue1.getId());
        issuesAction.setStatusToBeUpdated(GlobalEnums.TestRunIssueStatus.FIXED);
        issuesAction.setIgnoreReason("Fixed");

        String resp1 = issuesAction.updateIssueStatus();
        assertEquals(resp1, "SUCCESS");

        TestingRunIssuesDao.instance.updateOne(Filters.in("_id", issue3.getId()), update);
        TestingRunIssuesDao.instance.updateOne(Filters.in("_id", issue5.getId()), update);

        Thread.sleep(1000);
        riskScoreOfCollections.updateSeverityScoreInApiInfo(ts);
        ts = Context.now();

        ApiInfo apiInfo4 = ApiInfoDao.instance.findOne(filter1);
        assertEquals((double) 1, apiInfo4.getSeverityScore(), 0);

        ApiInfo apiInfo5 = ApiInfoDao.instance.findOne(filter2);
        assertEquals((double) 10, apiInfo5.getSeverityScore(), 0);

        ApiInfo apiInfo6 = ApiInfoDao.instance.findOne(filter3);
        assertEquals((double) 10, apiInfo6.getSeverityScore(), 0);

        TestingRunIssues issue7 = generateTestResultIssue(url2, "POST", GlobalEnums.Severity.MEDIUM, "CSRF_LOGIN_ATTACK");
        TestingRunIssuesDao.instance.insertOne(issue7);

        riskScoreOfCollections = new RiskScoreOfCollections();

        Thread.sleep(1000);
        riskScoreOfCollections.updateSeverityScoreInApiInfo(ts);
        ts = Context.now();

        ApiInfo apiInfo7 = ApiInfoDao.instance.findOne(filter2);
        assertEquals((double) 20 , apiInfo7.getSeverityScore(), 0);


    }
}
