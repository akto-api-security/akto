package com.akto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import com.akto.runtime.policies.ApiAccessTypePolicy;
import org.junit.jupiter.api.*;

public class ApiAccessTypePolicyTest {
    
    @Test
    public void testIpv4MatchSingleCidr() {
        ApiAccessTypePolicy p = new ApiAccessTypePolicy(Arrays.asList("10.0.0.0/8"), null);
        assertTrue(p.ipInCidr("10.1.2.3"));
    }

    @Test
    public void testIpv4NoMatchSingleCidr() {
        ApiAccessTypePolicy p = new ApiAccessTypePolicy(Arrays.asList("10.0.0.0/8"), null);
        assertFalse(p.ipInCidr("11.0.0.1"));
    }

    @Test
    public void testMultipleCidrsOneMatches() {
        ApiAccessTypePolicy p = new ApiAccessTypePolicy(Arrays.asList("192.168.1.0/24", "172.16.0.0/12"), null);
        assertTrue(p.ipInCidr("172.31.255.255"));
        assertFalse(p.ipInCidr("8.8.8.8"));
    }

    @Test
    public void testEmptyCidrListReturnsFalse() {
        ApiAccessTypePolicy p = new ApiAccessTypePolicy(Collections.emptyList(), null);
        assertFalse(p.ipInCidr("10.0.0.1"));
    }

    @Test
    public void testBoundaryAddressesIncluded() {
        ApiAccessTypePolicy p = new ApiAccessTypePolicy(Arrays.asList("192.168.1.0/24"), null);
        assertTrue(p.ipInCidr("192.168.1.0"));
        assertTrue(p.ipInCidr("192.168.1.255"));
    }

    @Test
    public void testIpv6Match() {
        ApiAccessTypePolicy p = new ApiAccessTypePolicy(Arrays.asList("2001:db8::/32"), null);
        assertTrue(p.ipInCidr("2001:db8::1"));
    }

    @Test
    public void testIpv6NoMatch() {
        ApiAccessTypePolicy p = new ApiAccessTypePolicy(Arrays.asList("2001:db8::/32"), null);
        assertFalse(p.ipInCidr("2001:db9::1"));
    }

    @Test
    public void testUpdateCidrsReflectsInIpInCidr() {
        ApiAccessTypePolicy p = new ApiAccessTypePolicy(Arrays.asList("10.0.0.0/8"), null);
        assertTrue(p.ipInCidr("10.2.3.4"));
        p.setPrivateCidrList(Arrays.asList("192.168.0.0/16"));
        assertFalse(p.ipInCidr("10.2.3.4"));
        assertTrue(p.ipInCidr("192.168.10.10"));
    }
}
