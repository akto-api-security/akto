package com.akto.utils;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.runtime.utils.Utils;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CurlUtils {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final JsonFactory factory = mapper.getFactory();

    public static String getCurl(String sampleData) throws IOException {
        HttpResponseParams httpResponseParams;
        try {
            httpResponseParams = Utils.parseKafkaMessage(sampleData);
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

}
