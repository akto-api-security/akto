package com.akto.hybrid_parsers;

import com.akto.dto.tracing.Trace;
import com.akto.dto.tracing.Span;
import com.akto.tracing.bedrock.BedrockAgentTraceParser;
import com.akto.tracing.TraceParseResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

import static org.junit.Assert.*;

public class HttpCallParserTest {

    @Test
    public void testIsBlockedHostWithNull() {
        assertFalse(HttpCallParser.isBlockedHost(null));
    }

    @Test
    public void testIsBlockedHostWithEmpty() {
        assertFalse(HttpCallParser.isBlockedHost(""));
        assertFalse(HttpCallParser.isBlockedHost("   "));
    }

    @Test
    public void testIsBlockedHostWithIPv4Addresses() {
        // Valid IPv4 addresses
        assertTrue("192.168.1.1 should be blocked", HttpCallParser.isBlockedHost("192.168.1.1"));
        assertTrue("10.0.0.1 should be blocked", HttpCallParser.isBlockedHost("10.0.0.1"));
        assertTrue("172.16.0.1 should be blocked", HttpCallParser.isBlockedHost("172.16.0.1"));
        assertTrue("127.0.0.1 should be blocked", HttpCallParser.isBlockedHost("127.0.0.1"));
        assertTrue("0.0.0.0 should be blocked", HttpCallParser.isBlockedHost("0.0.0.0"));
        assertTrue("255.255.255.255 should be blocked", HttpCallParser.isBlockedHost("255.255.255.255"));
        
        // IPv4 addresses with ports and paths
        assertTrue("192.168.1.1:8080 should be blocked", HttpCallParser.isBlockedHost("192.168.1.1:8080"));
        assertTrue("10.0.0.1/api/v1 should be blocked", HttpCallParser.isBlockedHost("10.0.0.1/api/v1"));
        assertTrue("172.16.0.1:3000/path should be blocked", HttpCallParser.isBlockedHost("172.16.0.1:3000/path"));
        assertTrue("127.0.0.1:9000/health should be blocked", HttpCallParser.isBlockedHost("127.0.0.1:9000/health"));
    }

    @Test
    public void testIsBlockedHostWithSvcClusterLocal() {
        assertTrue("svc.cluster.local should be blocked", HttpCallParser.isBlockedHost("svc.cluster.local"));
        assertTrue("my-service.svc.cluster.local should be blocked", HttpCallParser.isBlockedHost("my-service.svc.cluster.local"));
        assertTrue("api.svc.cluster.local should be blocked", HttpCallParser.isBlockedHost("api.svc.cluster.local"));
        assertTrue("database.svc.cluster.local should be blocked", HttpCallParser.isBlockedHost("database.svc.cluster.local"));
        assertTrue("redis.svc.cluster.local should be blocked", HttpCallParser.isBlockedHost("redis.svc.cluster.local"));
        assertTrue("svc.cluster.local:8080 should be blocked", HttpCallParser.isBlockedHost("svc.cluster.local:8080"));
        assertTrue("my-service.svc.cluster.local/api should be blocked", HttpCallParser.isBlockedHost("my-service.svc.cluster.local/api"));
        assertTrue("api.svc.cluster.local:3000/health should be blocked", HttpCallParser.isBlockedHost("api.svc.cluster.local:3000/health"));
    }

    @Test
    public void testIsBlockedHostWithLocalhost() {
        assertTrue("localhost should be blocked", HttpCallParser.isBlockedHost("localhost"));
        assertTrue("localhost:8080 should be blocked", HttpCallParser.isBlockedHost("localhost:8080"));
        assertTrue("localhost/api/v1 should be blocked", HttpCallParser.isBlockedHost("localhost/api/v1"));
        assertTrue("localhost:3000/health should be blocked", HttpCallParser.isBlockedHost("localhost:3000/health"));
        assertTrue("LOCALHOST should be blocked", HttpCallParser.isBlockedHost("LOCALHOST"));
        assertTrue("LocalHost should be blocked", HttpCallParser.isBlockedHost("LocalHost"));
        assertTrue("localhost.localdomain should be blocked", HttpCallParser.isBlockedHost("localhost.localdomain"));
        assertTrue("localhost.local should be blocked", HttpCallParser.isBlockedHost("localhost.local"));
    }

    @Test
    public void testIsBlockedHostWithKubernetesDefaultSvc() {
        assertTrue("kubernetes.default.svc should be blocked", HttpCallParser.isBlockedHost("kubernetes.default.svc"));
        assertTrue("kubernetes.default.svc:443 should be blocked", HttpCallParser.isBlockedHost("kubernetes.default.svc:443"));
        assertTrue("kubernetes.default.svc/api should be blocked", HttpCallParser.isBlockedHost("kubernetes.default.svc/api"));
    }

    @Test
    public void testIsBlockedHostWithValidDomains() {
        assertFalse("example.com should not be blocked", HttpCallParser.isBlockedHost("example.com"));
        assertFalse("api.example.com should not be blocked", HttpCallParser.isBlockedHost("api.example.com"));
        assertFalse("www.google.com should not be blocked", HttpCallParser.isBlockedHost("www.google.com"));
        assertFalse("github.com should not be blocked", HttpCallParser.isBlockedHost("github.com"));
        assertFalse("stackoverflow.com should not be blocked", HttpCallParser.isBlockedHost("stackoverflow.com"));
        assertFalse("example.com:443 should not be blocked", HttpCallParser.isBlockedHost("example.com:443"));
        assertFalse("api.example.com:8080 should not be blocked", HttpCallParser.isBlockedHost("api.example.com:8080"));
        assertFalse("www.google.com/search should not be blocked", HttpCallParser.isBlockedHost("www.google.com/search"));
        assertFalse("github.com/api/v3 should not be blocked", HttpCallParser.isBlockedHost("github.com/api/v3"));
        assertFalse("stackoverflow.com/questions should not be blocked", HttpCallParser.isBlockedHost("stackoverflow.com/questions"));
    }

    @Test
    public void testIsBlockedHostWithNonMatchingClusterDomains() {
        assertFalse("Missing svc should not be blocked", HttpCallParser.isBlockedHost("my-service.cluster.local"));
        assertFalse("Wrong TLD should not be blocked", HttpCallParser.isBlockedHost("svc.cluster.com"));
        assertFalse("Missing svc should not be blocked", HttpCallParser.isBlockedHost("cluster.local"));
        assertFalse("Missing cluster should not be blocked", HttpCallParser.isBlockedHost("my-service.local"));
        assertFalse("Missing cluster should not be blocked", HttpCallParser.isBlockedHost("svc.local"));
        assertFalse("Wrong order should not be blocked", HttpCallParser.isBlockedHost("cluster.svc.local"));
    }

    @Test
    public void testIsBlockedHostWithNonMatchingKubernetesDomains() {
        assertFalse("Wrong TLD should not be blocked", HttpCallParser.isBlockedHost("kubernetes.default.com"));
        assertFalse("Wrong order should not be blocked", HttpCallParser.isBlockedHost("kubernetes.svc.default"));
        assertFalse("Wrong order should not be blocked", HttpCallParser.isBlockedHost("default.kubernetes.svc"));
        assertFalse("Missing default should not be blocked", HttpCallParser.isBlockedHost("kubernetes.svc"));
        assertFalse("Missing kubernetes should not be blocked", HttpCallParser.isBlockedHost("default.svc"));
        assertFalse("Extra TLD should not be blocked", HttpCallParser.isBlockedHost("kubernetes.svc.default.com"));
    }


    @Test
    public void testIsBlockedHostCaseInsensitive() {
        // Test IPv4 addresses with mixed case
        assertTrue("192.168.1.1 should be blocked", HttpCallParser.isBlockedHost("192.168.1.1"));
        assertTrue("Uppercase IP should be blocked", HttpCallParser.isBlockedHost("192.168.1.1".toUpperCase()));
        
        // Test localhost with mixed case
        assertTrue("localhost should be blocked", HttpCallParser.isBlockedHost("localhost"));
        assertTrue("LOCALHOST should be blocked", HttpCallParser.isBlockedHost("LOCALHOST"));
        assertTrue("LocalHost should be blocked", HttpCallParser.isBlockedHost("LocalHost"));
        
        // Test svc.cluster.local with mixed case
        assertTrue("svc.cluster.local should be blocked", HttpCallParser.isBlockedHost("svc.cluster.local"));
        assertTrue("SVC.CLUSTER.LOCAL should be blocked", HttpCallParser.isBlockedHost("SVC.CLUSTER.LOCAL"));
        assertTrue("Svc.Cluster.Local should be blocked", HttpCallParser.isBlockedHost("Svc.Cluster.Local"));
        
        // Test kubernetes.default.svc with mixed case
        assertTrue("kubernetes.default.svc should be blocked", HttpCallParser.isBlockedHost("kubernetes.default.svc"));
        assertTrue("KUBERNETES.DEFAULT.SVC should be blocked", HttpCallParser.isBlockedHost("KUBERNETES.DEFAULT.SVC"));
        assertTrue("Kubernetes.Default.Svc should be blocked", HttpCallParser.isBlockedHost("Kubernetes.Default.Svc"));
    }

    @Test
    public void testIsBlockedHostEdgeCases() {
        // Test with leading/trailing whitespace
        assertTrue("localhost with whitespace should be blocked", HttpCallParser.isBlockedHost(" localhost "));
        assertTrue("svc.cluster.local with whitespace should be blocked", HttpCallParser.isBlockedHost(" svc.cluster.local "));

        // Test with tabs and newlines
        assertTrue("localhost with tabs should be blocked", HttpCallParser.isBlockedHost("\tlocalhost\t"));

        // Test with special characters in valid domains
        assertFalse("Domain with trailing dash should not be blocked", HttpCallParser.isBlockedHost("example.com-"));
        assertFalse("Domain with leading dash should not be blocked", HttpCallParser.isBlockedHost("-example.com"));
        assertFalse("Domain with trailing underscore should not be blocked", HttpCallParser.isBlockedHost("example.com_"));
    }

}
