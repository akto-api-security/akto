package com.akto.action;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.parsers.HttpCallParser;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
        ExportSampleDataAction exportSampleDataAction = new ExportSampleDataAction();
        exportSampleDataAction.setSampleData("{\"method\":\"POST\",\"requestPayload\":\"{\\\"p\\\":\\\"2\\\",\\\"q\\\":\\\"1\\\"}\",\"responsePayload\":\"{\\\"id\\\":\\\"1\\\",\\\"isbn\\\":\\\"3223\\\",\\\"title\\\":\\\"Book 1\\\",\\\"author\\\":{\\\"firstname\\\":\\\"Avneesh\\\",\\\"lastname\\\":\\\"Hota\\\"},\\\"timestamp\\\":1643660758}\\n\",\"ip\":\"null\",\"source\":\"HAR\",\"type\":\"HTTP/1.1\",\"akto_vxlan_id\":\"1643660938\",\"path\":\"http://localhost:8000/api/cars\",\"requestHeaders\":\"{\\\"Origin\\\":\\\"http://localhost:8000\\\",\\\"accept-language\\\":\\\"en-GB,en-US;q=0.9,en;q=0.8\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/97.0.4692.71 Safari/537.36\\\",\\\"Referer\\\":\\\"http://localhost:8000/api/books/1\\\",\\\"Connection\\\":\\\"close\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Host\\\":\\\"localhost:8000\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate\\\",\\\"accept\\\":\\\"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,/;q=0.8,application/signed-exchange;v=b3;q=0.9\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"sec-ch-ua\\\":\\\"\\\\\\\"Chromium\\\\\\\";v=\\\\\\\"97\\\\\\\", \\\\\\\" Not;A Brand\\\\\\\";v=\\\\\\\"99\\\\\\\"\\\",\\\"sec-ch-ua-mobile\\\":\\\"?0\\\",\\\"sec-ch-ua-platform\\\":\\\"\\\\\\\"macOS\\\\\\\"\\\",\\\"upgrade-insecure-requests\\\":\\\"1\\\",\\\"Content-Length\\\":\\\"2\\\",\\\"cache-control\\\":\\\"max-age=0\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"Connection\\\":\\\"close\\\",\\\"Content-Length\\\":\\\"116\\\",\\\"Date\\\":\\\"Mon, 31 Jan 2022 20:44:02 GMT\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"time\":\"0\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"201\",\"status\":\"Created\"}");
        exportSampleDataAction.generateCurl();
        System.out.println(exportSampleDataAction.getCurlString());
    }

    private String curlString;
    private String sampleData;
    // TODO: remove exception
    public String generateCurl() throws IOException {
        HttpResponseParams httpResponseParams;
        try {
            httpResponseParams = HttpCallParser.parseKafkaMessage(sampleData);
        } catch (Exception e) {
            e.printStackTrace();
            return ERROR.toUpperCase();
        }
        HttpRequestParams httpRequestParams = httpResponseParams.getRequestParams();

        StringBuilder builder = new StringBuilder("curl -v ");

        // Method
        builder.append("-X ").append(httpRequestParams.getMethod()).append(" \\\n  ");

        // Headers
        for (Map.Entry<String, List<String>> entry : httpRequestParams.getHeaders().entrySet()) {
            builder.append("-H '").append(entry.getKey()).append(":");
            for (String value : entry.getValue()) {
                builder.append(" ").append(value.replaceAll("\"", "\\\\\""));
            }
            builder.append("' \\\n  ");
        }

        StringBuilder url = new StringBuilder(httpRequestParams.getURL());

        // Body
        String payload = httpRequestParams.getPayload();
        if (!Objects.equals(httpRequestParams.getMethod(), "GET")) {
            if (payload != null)
                builder.append("-d '").append(payload).append("' \\\n  ");
        } else {
            JsonParser jp = factory.createParser(payload);
            JsonNode node = mapper.readTree(jp);
            Iterator<String> fieldNames = node.fieldNames();
            boolean flag =true;
            while(fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode fieldValue = node.get(fieldName);
                if (fieldValue.isValueNode()) {
                    if (flag) {
                        url.append("?").append(fieldName).append("=").append(fieldValue.textValue());
                        flag = false;
                    } else {
                        url.append("&").append(fieldName).append("=").append(fieldValue.textValue());
                    }
                }
            }
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
}

