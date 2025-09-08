package com.akto.threat.detection.tasks;

import com.akto.dto.HttpResponseParams;
import com.akto.dto.HttpRequestParams;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ActorId;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.Actor;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ThreatConfigurationEvaluatorTest {

    private ThreatConfigurationEvaluator evaluator;
    private HttpResponseParams responseParam;
    private String HostNameRegex = "dev\\..*\\.com";
    private String HostNameRegexTwo = "prod\\..*\\.com";
    private String ClientIP = "14.23.12.34";

    @BeforeEach
    public void setup() {
        ThreatConfiguration threatConfiguration = ThreatConfiguration.newBuilder()
                .setActor(Actor.newBuilder()
                        .addActorId(ActorId.newBuilder()
                                .setKind(ThreatConfigurationEvaluator.ThreatActorRuleKind.HOSTNAME.name().toLowerCase())
                                .setPattern(HostNameRegex)
                                .setKey("authorization")
                                .setType("header")
                                .build())
                        .addActorId(ActorId.newBuilder()
                                .setKind(ThreatConfigurationEvaluator.ThreatActorRuleKind.HOSTNAME.name().toLowerCase())
                                .setPattern(HostNameRegexTwo)
                                .setKey("authorization")
                                .setType("header")
                                .build())
                        .build())
                .build();

        evaluator = new ThreatConfigurationEvaluator(threatConfiguration, null, null);

        responseParam = new HttpResponseParams();
        responseParam.setRequestParams(new HttpRequestParams());
        responseParam.getRequestParams().setUrl("/api/v3/pet/findByStatus");
        responseParam.getRequestParams().setMethod("GET");
        // Set the host and authorization headers
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("host", Collections.singletonList("dev.example.com"));
        headers.put("authorization", Collections.singletonList("Bearer-token-1"));
        headers.put("x-forwarded-for", Collections.singletonList(ClientIP));
        responseParam.getRequestParams()
                .setHeaders(headers);
    }

    @Test
    public void testIsHostNameMatching() {
        assertTrue(evaluator.isHostNameMatching(responseParam, HostNameRegex));
        responseParam.getRequestParams().getHeaders().put("host", Collections.singletonList("test.example.com"));
        assertFalse(evaluator.isHostNameMatching(responseParam, HostNameRegex));
    }

    @Test
    public void testGetActorIdWithHostNameMatching() {
        String actorIdResult = evaluator.getActorId(responseParam);
        assertEquals("Bearer-token-1", actorIdResult);
    }

    @Test
    public void testGetActorIdWithHostNameMatchingTwo() {
        responseParam.getRequestParams().getHeaders().put("host", Collections.singletonList("prod.example.com"));
        responseParam.getRequestParams().getHeaders().put("authorization", Collections.singletonList("Bearer-token-2"));
        // Simulate a case where the host name matches the second regex
        // and the authorization header is different
        String actorIdResult = evaluator.getActorId(responseParam);
        assertEquals("Bearer-token-2", actorIdResult);
    }

    @Test
    public void testGetActorIdFallbackToSourceIpHostNameNotMatching() {
        // Simulate a case where the host name does not match
        responseParam.getRequestParams().getHeaders().put("host", Collections.singletonList("test.example.com"));
        String actorIdResult = evaluator.getActorId(responseParam);
        assertEquals(ClientIP, actorIdResult);
    }

    @Test
    public void testGetActorIdWhenThreatConfigurationIsNull() {
        ThreatConfigurationEvaluator evaluatorWithNullConfig = new ThreatConfigurationEvaluator(null, null, null) {
            @Override
            public ThreatConfiguration getThreatConfiguration() {
                return null;
            }
        };
        // Should fallback to source IP
        String actorIdResult = evaluatorWithNullConfig.getActorId(responseParam);
        assertEquals(ClientIP, actorIdResult);
    }

    @Test
    public void testGetActorIdWhenHeaderNotFound() {
        // Remove the "authorization" header so it should fallback to source IP
        responseParam.getRequestParams().getHeaders().remove("authorization");
        String actorIdResult = evaluator.getActorId(responseParam);
        assertEquals(ClientIP, actorIdResult);
    }

    @Test
    public void testGetActorIdWhenHeaderIsEmpty() {
        // Set the "authorization" header to an empty list
        responseParam.getRequestParams().getHeaders().put("authorization", new ArrayList<>());
        String actorIdResult = evaluator.getActorId(responseParam);
        assertEquals(ClientIP, actorIdResult);
    }

    @Test
    public void testGetActorIdWhenMultipleActorIdsFirstDoesNotMatchSecondMatches() {
        // First actorId pattern does not match, second matches
        responseParam.getRequestParams().getHeaders().put("host", Collections.singletonList("prod.example.com"));
        responseParam.getRequestParams().getHeaders().put("authorization", Collections.singletonList("Bearer-token-2"));
        String actorIdResult = evaluator.getActorId(responseParam);
        assertEquals("Bearer-token-2", actorIdResult);
    }

}
