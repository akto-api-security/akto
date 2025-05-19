package com.akto.parsers;

import com.akto.dto.HttpResponseParams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.akto.util.HttpRequestResponseUtils.rawToJsonString;
import static com.akto.util.HttpRequestResponseUtils.updateXmlWithModifiedJson;
import static org.junit.jupiter.api.Assertions.*;

public class KafkaParserTest {
    private final static Gson gson = new Gson();

    @Test
    public void testHappyPath() {
        String message = "{\"akto_account_id\":\"1111\",\"contentType\":\"application/json;charset=utf-8\",\"ip\":\"127.0.0.1:48940\",\"method\":\"GET\",\"path\":\"/api/books\",\"requestHeaders\":\"{\\\"Accept\\\":[\\\"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8\\\"],\\\"Accept-Encoding\\\":[\\\"gzip, deflate\\\"],\\\"Accept-Language\\\":[\\\"en-US,en;q=0.5\\\"],\\\"Cache-Control\\\":[\\\"no-cache\\\"],\\\"Connection\\\":[\\\"keep-alive\\\"],\\\"Cookie\\\":[\\\"G_ENABLED_IDPS=google\\\"],\\\"Pragma\\\":[\\\"no-cache\\\"],\\\"Sec-Fetch-Dest\\\":[\\\"document\\\"],\\\"Sec-Fetch-Mode\\\":[\\\"navigate\\\"],\\\"Sec-Fetch-Site\\\":[\\\"cross-site\\\"],\\\"Upgrade-Insecure-Requests\\\":[\\\"1\\\"],\\\"User-Agent\\\":[\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:94.0) Gecko/20100101 Firefox/94.0\\\"]}\",\"requestPayload\":\"\",\"responseHeaders\":\"{\\\"Content-Type\\\":[\\\"application/json;charset=utf-8\\\"]}\",\"responsePayload\":\"[{\\\"id\\\":\\\"1\\\",\\\"isbn\\\":\\\"3223\\\",\\\"title\\\":\\\"Book 1\\\",\\\"author\\\":{\\\"firstname\\\":\\\"Avneesh\\\",\\\"lastname\\\":\\\"Hota\\\"}},{\\\"id\\\":\\\"2\\\",\\\"isbn\\\":\\\"2323\\\",\\\"title\\\":\\\"Book 2\\\",\\\"author\\\":{\\\"firstname\\\":\\\"Ankush\\\",\\\"lastname\\\":\\\"Jain\\\"}}]\\n\",\"status\":\"null\",\"statusCode\":\"201\",\"time\":\"1638223603\",\"type\":\"HTTP/1.1\"}";
        HttpResponseParams httpResponseParams = null;
        try {
            httpResponseParams = HttpCallParser.parseKafkaMessage(message);
            assertEquals("/api/books",httpResponseParams.getRequestParams().getURL());
        } catch (Exception e) {
            e.printStackTrace();
        }
        assertNotNull(httpResponseParams);
        assertEquals(1, httpResponseParams.getHeaders().size());
        assertEquals(12, httpResponseParams.requestParams.getHeaders().size());

    }

    @Test
    public void testHappySoapPath() {
        String message = "{\"akto_account_id\":\"1111\",\"contentType\":\"application/json;charset=utf-8\",\"ip\":\"127.0.0.1:48940\",\"method\":\"GET\",\"path\":\"/api/books\",\"requestHeaders\":\"{\\\"Accept\\\":[\\\"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8\\\"],\\\"Accept-Encoding\\\":[\\\"gzip, deflate\\\"],\\\"Accept-Language\\\":[\\\"en-US,en;q=0.5\\\"],\\\"Cache-Control\\\":[\\\"no-cache\\\"],\\\"Connection\\\":[\\\"keep-alive\\\"],\\\"Cookie\\\":[\\\"G_ENABLED_IDPS=google\\\"],\\\"Pragma\\\":[\\\"no-cache\\\"],\\\"Sec-Fetch-Dest\\\":[\\\"document\\\"],\\\"Sec-Fetch-Mode\\\":[\\\"navigate\\\"],\\\"Sec-Fetch-Site\\\":[\\\"cross-site\\\"],\\\"Upgrade-Insecure-Requests\\\":[\\\"1\\\"],\\\"User-Agent\\\":[\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:94.0) Gecko/20100101 Firefox/94.0\\\"]}\",\"requestPayload\":\"\",\"responseHeaders\":\"{\\\"Content-Type\\\":[\\\"text/xml;charset=utf-8\\\"]}\",\"responsePayload\":\"<?xml version='1.0' Encoding='UTF-8' ?>\\r\\n" + //
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
        HttpResponseParams httpResponseParams = null;
        try {
            httpResponseParams = HttpCallParser.parseKafkaMessage(message);
            assertEquals("/api/books",httpResponseParams.getRequestParams().getURL());
        } catch (Exception e) {
            e.printStackTrace();
        }
        assertNotNull(httpResponseParams);
        assertEquals(1, httpResponseParams.getHeaders().size());
        assertEquals(12, httpResponseParams.requestParams.getHeaders().size());
        assertTrue(BasicDBObject.parse(httpResponseParams.getPayload()).size() > 0);

    }


    @Test
    public void testEmptyHeader() {
        String message = "{\"akto_account_id\":\"1111\",\"contentType\":\"application/json;charset=utf-8\",\"ip\":\"127.0.0.1:48940\",\"method\":\"GET\",\"path\":\"/api/books\",\"requestHeaders\":\"{}\",\"requestPayload\":\"\",\"responseHeaders\":\"{\\\"Content-Type\\\":[\\\"application/json;charset=utf-8\\\"]}\",\"responsePayload\":\"[{\\\"id\\\":\\\"1\\\",\\\"isbn\\\":\\\"3223\\\",\\\"title\\\":\\\"Book 1\\\",\\\"author\\\":{\\\"firstname\\\":\\\"Avneesh\\\",\\\"lastname\\\":\\\"Hota\\\"}},{\\\"id\\\":\\\"2\\\",\\\"isbn\\\":\\\"2323\\\",\\\"title\\\":\\\"Book 2\\\",\\\"author\\\":{\\\"firstname\\\":\\\"Ankush\\\",\\\"lastname\\\":\\\"Jain\\\"}}]\\n\",\"status\":\"null\",\"statusCode\":\"201\",\"time\":\"1638223603\",\"type\":\"HTTP/1.1\"}";
        HttpResponseParams httpResponseParams = null;
        try {
            httpResponseParams = HttpCallParser.parseKafkaMessage(message);
            assertEquals(0,httpResponseParams.getRequestParams().getHeaders().keySet().size());
        } catch (Exception e) {
            e.printStackTrace();
        }
        assertNotNull(httpResponseParams);

    }

    @Test
    public void testPostQueryString() {
        String message = "{\"method\":\"POST\",\"requestPayload\":\"name=a&status=available\",\"responsePayload\":\"{\\\"code\\\":200,\\\"type\\\":\\\"unknown\\\",\\\"message\\\":\\\"4\\\"}\",\"ip\":\"null\",\"type\":\"HTTP/2\",\"path\":\"https://petstore.swagger.io/v2/pet/4\",\"requestHeaders\":\"{\\\"Origin\\\":\\\"https://petstore.swagger.io\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"TE\\\":\\\"trailers\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Content-Length\\\":\\\"23\\\",\\\"Content-Type\\\":\\\"application/x-www-form-urlencoded\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"Mon, 03 Jan 2022 07:18:20 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\",\"time\":\"1641194300\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        HttpResponseParams httpResponseParams = null;
        try {
            httpResponseParams = HttpCallParser.parseKafkaMessage(message);
            String payload = httpResponseParams.getRequestParams().getPayload();
            Map<String, Object> json = gson.fromJson(payload, Map.class);
            System.out.println(json);
            assertEquals("a", json.get("name"));
            assertEquals("available", json.get("status"));

        } catch (Exception e) {
            e.printStackTrace();
        }
        assertNotNull(httpResponseParams);

    }

    @Test
    public void testJsonToValidXml(){
        Map<String,List<String>> requestHeaders = new HashMap<>();
        requestHeaders.put("content-type", Arrays.asList("text/html,application/xhtml+xml,application/xml"));
        String originalXml = "<env:Envelope xmlns:env=\"http://www.w3.org/2003/05/soap-envelope\">\n" +
                "  <env:Header>\n" +
                "    <m:reservation xmlns:m=\"http://travelcompany.example.org/reservation\"\n" +
                "        env:role=\"http://www.w3.org/2003/05/soap-envelope/role/next\">\n" +
                "      <m:reference>uuid:093a2da1-q345-739r-ba5d-pqff98fe8j7d</m:reference>\n" +
                "      <m:dateAndTime>2007-11-29T13:20:00.000-05:00</m:dateAndTime>\n" +
                "    </m:reservation>\n" +
                "    <n:passenger xmlns:n=\"http://mycompany.example.com/employees\"\n" +
                "        env:role=\"http://www.w3.org/2003/05/soap-envelope/role/next\">\n" +
                "      <n:name>Fred Bloggs</n:name>\n" +
                "    </n:passenger>\n" +
                "  </env:Header>\n" +
                "  <env:Body>\n" +
                "    <p:itinerary xmlns:p=\"http://travelcompany.example.org/reservation/travel\">\n" +
                "      <p:departure>\n" +
                "        <p:departing>New York</p:departing>\n" +
                "        <p:arriving>Los Angeles</p:arriving>\n" +
                "        <p:departureDate>2007-12-14</p:departureDate>\n" +
                "        <p:departureTime>late afternoon</p:departureTime>\n" +
                "        <p:seatPreference>aisle</p:seatPreference>\n" +
                "      </p:departure>\n" +
                "      <p:return>\n" +
                "        <p:departing>Los Angeles</p:departing>\n" +
                "        <p:arriving>New York</p:arriving>\n" +
                "        <p:departureDate>2007-12-20</p:departureDate>\n" +
                "        <p:departureTime>mid-morning</p:departureTime>\n" +
                "        <p:seatPreference></p:seatPreference>\n" +
                "      </p:return>\n" +
                "    </p:itinerary>\n" +
                "  </env:Body>\n" +
                "</env:Envelope>";


        String jsonString = rawToJsonString(originalXml, requestHeaders);
        System.out.println(jsonString);
        assertEquals(true, jsonString.length() > 0);
        try {
            Map<String, Object> json = gson.fromJson(jsonString, Map.class);
            System.out.println(json);
            json.put("aryan", "rocks");
            String modified = gson.toJson(json);
            System.out.println(modified);
            String xmlString = updateXmlWithModifiedJson(originalXml, modified);
            System.out.println(xmlString);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }
}
