package com.akto.utils;

import java.util.Collections;
import java.util.Map;

/**
 * Routes requests made with specific tokens to a fixed account: its accountId overrides the token's own claim
 * and the akto_account_id the caller put in the body, and its traffic goes to that account's Kafka topic
 * instead of this deployment's AKTO_KAFKA_TOPIC. Applies whether or not AKTO_DI_AUTHENTICATE is on; see
 * AuthFilter. Mirrors tokenAccountReroutes in guardrails-service (pkg/auth/middleware.go) and
 * TOKEN_ACCOUNT_REROUTES in mini-runtime's SaasGuardrailForwarder.
 */
public final class TokenReroutes {

    /** Where a rerouted token's traffic goes. */
    public static final class Route {
        public final int accountId;
        // Follows the deployments' AKTO_KAFKA_TOPIC format: akto.ai.traffic.${ACCOUNT_ID}-${CLIENT}
        public final String kafkaTopic;

        Route(int accountId, String kafkaTopic) {
            this.accountId = accountId;
            this.kafkaTopic = kafkaTopic;
        }
    }

    // Token suffix -> where that token's requests are routed.
    private static final Map<String, Route> REROUTES = Collections.singletonMap(
            "sqJVMK_iIk2qFaNjE0jmMsyo_x4Ubfw", new Route(1758787662, "akto.ai.traffic.1758787662-acorns"));

    // The route of the request on this thread, set by AuthFilter for the request's duration only.
    private static final ThreadLocal<Route> CURRENT = new ThreadLocal<>();

    private TokenReroutes() {}

    /** The route for a token, or null when its suffix isn't mapped. */
    public static Route routeFor(String token) {
        if (token != null) {
            String trimmed = token.trim();
            for (Map.Entry<String, Route> reroute : REROUTES.entrySet()) {
                if (trimmed.endsWith(reroute.getKey())) {
                    return reroute.getValue();
                }
            }
        }
        return null;
    }

    /** The route of the request being handled on this thread, or null when it wasn't rerouted. */
    public static Route current() {
        return CURRENT.get();
    }

    /** Marks this thread's request as rerouted; pair with {@link #clearCurrent()}. */
    public static void setCurrent(Route route) {
        CURRENT.set(route);
    }

    public static void clearCurrent() {
        CURRENT.remove();
    }
}
