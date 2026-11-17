package com.akto.filters;

import com.akto.dao.context.Context;
import com.akto.utils.TokenReroutes;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AuthFilterTest {

    private static final String SUFFIX = "sqJVMK_iIk2qFaNjE0jmMsyo_x4Ubfw";
    private static final int OTHER_ACCOUNT = 1718012345;

    private static KeyPair signingKeys;
    private static KeyPair otherKeys;

    @BeforeClass
    public static void keys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        signingKeys = gen.generateKeyPair();
        otherKeys = gen.generateKeyPair();
    }

    @After
    public void clearThread() {
        Context.accountId.remove();
        TokenReroutes.clearCurrent();
    }

    // ---- deployment check (unchanged behaviour) ----

    @Test
    public void tokenForThisDeploymentIsAccepted() {
        assertTrue(AuthFilter.isForThisDeployment(1718012345, 1718012345));
    }

    @Test
    public void tokenForAnotherAccountIsRejected() {
        // validly signed tokens of every account share one key; only the account tells them apart
        assertFalse(AuthFilter.isForThisDeployment(1718012345, 1718000000));
    }

    @Test
    public void unknownDeploymentAccountAcceptsAnyAccount() {
        // no DATABASE_ABSTRACTOR_SERVICE_TOKEN on this deployment: behave as before the check
        assertTrue(AuthFilter.isForThisDeployment(1718012345, null));
    }

    // ---- reroute through doFilter ----

    /** A validly signed token issued for OTHER_ACCOUNT. */
    private static String signed(KeyPair keys) {
        return Jwts.builder().claim("accountId", OTHER_ACCOUNT).signWith(keys.getPrivate(), SignatureAlgorithm.RS256).compact();
    }

    /**
     * The real token's signature ends in SUFFIX; a test can't mint that, so the verifier strips a "~SUFFIX" tail
     * before checking the signature with signingKeys. A token signed with otherKeys still fails verification.
     */
    private static final AuthFilter.TokenVerifier VERIFIER = token -> {
        String jws = token.endsWith("~" + SUFFIX) ? token.substring(0, token.length() - SUFFIX.length() - 1) : token;
        return Jwts.parserBuilder().setSigningKey(signingKeys.getPublic()).build().parseClaimsJws(jws);
    };

    private static String rerouted(KeyPair keys) {
        return signed(keys) + "~" + SUFFIX;
    }

    /** What the downstream chain saw, or null if the chain never ran. */
    private static final class Seen {
        Integer contextAccount;
        TokenReroutes.Route route;
        int status;
    }

    private static Seen run(boolean authEnabled, String token) throws Exception {
        Seen seen = new Seen();
        boolean[] chainRan = {false};
        FilterChain chain = (req, res) -> {
            chainRan[0] = true;
            seen.contextAccount = Context.accountId.get();
            seen.route = TokenReroutes.current();
        };
        new AuthFilter(VERIFIER, () -> authEnabled).doFilter(request(token), response(seen), chain);
        // Nothing may leak onto the pooled thread once the request is done.
        assertNull("route leaked onto the thread", TokenReroutes.current());
        if (!authEnabled) {
            assertNull("account leaked onto the thread", Context.accountId.get());
        }
        return chainRan[0] ? seen : null;
    }

    @Test
    public void authOff_verifiedReroutedToken_routesToMappedAccountAndTopic() throws Exception {
        Seen seen = run(false, rerouted(signingKeys));
        assertEquals(Integer.valueOf(1758787662), seen.contextAccount);
        assertEquals(1758787662, seen.route.accountId);
        assertEquals("akto.ai.traffic.1758787662-acorns", seen.route.kafkaTopic);
    }

    @Test
    public void authOff_forgedSuffix_isNotRerouted() throws Exception {
        // anyone can read the suffix from source: without a valid signature it must not reroute
        Seen seen = run(false, "forged-" + SUFFIX);
        assertNull(seen.contextAccount);
        assertNull(seen.route);

        seen = run(false, rerouted(otherKeys));
        assertNull(seen.contextAccount);
        assertNull(seen.route);
    }

    @Test
    public void authOff_otherToken_passesThroughUntouched() throws Exception {
        Seen seen = run(false, signed(signingKeys));
        assertNull(seen.contextAccount);
        assertNull(seen.route);
    }

    @Test
    public void authOn_reroutedToken_overridesClaimAndRoutes() throws Exception {
        Seen seen = run(true, rerouted(signingKeys));
        assertEquals(Integer.valueOf(1758787662), seen.contextAccount);
        assertEquals("akto.ai.traffic.1758787662-acorns", seen.route.kafkaTopic);
    }

    @Test
    public void authOn_otherToken_keepsItsOwnAccountAndNoRoute() throws Exception {
        Seen seen = run(true, signed(signingKeys));
        assertEquals(Integer.valueOf(OTHER_ACCOUNT), seen.contextAccount);
        assertNull(seen.route);
    }

    @Test
    public void authOn_forgedReroutedToken_isRejected() throws Exception {
        assertNull("chain must not run", run(true, rerouted(otherKeys)));
        assertNull("chain must not run", run(true, "forged-" + SUFFIX));
    }

    @Test
    public void routeIsClearedEvenWhenTheChainThrows() throws Exception {
        FilterChain throwing = (req, res) -> { throw new RuntimeException("boom"); };
        for (boolean authEnabled : new boolean[]{false, true}) {
            try {
                new AuthFilter(VERIFIER, () -> authEnabled).doFilter(request(rerouted(signingKeys)), response(new Seen()), throwing);
            } catch (RuntimeException expected) {
                // expected
            }
            assertNull(TokenReroutes.current());
            if (!authEnabled) {
                assertNull(Context.accountId.get());
            }
        }
    }

    // ---- minimal servlet stubs (no mocking library in this module) ----

    private static HttpServletRequest request(String authorization) {
        Map<String, String> headers = new HashMap<>();
        headers.put("authorization", authorization);
        return (HttpServletRequest) Proxy.newProxyInstance(AuthFilterTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getHeader")) {
                        return headers.get(((String) args[0]).toLowerCase());
                    }
                    return null;
                });
    }

    private static HttpServletResponse response(Seen seen) {
        PrintWriter writer = new PrintWriter(new StringWriter());
        return (HttpServletResponse) Proxy.newProxyInstance(AuthFilterTest.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setStatus":
                            seen.status = (Integer) args[0];
                            return null;
                        case "getWriter":
                            return writer;
                        default:
                            return null;
                    }
                });
    }
}
