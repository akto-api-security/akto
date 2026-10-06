package com.akto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

public class IpAnonymizerLookupTest {

    private static Map<String, Object> record(Map<String, Object> proxy, String asnOrg) {
        Map<String, Object> rec = new HashMap<>();
        if (proxy != null) {
            rec.put("proxy", proxy);
        }
        if (asnOrg != null) {
            Map<String, Object> asn = new HashMap<>();
            asn.put("autonomous_system_organization", asnOrg);
            rec.put("asn", asn);
        }
        return rec;
    }

    private static Map<String, Object> flags(String... trueFlags) {
        Map<String, Object> proxy = new HashMap<>();
        for (String flag : trueFlags) {
            proxy.put(flag, true);
        }
        return proxy;
    }

    @Test
    public void parsesVpnTorAndHostingFlagsAndAsn() {
        IpAnonymizerLookup.Info info = IpAnonymizerLookup.fromRecord(
            record(flags("is_vpn", "is_hosting", "is_proxy", "is_anonymous"), "OVH SAS"));

        assertTrue(info.isVpn());
        assertFalse(info.isTor());
        assertTrue(info.isHosting());
        assertTrue(info.isVpnOrTor());
        assertEquals("OVH SAS", info.getAsnOrg());
    }

    @Test
    public void torAloneCountsAsVpnOrTor() {
        IpAnonymizerLookup.Info info = IpAnonymizerLookup.fromRecord(record(flags("is_tor"), null));

        assertTrue(info.isTor());
        assertTrue(info.isVpnOrTor());
        assertNull(info.getAsnOrg());
    }

    @Test
    public void hostingCdnAndGenericProxyAloneAreNotVpnOrTor() {
        IpAnonymizerLookup.Info info = IpAnonymizerLookup.fromRecord(
            record(flags("is_hosting", "is_proxy", "is_anonymous", "is_cdn"), "Google LLC"));

        assertTrue(info.isHosting());
        assertFalse(info.isVpnOrTor());
    }

    @Test
    public void recordWithoutProxyFieldIsClean() {
        IpAnonymizerLookup.Info info = IpAnonymizerLookup.fromRecord(record(null, "Comcast Cable Communications, LLC"));

        assertFalse(info.isVpn());
        assertFalse(info.isTor());
        assertFalse(info.isHosting());
        assertEquals("Comcast Cable Communications, LLC", info.getAsnOrg());
    }

    @Test
    public void lookupReturnsInfoFromSourceAndCachesResult() {
        AtomicInteger calls = new AtomicInteger();
        IpAnonymizerLookup lookup = new IpAnonymizerLookup(address -> {
            calls.incrementAndGet();
            return record(flags("is_vpn"), "Some VPN Ltd");
        });

        Optional<IpAnonymizerLookup.Info> first = lookup.lookup("1.2.3.4");
        Optional<IpAnonymizerLookup.Info> second = lookup.lookup("1.2.3.4");

        assertTrue(first.isPresent());
        assertTrue(first.get().isVpn());
        assertTrue(second.isPresent());
        assertEquals(1, calls.get());
    }

    @Test
    public void lookupAcceptsIpv6Literals() {
        IpAnonymizerLookup lookup = new IpAnonymizerLookup(address -> record(flags("is_tor"), null));

        assertTrue(lookup.lookup("2001:db8::1").get().isTor());
    }

    @Test
    public void nonIpInputNeverReachesTheSource() {
        AtomicInteger calls = new AtomicInteger();
        IpAnonymizerLookup lookup = new IpAnonymizerLookup(address -> {
            calls.incrementAndGet();
            return record(flags("is_vpn"), null);
        });

        assertFalse(lookup.lookup(null).isPresent());
        assertFalse(lookup.lookup("").isPresent());
        assertFalse(lookup.lookup("example.com").isPresent());
        assertFalse(lookup.lookup("abc").isPresent());
        assertFalse(lookup.lookup("1.2.3.4, 5.6.7.8").isPresent());
        assertEquals(0, calls.get());
    }

    @Test
    public void unknownIpOrSourceFailureOrMissingDatabaseGivesEmpty() {
        assertFalse(new IpAnonymizerLookup(address -> null).lookup("1.2.3.4").isPresent());
        assertFalse(new IpAnonymizerLookup(address -> {
            throw new IOException("corrupt database");
        }).lookup("1.2.3.4").isPresent());
        assertFalse(new IpAnonymizerLookup(null).lookup("1.2.3.4").isPresent());
    }

    @Test
    public void bundledDatabaseLoadsAndResolvesPublicAndPrivateAddresses() {
        assumeTrue(IpAnonymizerLookupTest.class.getClassLoader().getResource("maxmind/Merged-IP.mmdb") != null);

        IpAnonymizerLookup lookup = IpAnonymizerLookup.getInstance();

        assertTrue(lookup.lookup("8.8.8.8").isPresent());
        assertFalse(lookup.lookup("10.0.0.1").map(IpAnonymizerLookup.Info::isVpnOrTor).orElse(false));
    }
}
