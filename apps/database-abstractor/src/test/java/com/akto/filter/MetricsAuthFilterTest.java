package com.akto.filter;

import com.akto.metrics.CyborgMetricsConfig;
import org.junit.After;
import org.junit.Test;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

/**
 * Verifies MetricsAuthFilter's fail-closed auth. Enabled/auth/token come from CyborgMetricsConfig,
 * driven here via its test seams so the test does not depend on environment variables.
 *
 * Servlet types are faked with java.lang.reflect.Proxy (JDK-native) rather than a mocking library:
 * the Mockito version on the classpath (1.x/cglib) cannot initialize on JDK 17.
 */
public class MetricsAuthFilterTest {

    @After
    public void reset() {
        CyborgMetricsConfig.setEnabledForTest(false);
        CyborgMetricsConfig.setAuthEnabledForTest(true);
        CyborgMetricsConfig.setAuthTokenForTest(null);
    }

    /** Records the status passed to sendError(...); null means the chain was allowed through. */
    private static class ResponseHandler implements InvocationHandler {
        Integer sentError = null;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("sendError".equals(method.getName())) {
                sentError = (Integer) args[0];
                return null;
            }
            return defaultValue(method.getReturnType());
        }
    }

    private static class ChainHandler implements InvocationHandler {
        boolean called = false;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("doFilter".equals(method.getName())) {
                called = true;
            }
            return defaultValue(method.getReturnType());
        }
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) return false;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        return null;
    }

    private HttpServletRequest request(final String authHeader) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getHeader".equals(name) && "Authorization".equals(args[0])) return authHeader;
                    if ("getRequestURI".equals(name)) return "/metrics";
                    return defaultValue(method.getReturnType());
                });
    }

    private HttpServletResponse response(ResponseHandler handler) {
        return (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletResponse.class}, handler);
    }

    private FilterChain chain(ChainHandler handler) {
        return (FilterChain) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{FilterChain.class}, handler);
    }

    private void run(String authHeader, ResponseHandler resp, ChainHandler chain) throws Exception {
        new MetricsAuthFilter().doFilter(request(authHeader), response(resp), chain(chain));
    }

    // ---- filter behavior ----

    @Test
    public void notEnabled_returns404_andDoesNotChain() throws Exception {
        CyborgMetricsConfig.setEnabledForTest(false);
        CyborgMetricsConfig.setAuthTokenForTest("secret");
        ResponseHandler resp = new ResponseHandler();
        ChainHandler chain = new ChainHandler();

        run("Bearer secret", resp, chain);

        assertEquals(Integer.valueOf(HttpServletResponse.SC_NOT_FOUND), resp.sentError);
        assertFalse(chain.called);
    }

    @Test
    public void authDisabled_servesWithoutToken() throws Exception {
        CyborgMetricsConfig.setEnabledForTest(true);
        CyborgMetricsConfig.setAuthEnabledForTest(false); // opt-out
        CyborgMetricsConfig.setAuthTokenForTest(null);
        ResponseHandler resp = new ResponseHandler();
        ChainHandler chain = new ChainHandler();

        run(null, resp, chain);

        assertNull(resp.sentError);
        assertTrue(chain.called);
    }

    @Test
    public void authDisabledButFeatureOff_stillReturns404() throws Exception {
        CyborgMetricsConfig.setEnabledForTest(false);
        CyborgMetricsConfig.setAuthEnabledForTest(false);
        ResponseHandler resp = new ResponseHandler();
        ChainHandler chain = new ChainHandler();

        run(null, resp, chain);

        assertEquals(Integer.valueOf(HttpServletResponse.SC_NOT_FOUND), resp.sentError);
        assertFalse(chain.called);
    }

    @Test
    public void enabledButTokenUnset_failsClosedWith401() throws Exception {
        CyborgMetricsConfig.setEnabledForTest(true);
        CyborgMetricsConfig.setAuthEnabledForTest(true);
        CyborgMetricsConfig.setAuthTokenForTest(null);
        ResponseHandler resp = new ResponseHandler();
        ChainHandler chain = new ChainHandler();

        run("Bearer whatever", resp, chain);

        assertEquals(Integer.valueOf(HttpServletResponse.SC_UNAUTHORIZED), resp.sentError);
        assertFalse(chain.called);
    }

    @Test
    public void enabledWithTokenMismatch_returns401() throws Exception {
        CyborgMetricsConfig.setEnabledForTest(true);
        CyborgMetricsConfig.setAuthTokenForTest("secret");
        ResponseHandler resp = new ResponseHandler();
        ChainHandler chain = new ChainHandler();

        run("Bearer wrong", resp, chain);

        assertEquals(Integer.valueOf(HttpServletResponse.SC_UNAUTHORIZED), resp.sentError);
        assertFalse(chain.called);
    }

    @Test
    public void enabledWithMissingHeader_returns401() throws Exception {
        CyborgMetricsConfig.setEnabledForTest(true);
        CyborgMetricsConfig.setAuthTokenForTest("secret");
        ResponseHandler resp = new ResponseHandler();
        ChainHandler chain = new ChainHandler();

        run(null, resp, chain);

        assertEquals(Integer.valueOf(HttpServletResponse.SC_UNAUTHORIZED), resp.sentError);
        assertFalse(chain.called);
    }

    @Test
    public void enabledWithMatchingBearerToken_passesThrough() throws Exception {
        CyborgMetricsConfig.setEnabledForTest(true);
        CyborgMetricsConfig.setAuthTokenForTest("secret");
        ResponseHandler resp = new ResponseHandler();
        ChainHandler chain = new ChainHandler();

        run("Bearer secret", resp, chain);

        assertNull(resp.sentError);
        assertTrue(chain.called);
    }

    @Test
    public void enabledWithBareToken_passesThrough() throws Exception {
        CyborgMetricsConfig.setEnabledForTest(true);
        CyborgMetricsConfig.setAuthTokenForTest("secret");
        ResponseHandler resp = new ResponseHandler();
        ChainHandler chain = new ChainHandler();

        run("secret", resp, chain); // no Bearer prefix

        assertNull(resp.sentError);
        assertTrue(chain.called);
    }

    // ---- static authorizer ----

    @Test
    public void isAuthorized_failsClosedWhenTokenUnset() {
        CyborgMetricsConfig.setAuthTokenForTest(null);
        assertFalse(MetricsAuthFilter.isAuthorized("Bearer anything"));
    }

    @Test
    public void isAuthorized_matchesBearerAndBareToken() {
        CyborgMetricsConfig.setAuthTokenForTest("secret");
        assertTrue(MetricsAuthFilter.isAuthorized("Bearer secret"));
        assertTrue(MetricsAuthFilter.isAuthorized("secret"));
        assertFalse(MetricsAuthFilter.isAuthorized("Bearer wrong"));
        assertFalse(MetricsAuthFilter.isAuthorized(null));
    }
}
