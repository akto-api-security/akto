package com.akto.action.llm;

import com.akto.dao.agents.LlmServiceConfigDao;
import com.akto.dto.agents.LlmServiceConfig;
import com.akto.dto.agents.Model;
import com.akto.dto.agents.ModelType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LlmServiceChainsTest {

    private final AtomicLong fakeClock = new AtomicLong(0);
    private Map<String, LlmServiceConfig> configs;

    @Before
    public void setUp() {
        LlmServiceChains.reset();
        configs = new HashMap<>();
        LlmServiceChains.loader = configs::get;
        LlmServiceChains.clock = fakeClock::get;
        LlmServiceChains.refresher = Runnable::run; // synchronous, so tests don't race the refresh
    }

    @After
    public void tearDown() {
        LlmServiceChains.reset();
        LlmServiceChains.loader = LlmServiceConfigDao.instance::findByService;
        LlmServiceChains.clock = System::currentTimeMillis;
        LlmServiceChains.refresher = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "llm-chain-refresh");
            t.setDaemon(true);
            return t;
        });
    }

    private static Map<String, String> mapOf(String k1, String v1, String k2, String v2) {
        Map<String, String> m = new HashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    @Test
    public void callReturnsFirstSuccessfulResult() throws Exception {
        String result = LlmServiceChains.call("svc-a", ModelTier.GPT_4O_MINI, target -> "hello");
        assertEquals("hello", result);
    }

    @Test
    public void callFallsBackWhenFirstEntryFails() throws Exception {
        Model anthropic = new Model("primary", ModelType.ANTHROPIC,
                mapOf(Model.PARAM_MODEL, "claude-haiku-5-5", Model.PARAM_API_KEY, "key"));
        configs.put("svc-b", new LlmServiceConfig("svc-b", Collections.singletonList(anthropic)));
        LlmServiceChains.chainFor("svc-b", ModelTier.GPT_4O_MINI); // force the synchronous refresh to pick up the config above

        final int[] calls = {0};
        String result = LlmServiceChains.call("svc-b", ModelTier.GPT_4O_MINI, target -> {
            calls[0]++;
            if (target.getType() == ModelType.ANTHROPIC) throw LlmCallException.provider("simulated provider failure");
            return "azure-fallback-response";
        });

        assertEquals("azure-fallback-response", result);
        assertEquals(2, calls[0]);
    }

    @Test
    public void breakerSkipsRecentlyFailedEntryExceptLast() throws Exception {
        Model anthropic = new Model("primary", ModelType.ANTHROPIC,
                mapOf(Model.PARAM_MODEL, "claude-haiku-5-5", Model.PARAM_API_KEY, "key"));
        configs.put("svc-c", new LlmServiceConfig("svc-c", Collections.singletonList(anthropic)));
        LlmServiceChains.chainFor("svc-c", ModelTier.GPT_4O_MINI);

        // first call: anthropic fails (provider-level), falls back to azure
        LlmServiceChains.call("svc-c", ModelTier.GPT_4O_MINI, target -> {
            if (target.getType() == ModelType.ANTHROPIC) throw LlmCallException.provider("down");
            return "azure-ok";
        });

        // second call within the skip window: anthropic should be skipped entirely (not invoked)
        final boolean[] anthropicInvoked = {false};
        String result = LlmServiceChains.call("svc-c", ModelTier.GPT_4O_MINI, target -> {
            if (target.getType() == ModelType.ANTHROPIC) {
                anthropicInvoked[0] = true;
                throw LlmCallException.provider("down");
            }
            return "azure-ok-again";
        });

        assertEquals("azure-ok-again", result);
        assertTrue("anthropic is not the last entry, so it should have been skipped by the breaker", !anthropicInvoked[0]);

        // advance the clock past SKIP_MS: the breaker resets, anthropic is tried again
        fakeClock.addAndGet(LlmServiceChains.SKIP_MS + 1);
        final boolean[] anthropicInvokedAgain = {false};
        LlmServiceChains.call("svc-c", ModelTier.GPT_4O_MINI, target -> {
            if (target.getType() == ModelType.ANTHROPIC) {
                anthropicInvokedAgain[0] = true;
                throw LlmCallException.provider("down");
            }
            return "azure-ok-yet-again";
        });
        assertTrue(anthropicInvokedAgain[0]);
    }

    @Test
    public void callReturnsNullWhenLastEntryHasNoUsableAnswer() throws Exception {
        String result = LlmServiceChains.call("svc-d", ModelTier.GPT_4O_MINI, target -> null);
        assertNull(result);
    }

    @Test
    public void callRethrowsLastExceptionWhenNothingSucceeds() {
        try {
            LlmServiceChains.call("svc-e", ModelTier.GPT_4O_MINI, target -> {
                throw LlmCallException.provider("always fails");
            });
            fail("expected exception to propagate");
        } catch (Exception e) {
            assertTrue(e instanceof LlmCallException);
        }
    }

    @Test
    public void chainForDefaultsToHandlerTierWhenNothingConfigured() {
        List<Model> chain = LlmServiceChains.chainFor("svc-f", ModelTier.GPT_4O_MINI);
        assertEquals(1, chain.size());
        assertEquals(ModelTier.GPT_4O_MINI, LlmServiceChains.tierOf(chain.get(0)));
    }
}
