package com.akto.action;

import com.akto.DaoInit;
import com.akto.action.test_editor.SaveTestEditorAction;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import com.akto.listener.InitializerListener;
import com.akto.listener.RuntimeListener;
import com.akto.parsers.HttpCallParser;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.sun.jndi.toolkit.url.Uri;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public class ExportSampleDataAction extends UserAction {
    private final static ObjectMapper mapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(ExportSampleDataAction.class);
    private static final JsonFactory factory = mapper.getFactory();
    @Override
    public String execute() {
        return SUCCESS.toUpperCase();
    }

    private String collectionName;
    private String lastUrlFetched;
    private String lastMethodFetched;
    private int limit;
    private final List<BasicDBObject> importInBurpResult = new ArrayList<>();
    public String importInBurp() {
        if (limit <= 0 || limit > 500 ) limit = 500;
        ApiCollection apiCollection = ApiCollectionsDao.instance.findByName(collectionName);

        if (apiCollection == null) {
            addActionError("Invalid collection");
            return ERROR.toUpperCase();
        }

        int apiCollectionId = apiCollection.getId();

        List<SampleData> sampleDataList = SampleDataDao.instance.fetchSampleDataPaginated(apiCollectionId, lastUrlFetched, lastMethodFetched, limit, 1);

        lastMethodFetched = null;
        lastUrlFetched = null;

        for (SampleData s: sampleDataList) {
            List<String> samples = s.getSamples();
            if (samples.size() < 1) continue;

            String msg = samples.get(0);
            Map<String, String> burpRequestFromSampleData = generateBurpRequestFromSampleData(msg, false);
            // use url from the sample data instead of relying on the id
            // this is to handle parameterised URLs
            String url = burpRequestFromSampleData.get("url");
            String req = burpRequestFromSampleData.get("request");
            String httpType = burpRequestFromSampleData.get("type");
            String resp = generateBurpResponseFromSampleData(msg, httpType);

            BasicDBObject res = new BasicDBObject();
            res.put("url", url);
            res.put("req", req);
            res.put("res", resp);

            importInBurpResult.add(res);

            // But for lastUrlFetched we need the id because mongo query uses the one in _id
            lastUrlFetched = s.getId().url;
            lastMethodFetched =  s.getId().method.name();
        }

        return SUCCESS.toUpperCase();
    }

    private String burpRequest;
    public String generateBurpRequest() {
        if (sampleData == null) {
            addActionError("Invalid sample data");
            return ERROR.toUpperCase();
        }

        Map<String, String> result = generateBurpRequestFromSampleData(sampleData, true);
        burpRequest = result.get("request_path");
        return SUCCESS.toUpperCase();
    }


    private Map<String, String> generateBurpRequestFromSampleData(String sampleData, boolean shouldDeleteContentLengthHeader) {
        OriginalHttpRequest originalHttpRequest = new OriginalHttpRequest();
        try {
            originalHttpRequest.buildFromSampleMessage(sampleData);
        } catch (Exception e) {
            originalHttpRequest.buildFromApiSampleMessage(sampleData);
        }

        String url = originalHttpRequest.getFullUrlWithParams();
        String path = originalHttpRequest.getPath();

        if (!url.startsWith("http")) {
            String host = originalHttpRequest.findHostFromHeader();
            String protocol = originalHttpRequest.findProtocolFromHeader();
            try {
                url = OriginalHttpRequest.makeUrlAbsolute(url,host, protocol);
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        } else {
            if (!originalHttpRequest.getHeaders().containsKey("host")) {
                // this is because Cloudfront requires host header else gives 4xx
                try {
                    URI uri = new URI(url);
                    String host = uri.getHost();
                    originalHttpRequest.getHeaders().put("host", Collections.singletonList(host));
                } catch (URISyntaxException e) {
                }
            }
        }

        if(shouldDeleteContentLengthHeader){
            originalHttpRequest.getHeaders().remove("content-length");
        }

        StringBuilder builderWithUrl = buildRequest(originalHttpRequest, url);
        StringBuilder builderWithPath = buildRequest(originalHttpRequest, path);

        Map<String, String> result = new HashMap<>();
        result.put("request", builderWithUrl.toString());
        result.put("url", url);
        result.put("type", originalHttpRequest.getType());
        result.put("request_path", builderWithPath.toString());

        return result;
    }

    private StringBuilder buildRequest(OriginalHttpRequest originalHttpRequest, String url) {
        StringBuilder builder = new StringBuilder("");

        // METHOD and PATH
        builder.append(originalHttpRequest.getMethod())
                .append(" ")
                .append(url);
        
        String queryParams = originalHttpRequest.getQueryParams();
        if (queryParams != null && queryParams.trim().length() > 0) {
            builder.append("?").append(queryParams);
        }

        builder.append(" ").append(originalHttpRequest.getType())
                .append("\n");

        // HEADERS
        addHeadersBurp(originalHttpRequest.getHeaders(), builder);

        builder.append("\n");

        // BODY
        builder.append(originalHttpRequest.getBody());
        return builder;
    }

    private String generateBurpResponseFromSampleData(String sampleData, String httpType) {
        OriginalHttpResponse originalHttpResponse = new OriginalHttpResponse();
        originalHttpResponse.buildFromSampleMessage(sampleData);

        OriginalHttpRequest originalHttpRequest = new OriginalHttpRequest();
        originalHttpRequest.buildFromSampleMessage(sampleData);

        StringBuilder builder = new StringBuilder("");

        // HTTP type
        builder.append(httpType).append(" ").append(originalHttpResponse.getStatusCode()).append(" ").append("\n");

        // Headers
        addHeadersBurp(originalHttpResponse.getHeaders(), builder);

        builder.append("\n");

        // Body
        builder.append(originalHttpResponse.getBody());

        return builder.toString();
    }

    private  void addHeadersBurp(Map<String, List<String>> headers, StringBuilder builder) {
        for (String headerName: headers.keySet()) {
            if (headerName.startsWith(":")) continue; // pseudo-headers need to be removed before sending to burp
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

        try {
            curlString = getCurl(sampleData);
            return SUCCESS.toUpperCase();
        } catch (IOException e) {
            addActionError("Couldn't parse the data");
            return ERROR.toUpperCase();
        }
    }

    public static String getCurl(String sampleData) throws IOException {
        HttpResponseParams httpResponseParams;
        try {
            httpResponseParams = HttpCallParser.parseKafkaMessage(sampleData);
        } catch (Exception e) {
            try {
                OriginalHttpRequest originalHttpRequest = new OriginalHttpRequest();
                originalHttpRequest.buildFromApiSampleMessage(sampleData);

                HttpRequestParams httpRequestParams = new HttpRequestParams(
                        originalHttpRequest.getMethod(), originalHttpRequest.getFullUrlWithParams(), originalHttpRequest.getType(),
                        originalHttpRequest.getHeaders(), originalHttpRequest.getBody(), 0
                );

                httpResponseParams = new HttpResponseParams();
                httpResponseParams.requestParams = httpRequestParams;
            } catch (Exception e1) {
                throw e1;
            }

        }

        HttpRequestParams httpRequestParams = httpResponseParams.getRequestParams();
        StringBuilder builder = new StringBuilder("curl -v ");

        Map<String, List<String>> headers = httpRequestParams.getHeaders();
        List<String> values = headers.get("x-forwarded-proto");
        headers.remove("content-length");
        String protocol = values != null && values.size() != 0 ? values.get(0) : "https";
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

        if (!urlString.startsWith("http")) {
            urlString = protocol + "://" + urlString;
        }

        StringBuilder url = new StringBuilder(urlString);
        // Body
        try {
            String payload = httpRequestParams.getPayload();
            if (payload == null) payload = "";
            boolean curlyBracesCond = payload.startsWith("{") && payload.endsWith("}");
            boolean squareBracesCond = payload.startsWith("[") && payload.endsWith("]");
            boolean htmlPayloadCond = payload.startsWith("<") && payload.endsWith(">");
            if(htmlPayloadCond) {
                String escapedPayload = payload.replace("'", "'\\''");
                builder.append("-d '").append(escapedPayload).append("' \\\n  ");
            } else if (curlyBracesCond || squareBracesCond) {
                if (!Objects.equals(httpRequestParams.getMethod(), "GET")) {
                    String escapedPayload = payload.replace("'", "'\\''");
                    builder.append("-d '").append(escapedPayload).append("' \\\n  ");
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
            } else {
                String escapedPayload = payload.replace("'", "'\\''");
                builder.append("-d '").append(escapedPayload).append("' \\\n  ");
            }
        } catch (Exception e) {
            throw e;
        }


        // URL
        builder.append("\"").append(url).append("\"");

        return builder.toString();
    }

    int accountId;

    public String insertLlmData() {
        Context.accountId.set(accountId);
        RuntimeListener.addLlmSampleData(accountId);
        InitializerListener.saveLLmTemplates();
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

    public void setLastUrlFetched(String lastUrlFetched) {
        this.lastUrlFetched = lastUrlFetched;
    }

    public void setLastMethodFetched(String lastMethodFetched) {
        this.lastMethodFetched = lastMethodFetched;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public String getLastUrlFetched() {
        return lastUrlFetched;
    }

    public String getLastMethodFetched() {
        return lastMethodFetched;
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }
}

