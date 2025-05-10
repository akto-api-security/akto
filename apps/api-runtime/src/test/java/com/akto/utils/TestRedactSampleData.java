package com.akto.utils;

import com.akto.dao.CustomDataTypeDao;
import com.akto.dto.CustomDataType;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.parsers.HttpCallParser;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.mongodb.BasicDBObject;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertEquals;

public class TestRedactSampleData {

    static ObjectMapper mapper = new ObjectMapper();
    static JsonFactory factory = mapper.getFactory();

    private Set<String> extractKeys(String payload) {
        JsonParser jp = null;
        JsonNode node = null;
        try {
            jp = factory.createParser(payload);
            node = mapper.readTree(jp);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Map<String,Set<String>> requestParamMap = new HashMap<>();
        extractKeys(node,new ArrayList<>(), requestParamMap);

        return requestParamMap.keySet();
    }

    private void extractKeys(JsonNode node, List<String> params, Map<String, Set<String>> values) {
        if (node == null) return;
        if (node.isValueNode()) {
            String textValue = node.asText();
            String param = String.join("",params);
            if (param.startsWith("#")) {
                param = param.substring(1);
            }
            if (!values.containsKey(param)) {
                values.put(param, new HashSet<>());
            }
            values.get(param).add(textValue);
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for(int i = 0; i < arrayNode.size(); i++) {
                JsonNode arrayElement = arrayNode.get(i);
                params.add("#$");
                extractKeys(arrayElement, params, values);
                params.remove(params.size()-1);
            }
        } else {
            Iterator<String> fieldNames = node.fieldNames();
            while(fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                params.add("#"+fieldName);
                JsonNode fieldValue = node.get(fieldName);
                extractKeys(fieldValue, params,values);
                params.remove(params.size()-1);
            }
        }

    }

    private boolean testRedactDoneCorrect(HttpResponseParams original, HttpResponseParams redacted) throws IOException {
        HttpRequestParams originalHttpRequestParams = original.requestParams;
        HttpRequestParams redactedHttpRequestParams = redacted.requestParams;

        boolean result1 = originalHttpRequestParams.url.equals(redactedHttpRequestParams.url) &&
                originalHttpRequestParams.method.equals(redactedHttpRequestParams.method) &&
                originalHttpRequestParams.type.equals(redactedHttpRequestParams.type);

        if (!result1) return false;

        boolean result2 = original.type.equals(redacted.type) &&
                original.statusCode == redacted.statusCode &&
                Objects.equals(original.status, redacted.status) &&
                original.getTime() == redacted.getTime();

        if (!result2) return false;

        Set<String> originalHeaderNamesFromRequest = originalHttpRequestParams.getHeaders().keySet();
        Set<String> redactedHeaderNamesFromRequest = redactedHttpRequestParams.getHeaders().keySet();

        boolean result3 = originalHeaderNamesFromRequest.equals(redactedHeaderNamesFromRequest);
        if (!result3) return false;

        Set<String> originalHeaderNamesFromResponse = original.getHeaders().keySet();
        Set<String> redactedHeaderNamesFromResponse = redacted.getHeaders().keySet();

        boolean result4 = originalHeaderNamesFromResponse.equals(redactedHeaderNamesFromResponse);
        if (!result4) return false;

        // test if key names in payload have not been changed
        Set<String> redactedReqKeys = extractKeys(redacted.requestParams.getPayload());
        Set<String> originalReqKeys = extractKeys(original.requestParams.getPayload());
        if (!redactedReqKeys.equals(originalReqKeys)) {
            System.out.println(redactedReqKeys);
            System.out.println(originalReqKeys);
            return false;
        }

        Set<String> redactedRespKeys = extractKeys(redacted.getPayload());
        Set<String> originalRespKeys = extractKeys(original.getPayload());
        if (!redactedRespKeys.equals(originalRespKeys)) {
            System.out.println(redactedRespKeys);
            System.out.println(originalRespKeys);
            return false;
        }


        // test if all values have been hidden

        //   1. For headers
        for (List<String> val: redacted.getHeaders().values()) {
            if (val.size() > 1) return false;
            if (val.size() == 1 && !Objects.equals(val.get(0), RedactSampleData.redactValue)) return false;
        }

        for (List<String> val: redacted.requestParams.getHeaders().values()) {
            if (val.size() > 1) return false;
            if (val.size() == 1 && !Objects.equals(val.get(0), RedactSampleData.redactValue)) return false;
        }

        //   2. For Payload
        String responsePayload = redacted.getPayload();
        JsonParser jp = factory.createParser(responsePayload);
        JsonNode node = mapper.readTree(jp);
        boolean result5 = checkRedactPayload(node);
        if (!result5) return false;

        String requestPayload = redacted.getRequestParams().getPayload();
        jp = factory.createParser(requestPayload);
        node = mapper.readTree(jp);
        boolean result6 = checkRedactPayload(node);
        if (!result6) return false;

        //   3. For IP
        if (!Objects.equals(redacted.getSourceIP(), RedactSampleData.redactValue)) return false;

        return true;
    }

    public boolean checkRedactPayload(JsonNode parent) {
        if (parent == null) return true;

        if (parent.isArray()) {
            ArrayNode arrayNode = (ArrayNode) parent;
            for(int i = 0; i < arrayNode.size(); i++) {
                JsonNode arrayElement = arrayNode.get(i);
                boolean result = false;
                if (arrayElement.isValueNode()) {
                    result = arrayElement.textValue().equals(RedactSampleData.redactValue);
                    if (!result) return false;
                } else {
                    result = checkRedactPayload(arrayElement);
                    if (!result) return false;
                }
            }
        } else {
            Iterator<String> fieldNames = parent.fieldNames();
            while(fieldNames.hasNext()) {
                boolean result = false;
                String f = fieldNames.next();
                JsonNode fieldValue = parent.get(f);
                if (fieldValue.isValueNode()) {
                    result = fieldValue.textValue().equals(RedactSampleData.redactValue);
                    if (!result) return false;
                } else {
                    result = checkRedactPayload(fieldValue);
                    if (!result) return false;
                }
            }
        }

        return true;
    }

    @Test
    public void redactNonJsonPayload() throws Exception {
        Map<String, List<String>> reqHeaders = new HashMap<>();
        reqHeaders.put("header1", Arrays.asList("valueA1","valueB1","valueC1"));
        reqHeaders.put("header2", Arrays.asList("valueA1","valueB1","valueC1"));

        Map<String, List<String>> respHeaders = new HashMap<>();
        respHeaders.put("header3", Arrays.asList("valueA1","valueB1","valueC1"));

        String reqPayload = "something random";

        HttpRequestParams httpRequestParams = new HttpRequestParams(
                "GET", "/api/books", "type",reqHeaders, reqPayload, 0
        );

        String respPayload = "random response payload";

        HttpResponseParams httpResponseParams = new HttpResponseParams(
                "type", 200, "OK", respHeaders, respPayload, httpRequestParams, 0, "1000000", false, HttpResponseParams.Source.MIRRORING, "orig","172.0.0.1"
        );

        String redactedValue = RedactSampleData.redact(httpResponseParams, true);

        HttpResponseParams redactedHttpResponseParams = HttpCallParser.parseKafkaMessage(redactedValue);

        assertEquals(2, redactedHttpResponseParams.requestParams.getHeaders().size());
        assertEquals(1, redactedHttpResponseParams.getHeaders().size());
        assertEquals("something random", redactedHttpResponseParams.requestParams.getPayload());
        assertEquals("random response payload", redactedHttpResponseParams.getPayload());
        assertEquals(200, redactedHttpResponseParams.statusCode);

    }

    @Test
    public void happy() throws Exception {
//        String sample = "{\"method\":\"POST\",\"requestPayload\":\"{\\\"petId\\\":1,\\\"quantity\\\":0,\\\"id\\\":0,\\\"shipDate\\\":\\\"2022-01-04T20:10:16.578Z\\\",\\\"complete\\\":true,\\\"status\\\":\\\"avnebbesh@akto.io\\\"}\",\"responsePayload\":\"{\\\"id\\\":9223372036854772476,\\\"petId\\\":0,\\\"quantity\\\":0,\\\"shipDate\\\":\\\"2022-01-04T20:10:16.578+0000\\\",\\\"status\\\":\\\"av@gmail.com\\\",\\\"complete\\\":true, \\\"avav\\\": \\\"eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoicmVmcmVzaFRva2VuIiwic2lnbmVkVXAiOiJ0cnVlIiwidXNlcm5hbWUiOiJhbmtpdGFAZ21haWwuY29tIiwiaWF0IjoxNjM0OTcxMTMxLCJleHAiOjE2MzUwNTc1MzF9.Ph4Jv-fdggwvnbdVViD9BWUReYL0dVfVGuMRz4d2oZNnYzWV0JCmjpB68p6k0yyPPua_yagIWVZf_oYH9PUgS7EuaPYR-Vg6uxKR1HuXRA6wb8Xf4RPoFjJYkhWoYmv38V9Cz2My9U85wgGHGZXEufu8ubrFmIfOP6-A39M4meNGw48f5oOz8V337SX45uPc6jE0EfmM4l9EbqFFCF0lRXbMMzn-ijsyXxLkI5npWnqtW3PAHC2Rs3FV40tkRqHYF-WM6SzyHLBh6bVeyeOsFRBoEjv-zFh8yrYnT6OvCa6jII2A6uj4MQ2k11-5bDBhfVPVc4hEQz37H_DWwtf23g\\\"}\",\"ip\":\"null\",\"source\":\"HAR\",\"type\":\"HTTP/2\",\"akto_vxlan_id\":\"0\",\"path\":\"https://petstore.swagger.io/v2/store/order/1\",\"requestHeaders\":\"{\\\"Origin\\\":\\\"avneesh@gmail.com\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"TE\\\":\\\"trailers\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Content-Length\\\":\\\"128\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"+917021916328\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\",\"time\":\"1641327021\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";

        Map<String, List<String>> reqHeaders = new HashMap<>();
        reqHeaders.put("header1", Arrays.asList("valueA1","valueB1","valueC1"));
        reqHeaders.put("header2", Collections.singletonList("valueA2"));
        reqHeaders.put("header3", Collections.emptyList());

        Map<String, List<String>> respHeaders = new HashMap<>();
        respHeaders.put("header4", Arrays.asList("valueA1","valueB1","valueC1"));
        respHeaders.put("header5", Collections.singletonList("valueA2"));
        respHeaders.put("header6", Collections.emptyList());

        Map<String,Object> reqPayloadMap = new HashMap<>();
        reqPayloadMap.put("req-name", "Avneesh");
        reqPayloadMap.put("req-email", "avneesh@akto.io");
        reqPayloadMap.put("req-id", 12345);
        reqPayloadMap.put("req-teams", Arrays.asList("A", "B", "C"));
        reqPayloadMap.put("req-privateJet", "null");
        reqPayloadMap.put("req-bikes", Collections.emptyList());

        BasicDBObject friend1 = new BasicDBObject();
        friend1.append("name", "Ankush").append("email", "ankush@akto.io").append("teams", Arrays.asList("X","Y"));

        BasicDBObject friend2 = new BasicDBObject();
        friend2.append("name", "Ankita").append("email", "ankita@akto.io").append("teams", Collections.singletonList("X"));

        reqPayloadMap.put("friends", Arrays.asList(friend1, friend2));

        String reqPayload = mapper.writeValueAsString(reqPayloadMap);

        HttpRequestParams httpRequestParams = new HttpRequestParams(
                "GET", "/api/books", "type",reqHeaders, reqPayload, 0
        );

        Map<String,Object> respPayloadMap = new HashMap<>();
        respPayloadMap.put("name", "avneesh");
        respPayloadMap.put("email", "avneesh@akto.io");
        respPayloadMap.put("success", true);
        respPayloadMap.put("id", 12345);
        respPayloadMap.put("teams", Arrays.asList("A", "B", "C"));
        respPayloadMap.put("privateJet", "null");
        respPayloadMap.put("bikes", Collections.emptyList());
        respPayloadMap.put("friends", Arrays.asList(friend1, friend2));

        String respPayload = mapper.writeValueAsString(respPayloadMap);

        HttpResponseParams httpResponseParams = new HttpResponseParams(
                "type", 200, "OK", respHeaders, respPayload, httpRequestParams, 0, "1000000", false, HttpResponseParams.Source.MIRRORING, "orig","172.0.0.1"
        );

        String originalString = RedactSampleData.convertHttpRespToOriginalString(httpResponseParams);
        HttpResponseParams originalHttpResponseParams = HttpCallParser.parseKafkaMessage(originalString);


        String redactedValue = RedactSampleData.redact(httpResponseParams, true);

        HttpResponseParams redactedHttpResponseParams = HttpCallParser.parseKafkaMessage(redactedValue);

        try {
            boolean result = testRedactDoneCorrect(originalHttpResponseParams, redactedHttpResponseParams);
            Assertions.assertTrue(result);
        } catch (Exception e) {
            e.printStackTrace();
            Assertions.fail();
        }

    }

    @Test
    public void testRedactSampleDataForXMLPayload() throws Exception{
        String messageString = "{\"akto_account_id\":\"1111\",\"contentType\":\"application/json;charset=utf-8\",\"ip\":\"127.0.0.1:48940\",\"method\":\"GET\",\"path\":\"/api/books\",\"requestHeaders\":\"{\\\"Accept\\\":[\\\"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8\\\"],\\\"Accept-Encoding\\\":[\\\"gzip, deflate\\\"],\\\"Accept-Language\\\":[\\\"en-US,en;q=0.5\\\"],\\\"Cache-Control\\\":[\\\"no-cache\\\"],\\\"Connection\\\":[\\\"keep-alive\\\"],\\\"Cookie\\\":[\\\"G_ENABLED_IDPS=google\\\"],\\\"Pragma\\\":[\\\"no-cache\\\"],\\\"Sec-Fetch-Dest\\\":[\\\"document\\\"],\\\"Sec-Fetch-Mode\\\":[\\\"navigate\\\"],\\\"Sec-Fetch-Site\\\":[\\\"cross-site\\\"],\\\"Upgrade-Insecure-Requests\\\":[\\\"1\\\"],\\\"User-Agent\\\":[\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:94.0) Gecko/20100101 Firefox/94.0\\\"]}\",\"requestPayload\":\"\",\"responseHeaders\":\"{\\\"Content-Type\\\":[\\\"text/xml;charset=utf-8\\\"]}\",\"responsePayload\":\"<?xml version='1.0' Encoding='UTF-8' ?>\\r\\n" + //
        "<env:Envelope xmlns:env=\\\"http:\\/\\/www.w3.org\\/2003\\/05\\/soap-envelope\\\"> \\r\\n" + //
        " <env:Header>\\r\\n" + //
        "  <m:reservation xmlns:m=\\\"http:\\/\\/travelcompany.example.org\\/reservation\\\" \\r\\n" + //
        "\\t\\tenv:role=\\\"http:\\/\\/www.w3.org\\/2003\\/05\\/soap-envelope\\/role\\/next\\\">\\r\\n" + //
        "   <m:reference>uuid:093a2da1-q345-739r-ba5d-pqff98fe8j7d<\\/m:reference>\\r\\n" + //
        "   <m:dateAndTime>2007-11-29T13:20:00.000-05:00<\\/m:dateAndTime>\\r\\n" + //
        "  <\\/m:reservation>\\r\\n" + //
        "  <n:passenger xmlns:n=\\\"http:\\/\\/mycompany.example.com\\/employees\\\" \\r\\n" + //
        "\\t\\tenv:role=\\\"http:\\/\\/www.w3.org\\/2003\\/05\\/soap-envelope\\/role\\/next\\\">\\r\\n" + //
        "   <n:name>Fred Bloggs<\\/n:name>\\r\\n" + //
        "  <\\/n:passenger>\\r\\n" + //
        " <\\/env:Header>\\r\\n" + //
        " <env:Body>\\r\\n" + //
        "  <p:itinerary xmlns:p=\\\"http:\\/\\/travelcompany.example.org\\/reservation\\/travel\\\">\\r\\n" + //
        "   <p:departure>\\r\\n" + //
        "     <p:departing>New York<\\/p:departing>\\r\\n" + //
        "     <p:arriving>Los Angeles<\\/p:arriving>\\r\\n" + //
        "     <p:departureDate>2007-12-14<\\/p:departureDate>\\r\\n" + //
        "     <p:departureTime>late afternoon<\\/p:departureTime>\\r\\n" + //
        "     <p:seatPreference>aisle<\\/p:seatPreference>\\r\\n" + //
        "   <\\/p:departure>\\r\\n" + //
        "   <p:return>\\r\\n" + //
        "     <p:departing>Los Angeles<\\/p:departing>\\r\\n" + //
        "     <p:arriving>New York<\\/p:arriving>\\r\\n" + //
        "     <p:departureDate>2007-12-20<\\/p:departureDate>\\r\\n" + //
        "     <p:departureTime>mid-morning<\\/p:departureTime>\\r\\n" + //
        "     <p:seatPreference><\\/p:seatPreference>\\r\\n" + //
        "   <\\/p:return>\\r\\n" + //
        "  <\\/p:itinerary>\\r\\n" + //
        " <\\/env:Body>\\r\\n" + //
        "<\\/env:Envelope>\n" + //
        "\",\"status\":\"null\",\"statusCode\":\"201\",\"time\":\"1638223603\",\"type\":\"HTTP/1.1\"}";

        HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(messageString);
        String redactedValue = RedactSampleData.redact(httpResponseParams, true);
        assertEquals(true, redactedValue.contains("<?xml version='1.0' Encoding='UTF-8' ?>"));
    }
}
