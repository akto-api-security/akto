package com.akto.threat.detection.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.akto.IpAnonymizerLookup;
import com.akto.dao.monitoring.FilterConfigYamlParser;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.api_protection_parse_layer.Condition;
import com.akto.dto.api_protection_parse_layer.Rule;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SampleMaliciousRequest;
import com.akto.threat.detection.cache.CounterCache;
import com.akto.threat.detection.smart_event_detector.window_based.WindowBasedThresholdNotifier;
import com.akto.threat.detection.utils.ThreatDetector;

/**
 * Runs the VpnDetection policy template (src/test/resources/policies/VpnDetection.yaml, the same
 * YAML that is inserted into the local DB) through the real filter branch and aggregation.
 */
public class VpnDetectionPolicyTest {

    private static final String VPN_IP = "51.38.0.1";
    private static final String OTHER_VPN_IP = "185.220.101.1";
    private static final String CLEAN_IP = "24.0.0.1";

    private FilterConfig policy;
    private Rule rule;
    private ThreatDetector detector;
    private WindowBasedThresholdNotifier notifier;

    private static String readPolicy() throws Exception {
        try (InputStream in = VpnDetectionPolicyTest.class.getClassLoader().getResourceAsStream("policies/VpnDetection.yaml");
             Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
            return scanner.next();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        policy = FilterConfigYamlParser.parseTemplate(readPolicy(), false);
        rule = policy.getAggregationRules().getRule().get(0);

        Map<String, Map<String, Object>> records = new HashMap<>();
        records.put(VPN_IP, vpnRecord());
        records.put(OTHER_VPN_IP, vpnRecord());
        records.put(CLEAN_IP, new HashMap<>());
        detector = new ThreatDetector();
        detector.setIpAnonymizerLookup(new IpAnonymizerLookup(address -> records.get(address.getHostAddress())));

        Map<String, Long> counters = new HashMap<>();
        CounterCache cache = mock(CounterCache.class);
        doAnswer(inv -> counters.merge(inv.<String>getArgument(0), 1L, Long::sum)).when(cache).increment(anyString());
        when(cache.exists(anyString())).thenAnswer(inv -> counters.containsKey(inv.<String>getArgument(0)));
        when(cache.get(anyString())).thenAnswer(inv -> counters.getOrDefault(inv.<String>getArgument(0), 0L));
        doAnswer(inv -> counters.put(inv.<String>getArgument(0), 0L)).when(cache).reset(anyString());
        notifier = new WindowBasedThresholdNotifier(cache, new WindowBasedThresholdNotifier.Config(100, 10 * 60));
    }

    private static Map<String, Object> vpnRecord() {
        Map<String, Object> proxy = new HashMap<>();
        proxy.put("is_vpn", true);
        Map<String, Object> record = new HashMap<>();
        record.put("proxy", proxy);
        return record;
    }

    /** One request from sourceIp at the given minute; true when the policy raises an alert. */
    private boolean request(String sourceIp, int minute) {
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(new HttpRequestParams());
        params.setSourceIP(sourceIp);

        if (!detector.applyFilter(policy, params, null, null, null)) {
            return false;
        }
        SampleMaliciousRequest event = SampleMaliciousRequest.newBuilder().setTimestamp(minute * 60L).build();
        return notifier.shouldNotify(sourceIp + "|" + policy.getId(), event, rule, true, true);
    }

    @Test
    void policyParsesWithAggregationAndNoGrouping() {
        Condition condition = rule.getCondition();

        assertThat(policy.getId()).isEqualTo(ThreatDetector.VPN_DETECTION_FILTER_ID);
        assertThat(policy.getInfo().getSeverity()).isEqualTo("MEDIUM");
        assertThat(policy.getInfo().getCategory().getName()).isEqualTo("VpnDetection");
        assertThat(condition.getMatchCount()).isEqualTo(5);
        assertThat(condition.getWindowThreshold()).isEqualTo(10);
        assertThat(condition.getGroupBy()).isNull();
        assertThat(condition.getDistinctIdentifier()).isNull();
    }

    @Test
    void alertsOnTheFifthVpnRequestFromOneIp() {
        for (int i = 1; i <= 4; i++) {
            assertThat(request(VPN_IP, 0)).as("request %d", i).isFalse();
        }
        assertThat(request(VPN_IP, 0)).isTrue();
    }

    @Test
    void cleanIpNeverAlerts() {
        for (int i = 0; i < 20; i++) {
            assertThat(request(CLEAN_IP, i % 10)).isFalse();
        }
    }

    @Test
    void countsAreKeptPerSourceIp() {
        for (int i = 0; i < 4; i++) {
            assertThat(request(VPN_IP, 0)).isFalse();
            assertThat(request(OTHER_VPN_IP, 0)).isFalse();
        }
        assertThat(request(VPN_IP, 0)).isTrue();
        assertThat(request(OTHER_VPN_IP, 0)).isTrue();
    }

    @Test
    void requestsOutsideTheTenMinuteWindowDoNotAccumulate() {
        for (int i = 0; i < 4; i++) {
            assertThat(request(VPN_IP, 0)).isFalse();
        }
        // window is bins [minute-9, minute]; minute 10 no longer covers minute 0
        assertThat(request(VPN_IP, 10)).isFalse();
    }

    @Test
    void requestsInsideTheWindowAccumulate() {
        for (int i = 0; i < 4; i++) {
            assertThat(request(VPN_IP, 0)).isFalse();
        }
        assertThat(request(VPN_IP, 9)).isTrue();
    }
}
