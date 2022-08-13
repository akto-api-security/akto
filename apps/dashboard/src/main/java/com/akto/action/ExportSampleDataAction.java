package com.akto.action;

import com.akto.DaoInit;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.parsers.HttpCallParser;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.ConnectionString;

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
        DaoInit.init(new ConnectionString("mongodb://172.18.0.2:27017/admini"));
        Context.accountId.set(1_000_000);
        ApiCollection apiCollection = ApiCollectionsDao.instance.findOne("_id", 0);
        if (apiCollection == null) {
            Set<String> urls = new HashSet<>();
            for(SingleTypeInfo singleTypeInfo: SingleTypeInfoDao.instance.fetchAll()) {
                urls.add(singleTypeInfo.getUrl());
            }
            ApiCollectionsDao.instance.insertOne(new ApiCollection(0, "Default", Context.now(), urls, null,0));
        }
    }

    private String burpRequest;
    public String generateBurpRequest() {
        if (sampleData == null) {
            addActionError("Invalid collection name");
            return ERROR.toUpperCase();
        }

        OriginalHttpRequest originalHttpRequest = new OriginalHttpRequest();
        originalHttpRequest.buildFromSampleMessage(sampleData);

        StringBuilder builder = new StringBuilder("");

        // METHOD and PATH
        builder.append(originalHttpRequest.getMethod()).append(" ").append(originalHttpRequest.getUrl()).append("\n");

        // HEADERS
        Map<String, List<String>> headers = originalHttpRequest.getHeaders();
        for (String headerName: headers.keySet()) {
            List<String> values = headers.get(headerName);
            if (values == null || values.isEmpty() || headerName.length()<1) continue;
            String prettyHeaderName = headerName.substring(0, 1).toUpperCase() + headerName.substring(1);
            String value = String.join(",", values);
            builder.append(prettyHeaderName).append(": ").append(value);
            builder.append("\n");
        }

        // BODY
        builder.append("\n");
        builder.append(originalHttpRequest.getBody());

        burpRequest = builder.toString();

        return SUCCESS.toUpperCase();
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
}

