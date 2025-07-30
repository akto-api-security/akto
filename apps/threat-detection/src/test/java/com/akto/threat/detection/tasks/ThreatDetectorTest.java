package com.akto.threat.detection.tasks;

import com.akto.dto.HttpResponseParams;
import com.akto.dto.HttpRequestParams;
import com.akto.threat.detection.utils.ThreatDetector;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;


public class ThreatDetectorTest {

    private ThreatDetector threatDetector;

    @Before
    public void setUp() throws Exception {
        threatDetector = new ThreatDetector();
    }

    @Test
    public void testIsSqliThreat_url() {
        Map<String, List<String>> headers = new HashMap<>();
        String payload = "";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/login?user=admin' OR '1'='1", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertTrue(threatDetector.isSqliThreat(params));
    }

    @Test
    public void testIsSqliThreat_headers() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("X-Forwarded-For", Arrays.asList("127.0.0.1' OR '1'='1"));
        String payload = "";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/home", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertTrue(threatDetector.isSqliThreat(params));
    }

    @Test
    public void testIsSqliThreat_payload() {
        Map<String, List<String>> headers = new HashMap<>();
        String payload = "username=admin&password=' OR '1'='1";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/home", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertTrue(threatDetector.isSqliThreat(params));
    }

    @Test
    public void testIsSqliThreat_none() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("X-Forwarded-For", Arrays.asList("127.0.0.1"));
        String payload = "username=admin&password=1234";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/home", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertFalse(threatDetector.isSqliThreat(params));
    }

    @Test
    public void testIsLfiThreat_url() {
        Map<String, List<String>> headers = new HashMap<>();
        String payload = "";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/index.php?page=../../etc/passwd", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertTrue(threatDetector.isLFiThreat(params));
    }

    @Test
    public void testIsLfiThreat_headers() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Referer", Arrays.asList("../../etc/passwd"));
        String payload = "";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/home", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertTrue(threatDetector.isLFiThreat(params));
    }

    @Test
    public void testIsLfiThreat_payload() {
        Map<String, List<String>> headers = new HashMap<>();
        String payload = "file=../../etc/passwd";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/home", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertTrue(threatDetector.isLFiThreat(params));
    }

    @Test
    public void testIsLfiThreat_none() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Referer", Arrays.asList("/home"));
        String payload = "file=about.html";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/home", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertFalse(threatDetector.isLFiThreat(params));
    }

    @Test
    public void testIsOsCommandInjectionThreat_url() {
        Map<String, List<String>> headers = new HashMap<>();
        String payload = "";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/run?cmd=cat /etc/passwd", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertTrue(threatDetector.isOsCommandInjectionThreat(params));
    }

    @Test
    public void testIsOsCommandInjectionThreat_headers() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("X-Command", Arrays.asList("cat /etc/passwd"));
        String payload = "";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/home", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertTrue(threatDetector.isOsCommandInjectionThreat(params));
    }

    @Test
    public void testIsOsCommandInjectionThreat_payload() {
        Map<String, List<String>> headers = new HashMap<>();
        String payload = "cmd=cat /etc/passwd";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/home", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertTrue(threatDetector.isOsCommandInjectionThreat(params));
    }

    @Test
    public void testIsOsCommandInjectionThreat_none() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("X-Command", Arrays.asList("ls"));
        String payload = "cmd=ls";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/home", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertFalse(threatDetector.isOsCommandInjectionThreat(params));
    }

    @Test
    public void testIsSSRFThreat_url() {
        Map<String, List<String>> headers = new HashMap<>();
        String payload = "";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/fetch?url=http://169.254.169.254/latest/meta-data/", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertTrue(threatDetector.isSSRFThreat(params));
    }

    @Test
    public void testIsSSRFThreat_headers() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("X-URL", Arrays.asList("http://169.254.169.254/latest/meta-data/"));
        String payload = "";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/home", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertTrue(threatDetector.isSSRFThreat(params));
    }

    @Test
    public void testIsSSRFThreat_payload() {
        Map<String, List<String>> headers = new HashMap<>();
        String payload = "url=http://169.254.169.254/latest/meta-data/";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/home", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertTrue(threatDetector.isSSRFThreat(params));
    }

    @Test
    public void testIsSSRFThreat_none() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("X-URL", Arrays.asList("http://example.com"));
        String payload = "url=http://example.com";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/home", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertFalse(threatDetector.isSSRFThreat(params));
    }
}