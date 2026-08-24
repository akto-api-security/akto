package com.akto.filter;

import com.akto.listener.InfraMetricsListener;
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
 * Verifies MetricsAuthFilter's fail-closed auth. The token is injected via the package-private
 * static test seam so the test does not depend on environment variables. The same static logic
 * (MetricsAuthFilter.isAuthorized) is what InfraMetricsAction relies on via the filter.
 *
 * Servlet types are faked with java.lang.reflect.Proxy (JDK-native) rather than a mocking library:
 * the Mockito version on the classpath (1.x/cglib) cannot initialize on JDK 17.
 */
public class MetricsAuthFilterTest {

    @After
    public void reset() {
        MetricsAuthFilter.setExpectedTokenForTest(null);
        MetricsAuthFilter.setAuthEnabledForTest(true);
        InfraMetricsListener.setEnabledForTest(false);
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

    // ---- filter behavior ----

    @Test
    public void notEnabled_returns404_andDoesNotChain() throws Exception {
        InfraMetricsListener.setEnabledForTest(false);
        MetricsAuthFilter.setExpectedTokenForTest("secret");
        ResponseHandler resp = new ResponseHandler();
        ChainHandler chain = new ChainHandler();

        new MetricsAuthFilter().doFilter(request("Bearer secret"), response(resp), chain(chain));

        assertEquals(Integer.valueOf(HttpServletResponse.SC_NOT_FOUND), resp.sentError);
        assertFalse(chain.called);
    }

    @Test
    public void authDisabled_servesWithoutToken() throws Exception {
        InfraMetricsListener.setEnabledForTest(true);
        MetricsAuthFilter.setAuthEnabledForTest(false); // opt-out
        MetricsAuthFilter.setExpectedTokenForTest(null);
        ResponseHandler resp = new ResponseHandler();
        ChainHandler chain = new ChainHandler();

        new MetricsAuthFilter().doFilter(request(null), response(resp), chain(chain));

        assertNull(resp.sentError);
        assertTrue(chain.called);
    }

    @Test
    public void authDisabledButFeatureOff_stillReturns404() throws Exception {
        InfraMetricsListener.setEnabledForTest(false);
        MetricsAuthFilter.setAuthEnabledForTest(false);
        ResponseHandler resp = new ResponseHandler();
        ChainHandler chain = new ChainHandler();

        new MetricsAuthFilter().doFilter(request(null), response(resp), chain(chain));

        assertEquals(Integer.valueOf(HttpServletResponse.SC_NOT_FOUND), resp.sentError);
        assertFalse(chain.called);
    }

    @Test
    public void enabledButTokenUnset_failsClosedWith401() throws Exception {
        MetricsAuthFilter.setAuthEnabledForTest(true);
        InfraMetricsListener.setEnabledForTest(true);
        MetricsAuthFilter.setExpectedTokenForTest(null);
        ResponseHandler resp = new ResponseHandler();
        ChainHandler chain = new ChainHandler();

        new MetricsAuthFilter().doFilter(request("Bearer whatever"), response(resp), chain(chain));

        assertEquals(Integer.valueOf(HttpServletResponse.SC_UNAUTHORIZED), resp.sentError);
        assertFalse(chain.called);
    }

    @Test
    public void enabledWithTokenMismatch_returns401() throws Exception {
        InfraMetricsListener.setEnabledForTest(true);
        MetricsAuthFilter.setExpectedTokenForTest("secret");
        ResponseHandler resp = new ResponseHandler();
        ChainHandler chain = new ChainHandler();

        new MetricsAuthFilter().doFilter(request("Bearer wrong"), response(resp), chain(chain));

        assertEquals(Integer.valueOf(HttpServletResponse.SC_UNAUTHORIZED), resp.sentError);
        assertFalse(chain.called);
    }

    @Test
    public void enabledWithMissingHeader_returns401() throws Exception {
        InfraMetricsListener.setEnabledForTest(true);
        MetricsAuthFilter.setExpectedTokenForTest("secret");
        ResponseHandler resp = new ResponseHandler();
        ChainHandler chain = new ChainHandler();

        new MetricsAuthFilter().doFilter(request(null), response(resp), chain(chain));

        assertEquals(Integer.valueOf(HttpServletResponse.SC_UNAUTHORIZED), resp.sentError);
        assertFalse(chain.called);
    }

    @Test
    public void enabledWithMatchingBearerToken_passesThrough() throws Exception {
        InfraMetricsListener.setEnabledForTest(true);
        MetricsAuthFilter.setExpectedTokenForTest("secret");
        ResponseHandler resp = new ResponseHandler();
        ChainHandler chain = new ChainHandler();

        new MetricsAuthFilter().doFilter(request("Bearer secret"), response(resp), chain(chain));

        assertNull(resp.sentError);
        assertTrue(chain.called);
    }

    @Test
    public void enabledWithBareToken_passesThrough() throws Exception {
        InfraMetricsListener.setEnabledForTest(true);
        MetricsAuthFilter.setExpectedTokenForTest("secret");
        ResponseHandler resp = new ResponseHandler();
        ChainHandler chain = new ChainHandler();

        new MetricsAuthFilter().doFilter(request("secret"), response(resp), chain(chain)); // no Bearer prefix

        assertNull(resp.sentError);
        assertTrue(chain.called);
    }

    // ---- static authorizer ----

    @Test
    public void isAuthorized_failsClosedWhenTokenUnset() {
        MetricsAuthFilter.setExpectedTokenForTest(null);
        assertFalse(MetricsAuthFilter.isAuthorized("Bearer anything"));
        assertFalse(MetricsAuthFilter.isTokenConfigured());
    }

    @Test
    public void isAuthorized_matchesBearerAndBareToken() {
        MetricsAuthFilter.setExpectedTokenForTest("secret");
        assertTrue(MetricsAuthFilter.isAuthorized("Bearer secret"));
        assertTrue(MetricsAuthFilter.isAuthorized("secret"));
        assertFalse(MetricsAuthFilter.isAuthorized("Bearer wrong"));
        assertFalse(MetricsAuthFilter.isAuthorized(null));
        assertTrue(MetricsAuthFilter.isTokenConfigured());
    }
}
