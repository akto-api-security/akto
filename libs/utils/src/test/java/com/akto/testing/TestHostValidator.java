package com.akto.testing;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TestHostValidator {

    @Test
    public void testGetUniformUrl() {

        String url = "https://example.com";
        String finalUrl = HostValidator.getUniformUrl(url);
        assertEquals("https://example.com:443", finalUrl);

        url = "https://example.com:8443";
        finalUrl = HostValidator.getUniformUrl(url);
        assertEquals("https://example.com:8443", finalUrl);
        
        url = "example.com";
        finalUrl = HostValidator.getUniformUrl(url);
        assertEquals("example.com", finalUrl);

        url = "http://example.com";
        finalUrl = HostValidator.getUniformUrl(url);
        assertEquals("http://example.com:80", finalUrl);

        url = "http://example.com:8080";
        finalUrl = HostValidator.getUniformUrl(url);
        assertEquals("http://example.com:8080", finalUrl);

    }

}
