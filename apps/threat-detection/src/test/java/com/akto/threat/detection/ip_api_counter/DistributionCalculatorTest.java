package com.akto.threat.detection.ip_api_counter;

import io.lettuce.core.StreamMessage;
import io.lettuce.core.XReadArgs;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for DistributionCalculator.
 * Tests the async message push to Redis stream.
 */
class DistributionCalculatorTest extends DistributionIntegrationTestBase {

    @Test
    void testProcessRequestPushesMessageToRedisStream() throws Exception {
        // Given: Empty stream
        assertThat(getStreamLength()).isEqualTo(0);

        // When: processRequest called
        String apiKey = "123|/api/users|GET";
        String ip = "192.168.1.1";
        String ipCmsKey = "ipApiCmsData|123|" + ip + "|/api/users|GET";

        distributionCalculator.processRequest(
            apiKey,
            120,
            ipCmsKey,
            30,  // rateLimitWindow
            -1,  // threshold
            300, // mitigationPeriod
            ip,
            "test-host",
            "test-acct",
            1234567890,
            "US",
            "US"
        );

        // Then: Message appears in stream (async, so wait a bit)
        Thread.sleep(500);
        assertThat(getStreamLength()).isEqualTo(1);
    }

    @Test
    void testMessageContainsCorrectFields() throws Exception {
        // Given: Process a request with specific values
        String apiKey = "123|/api/users|GET";
        String ip = "192.168.1.1";
        String ipCmsKey = "ipApiCmsData|123|" + ip + "|/api/users|GET";
        String host = "example.com";
        String accountId = "acct-123";
        int timestamp = 1234567890;

        // When: Push message
        distributionCalculator.processRequest(
            apiKey,
            120,
            ipCmsKey,
            30,
            -1,
            300,
            ip,
            host,
            accountId,
            timestamp,
            "US",
            "DE"
        );

        Thread.sleep(500);

        // Then: Read message from stream and verify fields
        @SuppressWarnings("unchecked")
        List<StreamMessage<String, String>> messages = syncRedis.xread(
            XReadArgs.StreamOffset.from("threat_input_stream", "0-0")
        );

        assertThat(messages).isNotEmpty();
        Map<String, String> body = messages.get(0).getBody();

        assertThat(body)
            .containsEntry("ipApiCmsKey", ipCmsKey)
            .containsEntry("apiKey", apiKey)
            .containsEntry("minute", "120")
            .containsEntry("rateLimitWindow", "30")
            .containsEntry("threshold", "-1")
            .containsEntry("mitigationPeriod", "300")
            .containsEntry("actor", ip)
            .containsEntry("host", host)
            .containsEntry("accountId", accountId)
            .containsEntry("timestamp", String.valueOf(timestamp))
            .containsEntry("countryCode", "US")
            .containsEntry("destCountryCode", "DE");
    }

    @Test
    void testBatchOfMessagesProcessedTogether() throws Exception {
        // When: Push 50 different IP/API pairs
        int batchSize = 50;
        for (int i = 0; i < batchSize; i++) {
            String ipSuffix = String.format("%d", i % 256);
            String ip = "192.168.1." + ipSuffix;
            String apiKey = "123|/api/users|GET";
            String ipCmsKey = "ipApiCmsData|123|" + ip + "|/api/users|GET";

            distributionCalculator.processRequest(
                apiKey, 120, ipCmsKey, 30, -1, 300,
                ip, "host", "acct", (int) (System.currentTimeMillis() / 1000),
                "US", "US"
            );
        }

        Thread.sleep(1000);

        // Then: Stream should have 50 messages
        assertThat(getStreamLength()).isEqualTo(batchSize);
    }

    @Test
    void testMultipleCallsFromSameIP() throws Exception {
        // Given: Same IP calling same API multiple times
        String apiKey = "123|/api/users|GET";
        String ip = "192.168.1.1";
        String ipCmsKey = "ipApiCmsData|123|" + ip + "|/api/users|GET";

        // When: Push 10 messages from same IP
        for (int i = 0; i < 10; i++) {
            distributionCalculator.processRequest(
                apiKey, 120 + (i / 5), ipCmsKey, 30, -1, 300,
                ip, "host", "acct", (int) (System.currentTimeMillis() / 1000),
                "US", "US"
            );
        }

        Thread.sleep(500);

        // Then: All 10 messages in stream
        assertThat(getStreamLength()).isEqualTo(10);
    }

    @Test
    void testNullFieldsHandledGracefully() throws Exception {
        // Given: Process request with null fields
        String apiKey = "123|/api/users|GET";
        String ip = "192.168.1.1";
        String ipCmsKey = "ipApiCmsData|123|" + ip + "|/api/users|GET";

        // When: Call with null host and accountId
        distributionCalculator.processRequest(
            apiKey,
            120,
            ipCmsKey,
            30,
            -1,
            300,
            ip,
            null,  // host
            null,  // accountId
            1234567890,
            "US",
            "US"
        );

        Thread.sleep(500);

        // Then: Message still appears with empty strings for null fields
        @SuppressWarnings("unchecked")
        List<StreamMessage<String, String>> messages = syncRedis.xread(
            XReadArgs.StreamOffset.from("threat_input_stream", "0-0")
        );

        assertThat(messages).isNotEmpty();
        Map<String, String> body = messages.get(0).getBody();

        assertThat(body.get("host")).isEmpty();
        assertThat(body.get("accountId")).isEmpty();
    }

    @Test
    void testAsyncOperationNonBlocking() throws Exception {
        // Given: Empty stream and current time
        long startTime = System.currentTimeMillis();

        // When: Call processRequest (should be non-blocking)
        distributionCalculator.processRequest(
            "123|/api/users|GET",
            120,
            "ipApiCmsData|123|192.168.1.1|/api/users|GET",
            30, -1, 300,
            "192.168.1.1", "host", "acct", 1234567890,
            "US", "US"
        );

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then: Operation should complete quickly (< 50ms for async fire-and-forget)
        assertThat(duration).isLessThan(50);
    }

    @Test
    void testMessageStreamFormatting() throws Exception {
        // Given: Process request with specific data
        String apiKey = "123|/api/products|POST";
        String ip = "10.0.0.5";

        // When: Push message
        distributionCalculator.processRequest(
            apiKey,
            250,
            "ipApiCmsData|123|10.0.0.5|/api/products|POST",
            15,
            100,
            600,
            ip,
            "api.example.com",
            "acct-999",
            1111111111,
            "GB",
            "FR"
        );

        Thread.sleep(500);

        // Then: Verify message structure and field types
        @SuppressWarnings("unchecked")
        List<StreamMessage<String, String>> messages = syncRedis.xread(
            XReadArgs.StreamOffset.from("threat_input_stream", "0-0")
        );

        assertThat(messages).hasSize(1);
        Map<String, String> body = messages.get(0).getBody();

        // Verify all expected fields exist
        assertThat(body).hasSize(12);  // ipApiCmsKey, apiKey, minute, rateLimitWindow, threshold, mitigationPeriod, actor, host, accountId, timestamp, countryCode, destCountryCode

        // Verify numeric fields are strings but parseable
        assertThat(Integer.parseInt(body.get("minute"))).isEqualTo(250);
        assertThat(Integer.parseInt(body.get("rateLimitWindow"))).isEqualTo(15);
        assertThat(Long.parseLong(body.get("threshold"))).isEqualTo(100);
    }
}
