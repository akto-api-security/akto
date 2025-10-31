package com.akto.dto;

import org.junit.Test;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class TestHeadersMapPerformance {

    @Test
    public void testBuildHeadersMapBasic() {
        String headersString = "{\"Content-Type\":\"application/json\",\"Host\":\"example.com\"}";
        Map<String, List<String>> headers = OriginalHttpRequest.buildHeadersMap(headersString);

        assertNotNull(headers);
        assertEquals(2, headers.size());
        assertEquals("application/json", headers.get("content-type").get(0));
        assertEquals("example.com", headers.get("host").get(0));
    }

    @Test
    public void testBuildHeadersMapWithEscapedQuotes() {
        String headersString = "{\"accept\":\"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7\",\"accept-encoding\":\"gzip, deflate, br, zstd\",\"accept-language\":\"en-GB,en;q=0.9\",\"cache-control\":\"max-age=0\",\"connection\":\"keep-alive\",\"host\":\"ayush.akto.example\",\"sec-ch-ua\":\"\\\"Not)A;Brand\\\";v=\\\"99\\\", \\\"Google Chrome\\\";v=\\\"127\\\", \\\"Chromium\\\";v=\\\"127\\\"\",\"sec-ch-ua-mobile\":\"?0\",\"sec-ch-ua-platform\":\"\\\"macOS\\\"\",\"sec-fetch-dest\":\"document\",\"sec-fetch-mode\":\"navigate\",\"sec-fetch-site\":\"none\",\"sec-fetch-user\":\"?1\",\"upgrade-insecure-requests\":\"1\",\"user-agent\":\"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36\"}";

        Map<String, List<String>> headers = OriginalHttpRequest.buildHeadersMap(headersString);

        assertNotNull(headers);
        assertTrue(headers.size() > 10);
        assertEquals("ayush.akto.example", headers.get("host").get(0));
        assertEquals("\"Not)A;Brand\";v=\"99\", \"Google Chrome\";v=\"127\", \"Chromium\";v=\"127\"", headers.get("sec-ch-ua").get(0));
        assertEquals("\"macOS\"", headers.get("sec-ch-ua-platform").get(0));
    }

    @Test
    public void testBuildHeadersMapEmpty() {
        Map<String, List<String>> headers1 = OriginalHttpRequest.buildHeadersMap("");
        Map<String, List<String>> headers2 = OriginalHttpRequest.buildHeadersMap(null);
        Map<String, List<String>> headers3 = OriginalHttpRequest.buildHeadersMap("{}");

        assertNotNull(headers1);
        assertTrue(headers1.isEmpty());
        assertNotNull(headers2);
        assertTrue(headers2.isEmpty());
        assertNotNull(headers3);
        assertTrue(headers3.isEmpty());
    }

    @Test
    public void testBuildHeadersMapCaseInsensitive() {
        String headersString = "{\"Content-Type\":\"application/json\",\"HOST\":\"example.com\",\"Accept\":\"*/*\"}";
        Map<String, List<String>> headers = OriginalHttpRequest.buildHeadersMap(headersString);

        assertNotNull(headers);
        assertEquals("application/json", headers.get("content-type").get(0));
        assertEquals("example.com", headers.get("host").get(0));
        assertEquals("*/*", headers.get("accept").get(0));
    }

    @Test
    public void testBuildHeadersMapWithUnicodeAndSpecialChars() {
        String headersString = "{\"X-Custom-Header\":\"Hello 世界 🌍\",\"Content-Type\":\"text/plain; charset=utf-8\"}";
        Map<String, List<String>> headers = OriginalHttpRequest.buildHeadersMap(headersString);

        assertNotNull(headers);
        assertEquals("Hello 世界 🌍", headers.get("x-custom-header").get(0));
        assertEquals("text/plain; charset=utf-8", headers.get("content-type").get(0));
    }

    @Test
    public void testBuildHeadersMapWithBackslashes() {
        String headersString = "{\"X-Path\":\"C:\\\\Users\\\\test\\\\file.txt\",\"X-Regex\":\"\\\\d+\"}";
        Map<String, List<String>> headers = OriginalHttpRequest.buildHeadersMap(headersString);

        assertNotNull(headers);
        assertEquals("C:\\Users\\test\\file.txt", headers.get("x-path").get(0));
        assertEquals("\\d+", headers.get("x-regex").get(0));
    }

    @Test
    public void testBuildHeadersMapWithNewlinesAndTabs() {
        String headersString = "{\"X-Multiline\":\"Line1\\nLine2\\nLine3\",\"X-Tab\":\"Col1\\tCol2\"}";
        Map<String, List<String>> headers = OriginalHttpRequest.buildHeadersMap(headersString);

        assertNotNull(headers);
        assertEquals("Line1\nLine2\nLine3", headers.get("x-multiline").get(0));
        assertEquals("Col1\tCol2", headers.get("x-tab").get(0));
    }

    @Test
    public void testBuildHeadersMapWithEmptyValues() {
        String headersString = "{\"Empty\":\"\",\"Host\":\"example.com\"}";
        Map<String, List<String>> headers = OriginalHttpRequest.buildHeadersMap(headersString);

        assertNotNull(headers);
        assertEquals("", headers.get("empty").get(0));
        assertEquals("example.com", headers.get("host").get(0));
    }

    @Test
    public void testBuildHeadersMapWithNumericValues() {
        String headersString = "{\"Content-Length\":12345,\"X-Rate-Limit\":100,\"X-Float\":3.14}";
        Map<String, List<String>> headers = OriginalHttpRequest.buildHeadersMap(headersString);

        assertNotNull(headers);
        assertEquals("12345", headers.get("content-length").get(0));
        assertEquals("100", headers.get("x-rate-limit").get(0));
        assertEquals("3.14", headers.get("x-float").get(0));
    }

    @Test
    public void testBuildHeadersMapWithBooleanAndNull() {
        String headersString = "{\"X-Enabled\":true,\"X-Disabled\":false,\"X-Null\":null}";
        Map<String, List<String>> headers = OriginalHttpRequest.buildHeadersMap(headersString);

        assertNotNull(headers);
        assertEquals("true", headers.get("x-enabled").get(0));
        assertEquals("false", headers.get("x-disabled").get(0));
        assertEquals("null", headers.get("x-null").get(0));
    }

    @Test
    public void testBuildHeadersMapWithJsonInValue() {
        // Edge case: nested JSON as a string value
        String headersString = "{\"X-Json-Data\":\"{\\\"key\\\":\\\"value\\\"}\"}";
        Map<String, List<String>> headers = OriginalHttpRequest.buildHeadersMap(headersString);

        assertNotNull(headers);
        assertEquals("{\"key\":\"value\"}", headers.get("x-json-data").get(0));
    }

    @Test
    public void testBuildHeadersMapWithWhitespace() {
        String headersString = "{ \"Host\" : \"example.com\" , \"Content-Type\" : \"application/json\" }";
        Map<String, List<String>> headers = OriginalHttpRequest.buildHeadersMap(headersString);

        assertNotNull(headers);
        assertEquals("example.com", headers.get("host").get(0));
        assertEquals("application/json", headers.get("content-type").get(0));
    }

    @Test
    public void testBuildHeadersMapWithInvalidJson() {
        String headersString = "{\"Host\":\"example.com\" INVALID";
        Map<String, List<String>> headers = OriginalHttpRequest.buildHeadersMap(headersString);

        // Should return empty map on parse failure
        assertNotNull(headers);
        assertTrue(headers.isEmpty());
    }

    @Test
    public void testBuildHeadersMapDuplicateKeys() {
        // NOTE: In standard JSON, duplicate keys are not allowed, but if they occur,
        // the last value typically wins. However, our implementation should handle this gracefully.
        // Jackson will only see the last occurrence in the JSON string.
        String headersString = "{\"Cookie\":\"session=abc\",\"Cookie\":\"user=123\"}";
        Map<String, List<String>> headers = OriginalHttpRequest.buildHeadersMap(headersString);

        assertNotNull(headers);
        // Jackson will parse and we'll see only one entry (the last one typically)
        assertTrue(headers.containsKey("cookie"));
    }

    @Test
    public void testBuildHeadersMapWithForwardSlashesAndUrls() {
        String headersString = "{\"Referer\":\"https://example.com/path/to/page?query=value\",\"X-Forwarded-For\":\"192.168.1.1, 10.0.0.1\"}";
        Map<String, List<String>> headers = OriginalHttpRequest.buildHeadersMap(headersString);

        assertNotNull(headers);
        assertEquals("https://example.com/path/to/page?query=value", headers.get("referer").get(0));
        assertEquals("192.168.1.1, 10.0.0.1", headers.get("x-forwarded-for").get(0));
    }
}
