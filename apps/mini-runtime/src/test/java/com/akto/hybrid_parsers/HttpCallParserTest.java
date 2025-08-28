package com.akto.hybrid_parsers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class HttpCallParserTest {

    @Test
    @DisplayName("Should return false for null hostname")
    void testIsBlockedHostWithNull() {
        assertFalse(HttpCallParser.isBlockedHost(null));
    }

    @Test
    @DisplayName("Should return false for empty hostname")
    void testIsBlockedHostWithEmpty() {
        assertFalse(HttpCallParser.isBlockedHost(""));
        assertFalse(HttpCallParser.isBlockedHost("   "));
    }

    @Test
    @DisplayName("Should return true for IPv4 addresses")
    void testIsBlockedHostWithIPv4Addresses() {
        // Valid IPv4 addresses
        assertTrue(HttpCallParser.isBlockedHost("192.168.1.1"), "192.168.1.1 should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("10.0.0.1"), "10.0.0.1 should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("172.16.0.1"), "172.16.0.1 should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("127.0.0.1"), "127.0.0.1 should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("0.0.0.0"), "0.0.0.0 should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("255.255.255.255"), "255.255.255.255 should be blocked");
        
        // IPv4 addresses with ports and paths
        assertTrue(HttpCallParser.isBlockedHost("192.168.1.1:8080"), "192.168.1.1:8080 should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("10.0.0.1/api/v1"), "10.0.0.1/api/v1 should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("172.16.0.1:3000/path"), "172.16.0.1:3000/path should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("127.0.0.1:9000/health"), "127.0.0.1:9000/health should be blocked");
    }

    @Test
    @DisplayName("Should return true for svc.cluster.local hosts")
    void testIsBlockedHostWithSvcClusterLocal() {
        assertTrue(HttpCallParser.isBlockedHost("svc.cluster.local"), "svc.cluster.local should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("my-service.svc.cluster.local"), "my-service.svc.cluster.local should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("api.svc.cluster.local"), "api.svc.cluster.local should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("database.svc.cluster.local"), "database.svc.cluster.local should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("redis.svc.cluster.local"), "redis.svc.cluster.local should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("svc.cluster.local:8080"), "svc.cluster.local:8080 should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("my-service.svc.cluster.local/api"), "my-service.svc.cluster.local/api should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("api.svc.cluster.local:3000/health"), "api.svc.cluster.local:3000/health should be blocked");
    }

    @Test
    @DisplayName("Should return true for localhost hosts")
    void testIsBlockedHostWithLocalhost() {
        assertTrue(HttpCallParser.isBlockedHost("localhost"), "localhost should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("localhost:8080"), "localhost:8080 should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("localhost/api/v1"), "localhost/api/v1 should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("localhost:3000/health"), "localhost:3000/health should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("LOCALHOST"), "LOCALHOST should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("LocalHost"), "LocalHost should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("localhost.localdomain"), "localhost.localdomain should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("localhost.local"), "localhost.local should be blocked");
    }

    @Test
    @DisplayName("Should return true for kubernetes.default.svc")
    void testIsBlockedHostWithKubernetesDefaultSvc() {
        assertTrue(HttpCallParser.isBlockedHost("kubernetes.default.svc"), "kubernetes.default.svc should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("kubernetes.default.svc:443"), "kubernetes.default.svc:443 should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("kubernetes.default.svc/api"), "kubernetes.default.svc/api should be blocked");
    }

    @Test
    @DisplayName("Should return false for valid public domain names")
    void testIsBlockedHostWithValidDomains() {
        assertFalse(HttpCallParser.isBlockedHost("example.com"), "example.com should not be blocked");
        assertFalse(HttpCallParser.isBlockedHost("api.example.com"), "api.example.com should not be blocked");
        assertFalse(HttpCallParser.isBlockedHost("www.google.com"), "www.google.com should not be blocked");
        assertFalse(HttpCallParser.isBlockedHost("github.com"), "github.com should not be blocked");
        assertFalse(HttpCallParser.isBlockedHost("stackoverflow.com"), "stackoverflow.com should not be blocked");
        assertFalse(HttpCallParser.isBlockedHost("example.com:443"), "example.com:443 should not be blocked");
        assertFalse(HttpCallParser.isBlockedHost("api.example.com:8080"), "api.example.com:8080 should not be blocked");
        assertFalse(HttpCallParser.isBlockedHost("www.google.com/search"), "www.google.com/search should not be blocked");
        assertFalse(HttpCallParser.isBlockedHost("github.com/api/v3"), "github.com/api/v3 should not be blocked");
        assertFalse(HttpCallParser.isBlockedHost("stackoverflow.com/questions"), "stackoverflow.com/questions should not be blocked");
    }

    @Test
    @DisplayName("Should return false for similar but non-matching cluster domains")
    void testIsBlockedHostWithNonMatchingClusterDomains() {
        assertFalse(HttpCallParser.isBlockedHost("my-service.cluster.local"), "Missing svc should not be blocked");
        assertFalse(HttpCallParser.isBlockedHost("svc.cluster.com"), "Wrong TLD should not be blocked");
        assertFalse(HttpCallParser.isBlockedHost("cluster.local"), "Missing svc should not be blocked");
        assertFalse(HttpCallParser.isBlockedHost("my-service.local"), "Missing cluster should not be blocked");
        assertFalse(HttpCallParser.isBlockedHost("svc.local"), "Missing cluster should not be blocked");
        assertFalse(HttpCallParser.isBlockedHost("cluster.svc.local"), "Wrong order should not be blocked");
    }

    @Test
    @DisplayName("Should return false for similar but non-matching kubernetes domains")
    void testIsBlockedHostWithNonMatchingKubernetesDomains() {
        assertFalse(HttpCallParser.isBlockedHost("kubernetes.default.com"), "Wrong TLD should not be blocked");
        assertFalse(HttpCallParser.isBlockedHost("kubernetes.svc.default"), "Wrong order should not be blocked");
        assertFalse(HttpCallParser.isBlockedHost("default.kubernetes.svc"), "Wrong order should not be blocked");
        assertFalse(HttpCallParser.isBlockedHost("kubernetes.svc"), "Missing default should not be blocked");
        assertFalse(HttpCallParser.isBlockedHost("default.svc"), "Missing kubernetes should not be blocked");
        assertFalse(HttpCallParser.isBlockedHost("kubernetes.svc.default.com"), "Extra TLD should not be blocked");
    }


    @Test
    @DisplayName("Should handle case insensitive matching")
    void testIsBlockedHostCaseInsensitive() {
        // Test IPv4 addresses with mixed case
        assertTrue(HttpCallParser.isBlockedHost("192.168.1.1"), "192.168.1.1 should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("192.168.1.1".toUpperCase()), "Uppercase IP should be blocked");
        
        // Test localhost with mixed case
        assertTrue(HttpCallParser.isBlockedHost("localhost"), "localhost should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("LOCALHOST"), "LOCALHOST should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("LocalHost"), "LocalHost should be blocked");
        
        // Test svc.cluster.local with mixed case
        assertTrue(HttpCallParser.isBlockedHost("svc.cluster.local"), "svc.cluster.local should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("SVC.CLUSTER.LOCAL"), "SVC.CLUSTER.LOCAL should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("Svc.Cluster.Local"), "Svc.Cluster.Local should be blocked");
        
        // Test kubernetes.default.svc with mixed case
        assertTrue(HttpCallParser.isBlockedHost("kubernetes.default.svc"), "kubernetes.default.svc should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("KUBERNETES.DEFAULT.SVC"), "KUBERNETES.DEFAULT.SVC should be blocked");
        assertTrue(HttpCallParser.isBlockedHost("Kubernetes.Default.Svc"), "Kubernetes.Default.Svc should be blocked");
    }

    @Test
    @DisplayName("Should handle edge cases with special characters")
    void testIsBlockedHostEdgeCases() {
        // Test with leading/trailing whitespace
        assertTrue(HttpCallParser.isBlockedHost(" localhost "), "localhost with whitespace should be blocked");
        assertTrue(HttpCallParser.isBlockedHost(" svc.cluster.local "), "svc.cluster.local with whitespace should be blocked");
        
        // Test with tabs and newlines
        assertTrue(HttpCallParser.isBlockedHost("\tlocalhost\t"), "localhost with tabs should be blocked");
        
        // Test with special characters in valid domains
        assertFalse(HttpCallParser.isBlockedHost("example.com-"), "Domain with trailing dash should not be blocked");
        assertFalse(HttpCallParser.isBlockedHost("-example.com"), "Domain with leading dash should not be blocked");
        assertFalse(HttpCallParser.isBlockedHost("example.com_"), "Domain with trailing underscore should not be blocked");
    }
}
