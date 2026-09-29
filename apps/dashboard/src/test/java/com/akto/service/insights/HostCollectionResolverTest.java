package com.akto.service.insights;

import com.akto.dto.ApiCollection;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Exact/loose/claude-config three-tier host -> collection-id join — extracted from
 * AgenticObserveAction#attributeViolationCountsToCollections (see HostCollectionResolver's own
 * javadoc), so this is a parity test against that original behavior as much as a unit test of the
 * new class.
 */
public class HostCollectionResolverTest {

    private static ApiCollection collection(int id, String hostName) {
        ApiCollection c = new ApiCollection();
        c.setId(id);
        c.setHostName(hostName);
        return c;
    }

    @Test
    public void exactHostMatch_resolvesDirectly() {
        HostCollectionResolver resolver = new HostCollectionResolver(Arrays.asList(
                collection(1, "usmbskxhjd93xcjhf-3b129dde.ai-agent.codex"),
                collection(2, "saianvithalolla.chrome.chatgpt.com")));

        assertEquals(Collections.singletonList(1), resolver.resolve("usmbskxhjd93xcjhf-3b129dde.ai-agent.codex"));
        assertEquals(Collections.singletonList(2), resolver.resolve("saianvithalolla.chrome.chatgpt.com"));
    }

    @Test
    public void unknownHost_resolvesToEmpty_neverNull() {
        HostCollectionResolver resolver = new HostCollectionResolver(Collections.singletonList(collection(1, "a.ai-agent.codex")));
        assertTrue(resolver.resolve("nobody.ai-agent.nothing").isEmpty());
        assertTrue(resolver.resolve(null).isEmpty());
        assertTrue(resolver.resolve("").isEmpty());
    }

    @Test
    public void looseMatch_fallsBackOnDeviceAndLastSegment() {
        // No exact-host collection exists for this event's host, but a collection sharing the
        // same device (first segment) + service (last segment) does — the loose fallback tier.
        HostCollectionResolver resolver = new HostCollectionResolver(
                Collections.singletonList(collection(5, "saianvithalolla.chrome.chatgpt.com")));

        // A 2-segment host with the same device+last-segment key ("saianvithalolla chatgpt.com")
        // still resolves via deviceServiceKey, even though the raw string differs.
        assertEquals(Collections.singletonList(5), resolver.resolve("saianvithalolla.chatgpt.com"));
    }

    @Test
    public void claudeConfigHost_fallsBackToDeviceThenAnyClaudeCollection() {
        HostCollectionResolver resolver = new HostCollectionResolver(Arrays.asList(
                collection(10, "deviceA.claude"),
                collection(11, "deviceB.claude")));

        // Exact device match on the claude-config fallback pool.
        assertEquals(Collections.singletonList(10), resolver.resolve("deviceA.claude-settings"));

        // No collection for this device -> falls back to *some* claude collection rather than nothing.
        List<Integer> fallback = resolver.resolve("unknownDevice.claude-settings");
        assertEquals(1, fallback.size());
        assertTrue(fallback.get(0) == 10 || fallback.get(0) == 11);
    }

    @Test
    public void nonClaudeTwoSegmentHost_withNoLooseMatch_resolvesEmpty() {
        HostCollectionResolver resolver = new HostCollectionResolver(Collections.singletonList(collection(1, "a.ai-agent.codex")));
        assertTrue(resolver.resolve("device.somethingelse").isEmpty());
    }

    @Test
    public void deviceServiceKey_and_isClaudeConfigHost_matchOriginalActionExactly() {
        assertEquals("device com", HostCollectionResolver.deviceServiceKey("device.chrome.chatgpt.com"));
        assertEquals(null, HostCollectionResolver.deviceServiceKey("onesegment"));
        assertTrue(HostCollectionResolver.isClaudeConfigHost("device.claude"));
        assertTrue(HostCollectionResolver.isClaudeConfigHost("device.claude-settings"));
        assertTrue(!HostCollectionResolver.isClaudeConfigHost("device.chrome.claude"));
    }
}
