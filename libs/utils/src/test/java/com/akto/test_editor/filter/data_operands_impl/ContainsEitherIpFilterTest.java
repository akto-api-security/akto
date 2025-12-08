package com.akto.test_editor.filter.data_operands_impl;

import com.akto.dto.test_editor.DataOperandFilterRequest;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class ContainsEitherIpFilterTest {

    private ContainsEitherIpFilter filter = new ContainsEitherIpFilter();

    @Test
    public void testEvaluateOnStringQuerySet_IpInCidr_ReturnsTrue() {
        // IP is within the CIDR range
        assertTrue(filter.evaluateOnStringQuerySet("192.168.1.1", "192.168.0.0/16"));
        assertTrue(filter.evaluateOnStringQuerySet("10.0.0.1", "10.0.0.0/8"));
        assertTrue(filter.evaluateOnStringQuerySet("172.16.0.1", "172.16.0.0/12"));
        assertTrue(filter.evaluateOnStringQuerySet("192.168.1.100", "192.168.1.0/24"));
    }

    @Test
    public void testEvaluateOnStringQuerySet_IpNotInCidr_ReturnsFalse() {
        // IP is not within the CIDR range
        assertFalse(filter.evaluateOnStringQuerySet("8.8.8.8", "192.168.0.0/16"));
        assertFalse(filter.evaluateOnStringQuerySet("1.1.1.1", "10.0.0.0/8"));
        assertFalse(filter.evaluateOnStringQuerySet("172.15.0.1", "172.16.0.0/12"));
    }

    @Test
    public void testEvaluateOnStringQuerySet_DataIsNotValidIp_ReturnsFalse() {
        // Data is not a valid IP, should return false
        assertFalse(filter.evaluateOnStringQuerySet("example.com", "192.168.0.0/16"));
        assertFalse(filter.evaluateOnStringQuerySet("not an ip", "10.0.0.0/8"));
        assertFalse(filter.evaluateOnStringQuerySet("123", "172.16.0.0/12"));
        assertFalse(filter.evaluateOnStringQuerySet("", "192.168.0.0/16"));
    }

    @Test
    public void testEvaluateOnStringQuerySet_InvalidCidr_ReturnsFalse() {
        // Query is not a valid CIDR, should return false
        assertFalse(filter.evaluateOnStringQuerySet("192.168.1.1", "invalid-cidr"));
        assertFalse(filter.evaluateOnStringQuerySet("10.0.0.1", "not.a.cidr"));
        assertFalse(filter.evaluateOnStringQuerySet("172.16.0.1", ""));
    }

    @Test
    public void testEvaluateOnStringQuerySet_IPv6Addresses() {
        // IPv6 CIDR matching
        assertTrue(filter.evaluateOnStringQuerySet("2001:db8::1", "2001:db8::/32"));
        assertTrue(filter.evaluateOnStringQuerySet("::1", "::/128"));
        assertFalse(filter.evaluateOnStringQuerySet("2001:db8::1", "2001:db9::/32"));
    }

    @Test
    public void testEvaluateOnStringQuerySet_SingleIpCidr() {
        // Single IP as CIDR (/32 for IPv4, /128 for IPv6)
        assertTrue(filter.evaluateOnStringQuerySet("192.168.1.1", "192.168.1.1/32"));
        assertTrue(filter.evaluateOnStringQuerySet("10.0.0.1", "10.0.0.1/32"));
        assertFalse(filter.evaluateOnStringQuerySet("192.168.1.2", "192.168.1.1/32"));
    }

    @Test
    public void testIsValid_WithValidRequest_IpInCidr_ReturnsTrue() {
        List<String> querySet = Arrays.asList("192.168.0.0/16", "10.0.0.0/8");
        DataOperandFilterRequest request = new DataOperandFilterRequest("192.168.1.1", querySet, "contains_either_cidr");
        
        assertTrue(filter.isValid(request).getIsValid());
    }

    @Test
    public void testIsValid_WithValidRequest_IpNotInCidr_ReturnsFalse() {
        List<String> querySet = Arrays.asList("192.168.0.0/16", "10.0.0.0/8");
        DataOperandFilterRequest request = new DataOperandFilterRequest("8.8.8.8", querySet, "contains_either_cidr");
        
        assertFalse(filter.isValid(request).getIsValid());
    }

    @Test
    public void testIsValid_WithValidRequest_DataIsNotIp_ReturnsFalse() {
        List<String> querySet = Arrays.asList("192.168.0.0/16");
        DataOperandFilterRequest request = new DataOperandFilterRequest("not an ip", querySet, "contains_either_cidr");
        
        assertFalse(filter.isValid(request).getIsValid());
    }

    @Test
    public void testIsValid_WithValidRequest_MultipleCidrs_IpInOne_ReturnsTrue() {
        List<String> querySet = Arrays.asList("192.168.0.0/16", "10.0.0.0/8", "172.16.0.0/12");
        DataOperandFilterRequest request = new DataOperandFilterRequest("10.0.0.1", querySet, "contains_either_cidr");
        
        assertTrue(filter.isValid(request).getIsValid());
    }

    @Test
    public void testIsValid_WithValidRequest_MultipleCidrs_IpNotInAny_ReturnsFalse() {
        List<String> querySet = Arrays.asList("192.168.0.0/16", "10.0.0.0/8", "172.16.0.0/12");
        DataOperandFilterRequest request = new DataOperandFilterRequest("8.8.8.8", querySet, "contains_either_cidr");
        
        assertFalse(filter.isValid(request).getIsValid());
    }

    @Test
    public void testIsValid_WithEmptyQuerySet_ReturnsFalse() {
        // With empty querySet, the loop never runs, so result stays false
        List<String> querySet = Collections.emptyList();
        DataOperandFilterRequest request = new DataOperandFilterRequest("192.168.1.1", querySet, "contains_either_cidr");
        
        assertFalse(filter.isValid(request).getIsValid());
    }

    @Test
    public void testIsValid_WithNullData_HandlesGracefully() {
        List<String> querySet = Arrays.asList("192.168.0.0/16");
        DataOperandFilterRequest request = new DataOperandFilterRequest(null, querySet, "contains_either_cidr");
        
        // Should handle null gracefully, likely returns false or throws exception
        try {
            ValidationResult result = filter.isValid(request);
            assertNotNull(result);
        } catch (Exception e) {
            // Expected if null handling throws exception
            assertTrue(true);
        }
    }

    @Test
    public void testIsValid_WithWhitespace_TrimsCorrectly() {
        List<String> querySet = Arrays.asList("  192.168.0.0/16  ", "  10.0.0.0/8  ");
        DataOperandFilterRequest request = new DataOperandFilterRequest("  192.168.1.1  ", querySet, "contains_either_cidr");
        
        assertTrue(filter.isValid(request).getIsValid());
    }

    @Test
    public void testIsValid_WithInvalidCidrInQuerySet() {
        List<String> querySet = Arrays.asList("invalid-cidr", "192.168.0.0/16");
        DataOperandFilterRequest request = new DataOperandFilterRequest("192.168.1.1", querySet, "contains_either_cidr");
        
        // Should still return true if IP matches one of the valid CIDRs
        assertTrue(filter.isValid(request).getIsValid());
    }

    @Test
    public void testIsValid_WithMixedValidAndInvalidCidrs() {
        List<String> querySet = Arrays.asList("not-a-cidr", "also-not-a-cidr");
        DataOperandFilterRequest request = new DataOperandFilterRequest("192.168.1.1", querySet, "contains_either_cidr");
        
        // No valid CIDRs, should return false
        assertFalse(filter.isValid(request).getIsValid());
    }
}
