package com.akto.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import io.github.bucket4j.Bucket;

public class RateLimitFilterTest {

    @Test
    public void oauthPathsShareAGenerousPerIpBucket() {
        RateLimitFilter filter = new RateLimitFilter();
        Bucket register = filter.resolveBucket("203.0.113.7", "/oauth/register");
        assertEquals(RateLimitFilter.MCP_OAUTH_REQUESTS_PER_HOUR, register.getAvailableTokens());
        assertSame(register, filter.resolveBucket("203.0.113.7", "/oauth/authorize"));
    }

    @Test
    public void oauthBucketIsSeparateFromTheLoginAndEmailBuckets() {
        RateLimitFilter filter = new RateLimitFilter();
        filter.resolveBucket("203.0.113.8", "/oauth/register").tryConsume(1);
        assertEquals(10, filter.resolveBucket("203.0.113.8", "/api/inviteUsers").getAvailableTokens());
        assertEquals(10, filter.resolveBucket("203.0.113.8", "/auth/login").getAvailableTokens());
    }

    @Test
    public void oauthBucketRefusesAfterTheLimit() {
        RateLimitFilter filter = new RateLimitFilter();
        Bucket bucket = filter.resolveBucket("203.0.113.9", "/oauth/register");
        assertEquals(RateLimitFilter.MCP_OAUTH_REQUESTS_PER_HOUR, bucket.tryConsumeAsMuchAsPossible(RateLimitFilter.MCP_OAUTH_REQUESTS_PER_HOUR));
        assertFalse(bucket.tryConsume(1));
    }
}
