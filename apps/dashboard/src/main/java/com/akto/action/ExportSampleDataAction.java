package com.akto.action;

import com.akto.DaoInit;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.parsers.HttpCallParser;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;

import java.io.IOException;
import java.util.*;

public class ExportSampleDataAction extends UserAction {
    private final static ObjectMapper mapper = new ObjectMapper();
    JsonFactory factory = mapper.getFactory();
    @Override
    public String execute() {
        return SUCCESS.toUpperCase();
    }

    public static void main(String[] args) throws IOException {
        DaoInit.init(new ConnectionString("mongodb://localhost:27017/admini"));
        Context.accountId.set(1_000_000);
        List<SampleData> sampleDataList = SampleDataDao.instance.findAll(Filters.and(
                Filters.eq("_id.apiCollectionId", 1661402334),
                Filters.eq("_id.method", "GET")
        ));

        for (SampleData s: sampleDataList) {
            List<String> samples = s.getSamples();
            if (samples.size() < 1) continue;
            String url = s.getId().getUrl();
            String msg = samples.get(0);
//            String req = generateBurpRequestFromSampleData(msg);
        }

    }

    private String collectionName;
    private String lastFetchedUrl;
    private String lastFetchedMethod;
    private int limit;
    private final List<BasicDBObject> importInBurpResult = new ArrayList<>();
    public String importInBurp() {
        if (limit <= 0 || limit > 100 ) limit = 100;
        ApiCollection apiCollection = ApiCollectionsDao.instance.findOne(Filters.eq(ApiCollection.NAME, collectionName));

        if (apiCollection == null) {
            addActionError("Invalid collection");
            return ERROR.toUpperCase();
        }

        int apiCollectionId = apiCollection.getId();

//        String resp = "HTTP/1.1 200 OK\n" +
//                "Date: Tue, 23 Aug 2022 16:44:15 GMT\n" +
//                "Content-Type: application/json\n" +
//                "Connection: keep-alive\n" +
//                "Access-Control-Allow-Origin: *\n" +
//                "Access-Control-Allow-Methods: GET, POST, DELETE, PUT\n" +
//                "Access-Control-Allow-Headers: Content-Type, api_key, Authorization\n" +
//                "Server: Jetty(9.2.9.v20150224)\n" +
//                "\n" +
//                "{\"id\":10,\"category\":{\"id\":10,\"name\":\"ZAP\"},\"name\":\"ZAP\",\"photoUrls\":[\"John Doe\"],\"tags\":[{\"id\":10,\"name\":\"ZAP\"}],\"status\":\"thishouldnotexistandhopefullyitwillnot\"}\n";

        // todo: handle parmeterised url
        // todo: send actual url
        // todo: send original response

        List<SampleData> sampleDataList = SampleDataDao.instance.fetchSampleDataPaginated(apiCollectionId, lastFetchedUrl, lastFetchedMethod, limit);
        System.out.println(sampleDataList.size());
        for (SampleData s: sampleDataList) {
            List<String> samples = s.getSamples();
            if (samples.size() < 1) continue;
            String msg = samples.get(0);
            Map<String, String> burpRequestFromSampleData = generateBurpRequestFromSampleData(msg);
            String url = burpRequestFromSampleData.get("url");
            String req = burpRequestFromSampleData.get("request");
            String resp = generateBurpResponseFromSampleData(msg);
            BasicDBObject res = new BasicDBObject();
            res.put("url", url);
            res.put("req", req);
            res.put("res", resp);
            importInBurpResult.add(res);
        }

        return SUCCESS.toUpperCase();
    }

    private String burpRequest;
    public String generateBurpRequest() {
        if (sampleData == null) {
            addActionError("Invalid sample data");
            return ERROR.toUpperCase();
        }

        Map<String, String> result = generateBurpRequestFromSampleData(sampleData);
        burpRequest = result.get("request");
        return SUCCESS.toUpperCase();
    }


    private Map<String, String> generateBurpRequestFromSampleData(String sampleData) {
        OriginalHttpRequest originalHttpRequest = new OriginalHttpRequest();
        originalHttpRequest.buildFromSampleMessage(sampleData);

        StringBuilder builder = new StringBuilder("");

        String url = originalHttpRequest.getFullUrlWithParams();

        if (!url.startsWith("http")) {
            String host = originalHttpRequest.findHostFromHeader();
            String protocol = originalHttpRequest.findProtocolFromHeader();
            try {
                url = OriginalHttpRequest.makeUrlAbsolute(url,host, protocol);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
        System.out.println(url);

        // METHOD and PATH
        builder.append(originalHttpRequest.getMethod()).append(" ")
                .append(url).append(" ")
                .append(originalHttpRequest.getType())
                .append("\n");

        // HEADERS
        addHeadersBurp(originalHttpRequest.getHeaders(), builder);

        builder.append("\n");

        // BODY
        builder.append(originalHttpRequest.getBody());

        Map<String, String> result = new HashMap<>();
        result.put("request", builder.toString());
        result.put("url", url);

        return result;
    }

    private String generateBurpResponseFromSampleData(String sampleData) {
        OriginalHttpResponse originalHttpResponse = new OriginalHttpResponse();
        originalHttpResponse.buildFromSampleMessage(sampleData);

        StringBuilder builder = new StringBuilder("");

        // HTTP type
        builder.append("HTTP/1.1 " + originalHttpResponse.getStatusCode() + " OK").append(" ")
                .append("\n");

        // Headers
        addHeadersBurp(originalHttpResponse.getHeaders(), builder);

        builder.append("\n");

        // Body
        builder.append(originalHttpResponse.getBody());

        return builder.toString();
    }

    private  void addHeadersBurp(Map<String, List<String>> headers, StringBuilder builder) {
        for (String headerName: headers.keySet()) {
            List<String> values = headers.get(headerName);
            if (values == null || values.isEmpty() || headerName.length()<1) continue;
            String prettyHeaderName = headerName.substring(0, 1).toUpperCase() + headerName.substring(1);
            String value = String.join(",", values);
            builder.append(prettyHeaderName).append(": ").append(value);
            builder.append("\n");
        }
    }

    private String curlString;
    private String sampleData;
    public String generateCurl() {
        HttpResponseParams httpResponseParams;
        try {
            httpResponseParams = HttpCallParser.parseKafkaMessage(sampleData);
        } catch (Exception e) {
            e.printStackTrace();
            addActionError("Couldn't parse the data");
            return ERROR.toUpperCase();
        }

        HttpRequestParams httpRequestParams = httpResponseParams.getRequestParams();

        StringBuilder builder = new StringBuilder("curl -v ");

        // Method
        builder.append("-X ").append(httpRequestParams.getMethod()).append(" \\\n  ");

        String hostName = null;
        // Headers
        for (Map.Entry<String, List<String>> entry : httpRequestParams.getHeaders().entrySet()) {
            if (entry.getKey().equalsIgnoreCase("host") && entry.getValue().size() > 0) {
                hostName = entry.getValue().get(0);
            }
            builder.append("-H '").append(entry.getKey()).append(":");
            for (String value : entry.getValue()) {
                builder.append(" ").append(value.replaceAll("\"", "\\\\\""));
            }
            builder.append("' \\\n  ");
        }

        String urlString;
        String path = httpRequestParams.getURL();
        if (hostName != null && !(path.toLowerCase().startsWith("http") || path.toLowerCase().startsWith("www."))) {
            urlString = path.startsWith("/") ? hostName + path : hostName + "/" + path;
        } else {
            urlString = path;
        }

        StringBuilder url = new StringBuilder(urlString);

        // Body
        try {
            String payload = httpRequestParams.getPayload();
            if (payload == null) payload = "";
            boolean curlyBracesCond = payload.startsWith("{") && payload.endsWith("}");
            boolean squareBracesCond = payload.startsWith("[") && payload.endsWith("]");
            if (curlyBracesCond || squareBracesCond) {
                if (!Objects.equals(httpRequestParams.getMethod(), "GET")) {
                    builder.append("-d '").append(payload).append("' \\\n  ");
                } else {
                    JsonParser jp = factory.createParser(payload);
                    JsonNode node = mapper.readTree(jp);
                    if (node != null) {
                        Iterator<String> fieldNames = node.fieldNames();
                        boolean flag =true;
                        while(fieldNames.hasNext()) {
                            String fieldName = fieldNames.next();
                            JsonNode fieldValue = node.get(fieldName);
                            if (fieldValue.isValueNode()) {
                                if (flag) {
                                    url.append("?").append(fieldName).append("=").append(fieldValue.asText());
                                    flag = false;
                                } else {
                                    url.append("&").append(fieldName).append("=").append(fieldValue.asText());
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            addActionError("Error parsing the body");
            return ERROR.toUpperCase();
        }


        // URL
        builder.append("\"").append(url).append("\"");

        curlString = builder.toString();

        return SUCCESS.toUpperCase();
    }

    public String getCurlString() {
        return curlString;
    }

    public void setSampleData(String sampleData) {
        this.sampleData = sampleData;
    }

    public String getBurpRequest() {
        return burpRequest;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public List<BasicDBObject> getImportInBurpResult() {
        return importInBurpResult;
    }

    public void setLastFetchedUrl(String lastFetchedUrl) {
        this.lastFetchedUrl = lastFetchedUrl;
    }

    public void setLastFetchedMethod(String lastFetchedMethod) {
        this.lastFetchedMethod = lastFetchedMethod;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}

