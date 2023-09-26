package com.akto.action.growth_tools;

import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import com.google.gson.Gson;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PublicApiAction extends ActionSupport implements Action, ServletResponseAware, ServletRequestAware {
    protected HttpServletRequest request;
    protected HttpServletResponse response;
    private List<SampleData> sampleDataList;

    private static Gson gson = new Gson();
    private String sampleRequestString;
    private String sampleResponseString;

    public static final String PATH = "path";
    public static final String TYPE = "type";
    public static final String METHOD = "method";
    public static final String REQUEST_PAYLOAD = "requestPayload";
    public static final String REQUEST_HEADERS = "requestHeaders";
    public static final String RESPONSE_PAYLOAD = "responsePayload";
    public static final String RESPONSE_HEADERS = "responseHeaders";
    public static final String AKTO_VXLAN_ID = "akto_vxlan_id";
    public static final String STATUS = "status";
    public static final String STATUS_CODE = "statusCode";

    @Override
    public String execute() throws Exception {
        return SUCCESS.toUpperCase();
    }

    /*
     *   Request and Response Sample Data
     *
     * Request and response folling burp's format
     *
     * First line METHOD URL PROTOCOL
     * Second line host
     * Third line headers
     * Fourth line empty
     * Fifth line body
     *
     * Response format is
     * First line PROTOCOL STATUS_CODE STATUS_MESSAGE
     * Second line headers
     * Third line empty
     * Fourth line body
     *
     * */

    public String createSampleDataJson() {
        try {
            if (sampleResponseString == null || sampleRequestString == null) {
                addActionError("request and response cannot be null");
                return ERROR.toUpperCase();
            }
            String[] requestLines = sampleRequestString.split("\n");
            Map<String, Object> map = new HashMap<>();
            int requestIndex = 0;
            String[] requestURL = requestLines[requestIndex].split(" ");
            map.put(METHOD, requestURL[0].trim());
            map.put(PATH, requestURL[1].trim());
            map.put(TYPE, requestURL[2].trim());


            Map<String, String> requestHeaders = new HashMap<>();
            for (requestIndex = requestIndex+1; requestIndex < requestLines.length; requestIndex++) {
                String[] requestHeader = requestLines[requestIndex].split(":",2);
                if (requestHeader.length == 2) {
                    requestHeaders.put(requestHeader[0].trim(), requestHeader[1].trim());
                } else {
                    break;
                }
            }
            map.put(REQUEST_HEADERS, gson.toJson(requestHeaders));
            if (requestIndex + 1 < requestLines.length) {
                StringBuilder requestPayload = new StringBuilder();
                for (int i = requestIndex + 1; i < requestLines.length; i++) {
                    requestPayload.append(requestLines[i].trim()).append("\n");
                }
                map.put(REQUEST_PAYLOAD, requestPayload.toString());
            } else {
                map.put(REQUEST_PAYLOAD, "");
            }
            map.put(AKTO_VXLAN_ID, 0);

            String[] responseLines = sampleResponseString.split("\n");
            int responseIndex = 0;
            String[] responseStatus = responseLines[responseIndex].split(" ",3);

            map.put(STATUS_CODE, responseStatus[1].trim());
            map.put(STATUS, responseStatus[2].trim());

            Map<String, String> responseHeaders = new HashMap<>();
            for (responseIndex = responseIndex+1; responseIndex < responseLines.length; responseIndex++) {
                String[] responseHeader = responseLines[responseIndex].split(":",2);
                if (responseHeader.length == 2) {
                    responseHeaders.put(responseHeader[0].trim(), responseHeader[1].trim());
                } else {
                    break;
                }
            }
            map.put(RESPONSE_HEADERS, gson.toJson(responseHeaders));
            if (responseIndex + 1 < responseLines.length) {
                StringBuilder builder = new StringBuilder();
                for (responseIndex = responseIndex+1; responseIndex < responseLines.length; responseIndex++) {
                    builder.append(responseLines[responseIndex].trim()).append("\n");
                }
                map.put(RESPONSE_PAYLOAD, builder.toString());
            } else {
                map.put(RESPONSE_PAYLOAD, "");
            }
            map.put("source", HttpResponseParams.Source.OTHER);
            map.put("time", Context.now());
            map.put("ip", "null");
            map.put("akto_account_id", "1000000");

            SampleData sampleData = new SampleData();
            Key key = new Key(0, (String) map.get(PATH), URLMethods.Method.fromString((String) map.get(METHOD)),
                    Integer.parseInt((String) map.get(STATUS_CODE)),0,0);

            sampleData.setId(key);
            sampleData.setSamples(Collections.singletonList(gson.toJson(map)));
            sampleDataList = Collections.singletonList(sampleData);
        } catch (Exception e) {
            addActionError("Please check your request and response format");
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    @Override
    public void setServletRequest(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public void setServletResponse(HttpServletResponse response) {
        this.response = response;
    }

    public List<SampleData> getSampleDataList() {
        return sampleDataList;
    }

    public void setSampleDataList(List<SampleData> sampleDataList) {
        this.sampleDataList = sampleDataList;
    }


    public void setSampleRequestString(String sampleRequestString) {
        this.sampleRequestString = sampleRequestString;
    }

    public void setSampleResponseString(String sampleResponseString) {
        this.sampleResponseString = sampleResponseString;
    }

    public String getSampleRequestString() {
        return sampleRequestString;
    }

    public String getSampleResponseString() {
        return sampleResponseString;
    }
}
