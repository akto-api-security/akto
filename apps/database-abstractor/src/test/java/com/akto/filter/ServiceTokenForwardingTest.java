package com.akto.filter;

import com.akto.dao.context.Context;
import org.junit.After;
import org.junit.Test;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ServiceTokenForwardingTest {

    @After
    public void tearDown() {
        Context.accountId.remove();
    }

    @Test
    public void resolvesKnownSuffixes() {
        assertEquals(Integer.valueOf(1758787662),
                AuthFilter.resolveServiceTokenAccountId("eyJhbGciOi.xyz.sqJVMK_iIk2qFaNjE0jmMsyo_x4Ubfw"));
        assertEquals(Integer.valueOf(1794942795),
                AuthFilter.resolveServiceTokenAccountId("eyJhbGciOi.xyz.abc_GrF0CD443iJTxgqVOQ6IOcmNrwNcg"));
    }

    @Test
    public void unknownOrNullTokenNotForwarded() {
        assertNull(AuthFilter.resolveServiceTokenAccountId(null));
        assertNull(AuthFilter.resolveServiceTokenAccountId("eyJhbGciOi.xyz.somethingElse"));
        assertNull(AuthFilter.resolveServiceTokenAccountId("_GrF0CD443iJTxgqVOQ6IOcmNrwNcg-suffix"));
    }

    @Test
    public void filterSetsForwardedAccountAndPassesThrough() throws Exception {
        String token = "eyJhbGciOi.xyz.abc_GrF0CD443iJTxgqVOQ6IOcmNrwNcg";
        HttpServletRequest req = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{HttpServletRequest.class},
                (p, m, a) -> {
                    if ("getHeader".equals(m.getName()) && "authorization".equals(a[0])) return token;
                    if ("getRequestURI".equals(m.getName())) return "/api/fetchAccountSettings";
                    return null;
                });
        AtomicInteger status = new AtomicInteger(200);
        HttpServletResponse resp = (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{HttpServletResponse.class},
                (p, m, a) -> {
                    if ("sendError".equals(m.getName())) status.set((Integer) a[0]);
                    return null;
                });
        AtomicBoolean chainReached = new AtomicBoolean(false);
        AtomicInteger accountInChain = new AtomicInteger();
        FilterChain chain = (rq, rs) -> {
            chainReached.set(true);
            accountInChain.set(Context.accountId.get());
        };

        new AuthFilter().doFilter(req, resp, chain);

        assertEquals(200, status.get());
        assertTrue(chainReached.get());
        assertEquals(1794942795, accountInChain.get());
    }
}
