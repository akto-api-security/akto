package com.akto.action;

import org.junit.Test;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;

public class TestExportSampleDataAction {

    @Test
    public void curlEmptyRequestBodyGet() {
        String sampleData = "{\"akto_account_id\":\"1000000\",\"akto_vxlan_id\":\"280483\",\"ip\":\"172.31.5.42\",\"is_pending\":\"false\",\"method\":\"GET\",\"path\":\"/api/books\",\"requestHeaders\":\"{\\\"Accept-Encoding\\\":\\\"gzip, compressed\\\",\\\"Connection\\\":\\\"close\\\",\\\"User-Agent\\\":\\\"ELB-HealthChecker/2.0\\\"}\",\"requestPayload\":\"\",\"responseHeaders\":\"{\\\"Content-Length\\\":\\\"116\\\",\\\"Content-Type\\\":\\\"application/json;charset=utf-8\\\",\\\"Date\\\":\\\"Fri, 04 Mar 2022 18:42:21 GMT\\\"}\",\"responsePayload\":\"{\\\"id\\\":\\\"1\\\",\\\"isbn\\\":\\\"3223\\\",\\\"title\\\":\\\"Book 1\\\",\\\"author\\\":{\\\"firstname\\\":\\\"Avneesh\\\",\\\"lastname\\\":\\\"Hota\\\"},\\\"timestamp\\\":1646416193}\\n\",\"status\":\"201 Created\",\"statusCode\":\"201\",\"time\":\"1646419341\",\"type\":\"HTTP/1.1\"}";
        ExportSampleDataAction exportSampleDataAction = new ExportSampleDataAction();
        exportSampleDataAction.setSampleData(sampleData);
        exportSampleDataAction.generateCurl();
        assertNotNull(exportSampleDataAction.getCurlString());
    }

    @Test
    public void curlEmptyRequestBodyPost() {
        String sampleData = "{\"akto_account_id\":\"1000000\",\"akto_vxlan_id\":\"280483\",\"ip\":\"172.31.5.42\",\"is_pending\":\"true\",\"method\":\"POST\",\"path\":\"/api/cars\",\"requestHeaders\":\"{\\\"Accept\\\":\\\"*/*\\\",\\\"User-Agent\\\":\\\"curl/7.79.1\\\",\\\"X-Amzn-Trace-Id\\\":\\\"Root=1-6222559f-33297d9f42d25b854217ca32\\\",\\\"X-Forwarded-For\\\":\\\"172.31.11.244\\\",\\\"X-Forwarded-Port\\\":\\\"443\\\",\\\"X-Forwarded-Proto\\\":\\\"https\\\"}\",\"requestPayload\":\"\",\"responseHeaders\":\"{\\\"Content-Length\\\":\\\"116\\\",\\\"Content-Type\\\":\\\"application/json;charset=utf-8\\\",\\\"Date\\\":\\\"Fri, 04 Mar 2022 18:08:31 GMT\\\"}\",\"responsePayload\":\"{\\\"id\\\":\\\"1\\\",\\\"isbn\\\":\\\"3223\\\",\\\"title\\\":\\\"Book 1\\\",\\\"author\\\":{\\\"firstname\\\":\\\"Avneesh\\\",\\\"lastname\\\":\\\"Hota\\\"},\\\"timestamp\\\":1646416193}\\n\",\"status\":\"201 Created\",\"statusCode\":\"201\",\"time\":\"1646417399\",\"type\":\"HTTP/1.1\"}";
        ExportSampleDataAction exportSampleDataAction = new ExportSampleDataAction();
        exportSampleDataAction.setSampleData(sampleData);
        assertNotNull(exportSampleDataAction.getCurlString());
    }
}
