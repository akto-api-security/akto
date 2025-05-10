package com.akto.filter;

import com.akto.dao.context.Context;
import com.akto.utils.RateLimitCache;
import io.github.bucket4j.*;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimitFilter implements Filter {
    private final RateLimitCache cache = new RateLimitCache();


    public Bucket resolveBucket(String ip, String path) {
        ConcurrentHashMap<String, RateLimitCache.IpInfo> val ;
        if (path.equals("/auth/login") || path.equals("/trigger-google-sso")) {
            val = cache.cacheMap.get(RateLimitCache.CACHE_TYPE.SIGN_IN);
        } else if (path.startsWith("/tools/")) {
            val = cache.cacheMap.get(RateLimitCache.CACHE_TYPE.ANONYMOUS_LOGIN);
        } else if (path.equals("/auth/sendPasswordResetLink") || path.equals("/auth/resetPassword")) {
            val = cache.cacheMap.get(RateLimitCache.CACHE_TYPE.PASSWORD_RESET);
        } else {
            val = cache.cacheMap.get(RateLimitCache.CACHE_TYPE.SEND_EMAIL);
        }

        // ideally cleanup should be in separate thread, but I don't see such large number of concurrent users in near future.
        if (val.size() > 10_000) {
            try {
                cache.deleteOldData();
            } catch (Exception e) {
                e.printStackTrace();
                val.clear();
            }
        }

        if (!val.containsKey(ip)) {
            Bucket bucket = newBucket(path);
            RateLimitCache.IpInfo ipInfo = new RateLimitCache.IpInfo(bucket, Context.now());
            val.put(ip, ipInfo);
        }

        RateLimitCache.IpInfo ipInfo = val.get(ip);

        ipInfo.lastTimestamp = Context.now();

        return ipInfo.bucket;
    }

    private Bucket newBucket(String path) {
        Refill refill;
        Bandwidth limit;
        if (path.startsWith("/tools/")) {
            refill = Refill.intervally(1000, Duration.ofHours(1));
            limit = Bandwidth.classic(1000, refill);
        } else if (path.equals("/api/getCustomerStiggDetails")) {
            refill = Refill.intervally(100, Duration.ofHours(1));
            limit = Bandwidth.classic(100, refill);
        } else {
            refill = Refill.intervally(10, Duration.ofHours(1));
            limit = Bandwidth.classic(10, refill);
        }

        return Bucket.builder().addLimit(limit).build();
    }


    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest= (HttpServletRequest) request;
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        String requestURI = httpServletRequest.getRequestURI();
        String ip = httpServletRequest.getRemoteAddr();

        if (ip == null) {
            httpServletResponse.sendError(401);
            return ;
        }

        Bucket bucket = resolveBucket(ip, requestURI);

        if (!bucket.tryConsume(1)) {
            httpServletResponse.sendError(429);
            return ;
        }


        chain.doFilter(httpServletRequest, httpServletResponse);

    }

    @Override
    public void destroy() {

    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }
}
