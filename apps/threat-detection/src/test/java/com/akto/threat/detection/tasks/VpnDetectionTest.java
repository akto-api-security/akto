package com.akto.threat.detection.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.akto.IpAnonymizerLookup;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.threat.detection.utils.ThreatDetector;

public class VpnDetectionTest {

    private static final String VPN_IP = "51.38.0.1";
    private static final String TOR_IP = "185.220.101.1";
    private static final String HOSTING_IP = "5.9.1.1";
    private static final String CLEAN_IP = "24.0.0.1";
    private static final String UNKNOWN_IP = "192.0.2.1";
    private static final String VPN_NO_HOSTING_IP = "1.21.115.42";
    private static final String VPN_NO_ORG_IP = "192.0.2.50";
    private static final String TOR_NO_ORG_IP = "192.0.2.51";

    private ThreatDetector detector;
    private FilterConfig vpnFilter;

    private static Map<String, Object> record(String asnOrg, String... trueFlags) {
        Map<String, Object> proxy = new HashMap<>();
        for (String flag : trueFlags) {
            proxy.put(flag, true);
        }
        Map<String, Object> asn = new HashMap<>();
        asn.put("autonomous_system_organization", asnOrg);
        Map<String, Object> record = new HashMap<>();
        record.put("asn", asn);
        if (!proxy.isEmpty()) {
            record.put("proxy", proxy);
        }
        return record;
    }

    private static HttpResponseParams paramsFrom(String sourceIp) {
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(new HttpRequestParams());
        params.setSourceIP(sourceIp);
        return params;
    }

    private boolean matches(String sourceIp) {
        return detector.applyFilter(vpnFilter, paramsFrom(sourceIp), null, null, null);
    }

    @BeforeEach
    void setUp() throws Exception {
        Map<String, Map<String, Object>> records = new HashMap<>();
        records.put(VPN_IP, record("OVH SAS", "is_vpn", "is_hosting", "is_proxy", "is_anonymous"));
        records.put(TOR_IP, record("Stiftung Erneuerbare Freiheit", "is_tor", "is_vpn", "is_hosting"));
        records.put(HOSTING_IP, record("Hetzner Online GmbH", "is_hosting", "is_proxy", "is_anonymous"));
        records.put(CLEAN_IP, record("Comcast Cable Communications, LLC"));
        records.put(VPN_NO_HOSTING_IP, record("Example VPN Ltd", "is_vpn", "is_proxy", "is_anonymous"));
        records.put(VPN_NO_ORG_IP, record(null, "is_vpn"));
        records.put(TOR_NO_ORG_IP, record(null, "is_tor"));

        detector = new ThreatDetector();
        detector.setIpAnonymizerLookup(new IpAnonymizerLookup(address -> records.get(address.getHostAddress())));
        vpnFilter = new FilterConfig(ThreatDetector.VPN_DETECTION_FILTER_ID, null, null, null);
    }

    @Test
    void vpnIpMatches() {
        assertThat(matches(VPN_IP)).isTrue();
    }

    @Test
    void torIpMatches() {
        assertThat(matches(TOR_IP)).isTrue();
    }

    @Test
    void hostingOnlyIpDoesNotMatch() {
        assertThat(matches(HOSTING_IP)).isFalse();
    }

    @Test
    void cleanAndUnknownIpsDoNotMatch() {
        assertThat(matches(CLEAN_IP)).isFalse();
        assertThat(matches(UNKNOWN_IP)).isFalse();
    }

    @Test
    void missingSourceIpDoesNotMatch() {
        assertThat(matches(null)).isFalse();
        assertThat(detector.getVpnReason(null)).isNull();
    }

    @Test
    void disabledDatabaseNeverMatches() {
        detector.setIpAnonymizerLookup(new IpAnonymizerLookup(null));

        assertThat(matches(VPN_IP)).isFalse();
        assertThat(matches(TOR_IP)).isFalse();
    }

    @Test
    void vpnReasonNamesTheIpAndNetworkProvider() {
        assertThat(detector.getVpnReason(paramsFrom(VPN_IP)))
            .isEqualTo("IP 51.38.0.1 is listed as a VPN exit (network provider: OVH SAS).");
    }

    @Test
    void vpnReasonIsTheSameWhetherOrNotTheIpIsInAHostingRange() {
        assertThat(detector.getVpnReason(paramsFrom(VPN_NO_HOSTING_IP)))
            .isEqualTo("IP 1.21.115.42 is listed as a VPN exit (network provider: Example VPN Ltd).");
    }

    @Test
    void torTakesPrecedenceInTheReason() {
        assertThat(detector.getVpnReason(paramsFrom(TOR_IP)))
            .isEqualTo("Tor exit node 185.220.101.1 (network provider: Stiftung Erneuerbare Freiheit). Tor hides the client's real origin.");
    }

    @Test
    void reasonOmitsTheProviderWhenItIsUnknown() {
        assertThat(detector.getVpnReason(paramsFrom(VPN_NO_ORG_IP)))
            .isEqualTo("IP 192.0.2.50 is listed as a VPN exit.");
        assertThat(detector.getVpnReason(paramsFrom(TOR_NO_ORG_IP)))
            .isEqualTo("Tor exit node 192.0.2.51. Tor hides the client's real origin.");
    }

    @Test
    void noReasonWhenNothingMatches() {
        assertThat(detector.getVpnReason(paramsFrom(CLEAN_IP))).isNull();
        assertThat(detector.getVpnReason(paramsFrom(HOSTING_IP))).isNull();
    }
}
