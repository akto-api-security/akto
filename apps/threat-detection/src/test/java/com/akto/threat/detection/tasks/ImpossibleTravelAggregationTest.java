package com.akto.threat.detection.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.RawApiMetadata;
import com.akto.dto.api_protection_parse_layer.Condition;
import com.akto.dto.api_protection_parse_layer.Condition.DistinctIdentifier;
import com.akto.dto.api_protection_parse_layer.Condition.ValueSource;
import com.akto.dto.api_protection_parse_layer.Rule;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SampleMaliciousRequest;
import com.akto.threat.detection.cache.CounterCache;
import com.akto.threat.detection.smart_event_detector.window_based.WindowBasedThresholdNotifier;
import com.akto.threat.detection.utils.Utils;

/**
 * Impossible travel = groupBy identity + distinct country_code, evaluated by the
 * existing distinct-mode WindowBasedThresholdNotifier. The cache is an in-memory fake
 * of the Redis-backed sets, same approach as ParamEnumerationDetectorTest.
 */
public class ImpossibleTravelAggregationTest {

    private static final String FILTER_ID = "ImpossibleTravel";
    private static final String DEFAULT_AGG_KEY = "1.1.1.1|" + FILTER_ID;
    private static final int WINDOW_MINUTES = 30;

    private WindowBasedThresholdNotifier notifier;
    private Rule rule;

    @BeforeEach
    void setUp() {
        Map<String, Set<String>> sets = new HashMap<>();
        CounterCache cache = mock(CounterCache.class);
        doAnswer(inv -> sets.computeIfAbsent(inv.getArgument(0), k -> new HashSet<>()).add(inv.getArgument(1)))
                .when(cache).addToSet(anyString(), anyString());
        when(cache.getSetMembers(anyString()))
                .thenAnswer(inv -> sets.getOrDefault(inv.<String>getArgument(0), Collections.emptySet()));
        doAnswer(inv -> sets.remove(inv.<String>getArgument(0))).when(cache).resetSet(anyString());

        notifier = new WindowBasedThresholdNotifier(cache, new WindowBasedThresholdNotifier.Config(100, 10 * 60));

        DistinctIdentifier distinct = new DistinctIdentifier();
        distinct.setAttribute("country_code");
        distinct.setCount(2);
        Condition condition = new Condition();
        condition.setWindowThreshold(WINDOW_MINUTES);
        condition.setGroupBy(new ValueSource("request_headers", "x-user-id"));
        condition.setDistinctIdentifier(distinct);
        rule = new Rule("Rule 1", condition);
    }

    /** Mirrors the glue in MaliciousTrafficDetectorTask for one request. */
    private boolean request(String userId, String country, int minute) {
        Map<String, List<String>> headers = new HashMap<>();
        if (userId != null) {
            headers.put("x-user-id", Collections.singletonList(userId));
        }
        HttpRequestParams requestParams = new HttpRequestParams();
        requestParams.setHeaders(headers);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(requestParams);

        String aggKey = Utils.buildAggKey(DEFAULT_AGG_KEY, rule.getCondition().getGroupBy(), params, FILTER_ID);
        if (aggKey == null) {
            return false;
        }
        String identity = Utils.extractDistinctValue(params, new RawApiMetadata(country), rule.getCondition().getDistinctIdentifier());
        SampleMaliciousRequest event = SampleMaliciousRequest.newBuilder().setTimestamp(minute * 60L).build();
        return notifier.shouldNotify(aggKey, event, rule, true, true, identity);
    }

    @Test
    void firesWhenSameUserSeenFromTwoCountries() {
        assertThat(request("u1", "DE", 0)).isFalse();
        assertThat(request("u1", "FR", 5)).isTrue();
    }

    @Test
    void keepsFiringOnLaterRequestsWhileOldCountryIsStillInWindow() {
        assertThat(request("u1", "DE", 0)).isFalse();
        assertThat(request("u1", "FR", 5)).isTrue();
        assertThat(request("u1", "FR", 6)).isTrue();
        assertThat(request("u1", "FR", 7)).isTrue();
    }

    @Test
    void doesNotFireForSameCountryRepeated() {
        for (int minute = 0; minute < 10; minute++) {
            assertThat(request("u1", "DE", minute)).isFalse();
        }
    }

    @Test
    void usersAreCountedIndependently() {
        assertThat(request("u1", "DE", 0)).isFalse();
        assertThat(request("u2", "FR", 1)).isFalse();
        assertThat(request("u3", "US", 2)).isFalse();
    }

    @Test
    void countryChangeInsideWindowFiresAndOutsideDoesNot() {
        assertThat(request("inside", "DE", 0)).isFalse();
        assertThat(request("inside", "FR", WINDOW_MINUTES - 1)).isTrue();

        assertThat(request("outside", "DE", 0)).isFalse();
        assertThat(request("outside", "FR", WINDOW_MINUTES)).isFalse();
    }

    @Test
    void requestWithoutIdentityIsIgnored() {
        assertThat(request(null, "DE", 0)).isFalse();
        assertThat(request(null, "FR", 1)).isFalse();
        // nothing was recorded for the anonymous requests, so a real user starts clean
        assertThat(request("u1", "FR", 2)).isFalse();
    }

    @Test
    void requestWithoutCountryIsNotCounted() {
        assertThat(request("u1", "DE", 0)).isFalse();
        assertThat(request("u1", "", 1)).isFalse();
        assertThat(request("u1", null, 2)).isFalse();
    }

    @Test
    void firedEventCarriesReasonWithIdentityAndCountries() {
        DistinctIdentifier distinct = rule.getCondition().getDistinctIdentifier();
        String aggKey = "u1|" + FILTER_ID;
        SampleMaliciousRequest first = SampleMaliciousRequest.newBuilder().setTimestamp(0).build();
        SampleMaliciousRequest second = SampleMaliciousRequest.newBuilder().setTimestamp(5 * 60L).build();

        assertThat(notifier.checkDistinct(aggKey, first, rule, true, true, "DE")).isNull();
        Set<String> members = notifier.checkDistinct(aggKey, second, rule, true, true, "FR");
        assertThat(members).containsExactlyInAnyOrder("DE", "FR");

        String reason = Utils.buildDistinctReason("x-user-id=u1", distinct, members, WINDOW_MINUTES);
        assertThat(reason).isEqualTo("x-user-id=u1: 2 distinct country_code within 30 min (DE, FR)");

        SampleMaliciousRequest event = Utils.withReason(second, reason);
        assertThat(event.getMetadata().getReason()).isEqualTo(reason);
    }

    @Test
    void reasonCapsLongMemberLists() {
        Set<String> members = new HashSet<>();
        for (int i = 0; i < 12; i++) {
            members.add(String.format("user%02d", i));
        }
        DistinctIdentifier distinct = new DistinctIdentifier(5, "request_payload", "email");

        String reason = Utils.buildDistinctReason("ip=1.2.3.4", distinct, members, 10);

        assertThat(reason).startsWith("ip=1.2.3.4: 12 distinct email within 10 min (user00, user01");
        assertThat(reason).endsWith("user09 +2 more)");
    }

    @Test
    void absentGroupByKeepsActorKey() {
        HttpResponseParams params = new HttpResponseParams();
        assertThat(Utils.buildAggKey(DEFAULT_AGG_KEY, null, params, FILTER_ID)).isEqualTo(DEFAULT_AGG_KEY);
    }
}
