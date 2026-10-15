package com.akto.gpt.handlers.gpt_prompts;

import com.akto.dto.agents.LlmServiceConfig;
import com.akto.dto.agents.Model;
import com.akto.dto.agents.ModelType;
import com.akto.gpt.handlers.gpt_prompts.AzureOpenAIPromptHandler.ModelTier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class LlmServiceChainsTest {

    private static final String SVC = "insights-overview";
    private static final ModelTier NANO = ModelTier.GPT_5_NANO;

    private final AtomicLong now = new AtomicLong(1_000_000);
    private Function<String, LlmServiceConfig> originalLoader;

    @BeforeEach
    void setUp() throws Exception {
        originalLoader = LlmServiceChains.loader;
        LlmServiceChains.reset();
        LlmServiceChains.clock = now::get;
        LlmServiceChains.refresher = Runnable::run;
        LlmServiceChains.loader = s -> null;
    }

    @AfterEach
    void tearDown() throws Exception {
        LlmServiceChains.loader = originalLoader;
        LlmServiceChains.clock = System::currentTimeMillis;
        LlmServiceChains.reset();
    }

    private static Model anthropic(String name, String model, String key) {
        Map<String, String> p = new HashMap<>();
        if (model != null) p.put(Model.PARAM_MODEL, model);
        if (key != null) p.put(Model.PARAM_API_KEY, key);
        return new Model(name, ModelType.ANTHROPIC, p);
    }

    private static Model azure(String name, String tier) {
        Map<String, String> p = new HashMap<>();
        if (tier != null) p.put(Model.PARAM_TIER, tier);
        return new Model(name, ModelType.AZURE_OPENAI, p);
    }

    private static List<String> labels(List<Model> chain) {
        List<String> out = new ArrayList<>();
        for (Model m : chain) out.add(LlmServiceChains.label(m));
        return out;
    }

    private void configure(String service, Model... chain) {
        LlmServiceChains.loader = s -> s.equals(service) ? new LlmServiceConfig(service, Arrays.asList(chain)) : null;
    }

    // --- config and defaults ---

    @Test
    void noConfigUsesCodeDefaults() throws Exception {
        assertEquals(Collections.singletonList("GPT_5_NANO"), labels(LlmServiceChains.chainFor(SVC, NANO)));
    }

    @Test
    void configuredChainReplacesDefaultsInOrder() throws Exception {
        configure(SVC, anthropic("haiku", "claude-haiku-5-5", "k"), azure("nano", "GPT_5_NANO"), azure("mini", "GPT_4O_MINI"));
        assertEquals(Arrays.asList("haiku", "nano", "mini"), labels(LlmServiceChains.chainFor(SVC, NANO)));
    }

    @Test
    void unusableEntriesAreDroppedNotFatal() throws Exception {
        configure(SVC,
            anthropic("no-key", "claude-haiku-5-5", null),
            anthropic("no-model", null, "k"),
            azure("bad-tier", "GPT_9_MEGA"),
            azure("no-tier", null),
            new Model("openai", ModelType.OPENAI, new HashMap<>()),
            new Model("no-type", null, new HashMap<>()),
            null,
            azure("mini", "GPT_4O_MINI"));
        assertEquals(Arrays.asList("mini", "GPT_5_NANO"), labels(LlmServiceChains.chainFor(SVC, NANO)));
    }

    @Test
    void allEntriesUnusableFallsBackToDefaults() throws Exception {
        configure(SVC, anthropic("no-key", "claude-haiku-5-5", null));
        assertEquals(Collections.singletonList("GPT_5_NANO"), labels(LlmServiceChains.chainFor(SVC, NANO)));
    }

    @Test
    void loaderFailureKeepsDefaultsAndDoesNotThrow() throws Exception {
        LlmServiceChains.loader = s -> { throw new IllegalStateException("mongo down"); };
        assertEquals(Collections.singletonList("GPT_5_NANO"), labels(LlmServiceChains.chainFor(SVC, NANO)));
    }

    @Test
    void loaderFailureAfterGoodConfigKeepsThePreviousChain() throws Exception {
        configure(SVC, anthropic("haiku", "claude-haiku-5-5", "k"));
        assertEquals(Arrays.asList("haiku", "GPT_5_NANO"), labels(LlmServiceChains.chainFor(SVC, NANO)));
        LlmServiceChains.loader = s -> { throw new IllegalStateException("mongo down"); };
        now.addAndGet(LlmServiceChains.CONFIG_TTL_MS + 1);
        assertEquals(Arrays.asList("haiku", "GPT_5_NANO"), labels(LlmServiceChains.chainFor(SVC, NANO)));
    }

    @Test
    void configIsCachedForTheTtlThenReloaded() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        LlmServiceChains.loader = s -> { loads.incrementAndGet(); return null; };
        LlmServiceChains.chainFor(SVC, NANO);
        LlmServiceChains.chainFor(SVC, NANO);
        assertEquals(1, loads.get());
        now.addAndGet(LlmServiceChains.CONFIG_TTL_MS - 1);
        LlmServiceChains.chainFor(SVC, NANO);
        assertEquals(1, loads.get());
        now.addAndGet(2);
        LlmServiceChains.chainFor(SVC, NANO);
        assertEquals(2, loads.get());
    }

    @Test
    void deletedConfigRevertsToDefaultsAfterTtl() throws Exception {
        configure(SVC, anthropic("haiku", "claude-haiku-5-5", "k"));
        LlmServiceChains.chainFor(SVC, NANO);
        LlmServiceChains.loader = s -> null;
        now.addAndGet(LlmServiceChains.CONFIG_TTL_MS + 1);
        assertEquals(Collections.singletonList("GPT_5_NANO"), labels(LlmServiceChains.chainFor(SVC, NANO)));
    }

    @Test
    void firstCallNeverBlocksOnTheLoader() throws Exception {
        List<Runnable> queued = new ArrayList<>();
        LlmServiceChains.refresher = queued::add;
        AtomicInteger loads = new AtomicInteger();
        configure(SVC, anthropic("haiku", "claude-haiku-5-5", "k"));
        Function<String, LlmServiceConfig> inner = LlmServiceChains.loader;
        LlmServiceChains.loader = s -> { loads.incrementAndGet(); return inner.apply(s); };

        assertEquals(Collections.singletonList("GPT_5_NANO"), labels(LlmServiceChains.chainFor(SVC, NANO)));
        assertEquals(0, loads.get());
        assertEquals(1, queued.size());
        LlmServiceChains.chainFor(SVC, NANO); // refresh still in flight: no second one queued
        assertEquals(1, queued.size());

        queued.get(0).run();
        assertEquals(Arrays.asList("haiku", "GPT_5_NANO"), labels(LlmServiceChains.chainFor(SVC, NANO)));
    }

    // --- fallback ---

    @Test
    void firstSuccessWinsAndLaterTargetsAreNotCalled() throws Exception {
        configure(SVC, anthropic("a", "m", "k"), azure("b", "GPT_5_NANO"), azure("c", "GPT_4O_MINI"));
        List<String> called = new ArrayList<>();
        String result = LlmServiceChains.call(SVC, NANO, t -> { called.add(t.getName()); return "ok-" + t.getName(); });
        assertEquals("ok-a", result);
        assertEquals(Collections.singletonList("a"), called);
    }

    @Test
    void failureOrEmptyFallsThroughInOrder() throws Exception {
        configure(SVC, anthropic("a", "m", "k"), azure("b", "GPT_5_NANO"), azure("c", "GPT_4O_MINI"));
        List<String> called = new ArrayList<>();
        String result = LlmServiceChains.call(SVC, NANO, t -> {
            called.add(t.getName());
            if (t.getName().equals("a")) throw LlmCallException.provider("HTTP 529");
            if (t.getName().equals("b")) return "  ";
            return "from-c";
        });
        assertEquals("from-c", result);
        assertEquals(Arrays.asList("a", "b", "c"), called);
    }

    @Test
    void nullResultAndPlainExceptionsAlsoFallThrough() throws Exception {
        configure(SVC, azure("a", "GPT_5_NANO"), azure("b", "GPT_4O_MINI"), anthropic("c", "m", "k"));
        String result = LlmServiceChains.call(SVC, NANO, t -> {
            if (t.getName().equals("a")) return null;
            if (t.getName().equals("b")) throw new java.io.IOException("timeout");
            return "from-c";
        });
        assertEquals("from-c", result);
    }

    @Test
    void everyTargetThrowingRethrowsTheLastExceptionLikeAPlainCall() {
        configure(SVC, anthropic("a", "m", "k"), azure("b", "GPT_5_NANO"));
        LlmCallException thrown = assertThrows(LlmCallException.class, () -> LlmServiceChains.call(SVC, NANO, t -> {
            throw LlmCallException.provider("down-" + t.getName());
        }));
        assertEquals("down-b", thrown.getMessage()); // the last attempted entry wins
    }

    @Test
    void everyTargetReturningNothingYieldsNull() throws Exception {
        assertNull(LlmServiceChains.call(SVC, NANO, t -> null));
        assertNull(LlmServiceChains.call(SVC, NANO, t -> "   "));
    }

    @Test
    void anEarlierExceptionIsForgottenWhenTheLastAttemptJustReturnsNothing() throws Exception {
        configure(SVC, anthropic("a", "m", "k"));
        String result = LlmServiceChains.call(SVC, NANO, t -> {
            if (t.getName().equals("a")) throw LlmCallException.provider("HTTP 529");
            return null;
        });
        assertNull(result);
    }

    @Test
    void aServiceWithNoDefaultOverrideUsesJustTheHandlersOwnTier() {
        assertEquals(Collections.singletonList("GPT_4O_MINI"), labels(LlmServiceChains.chainFor("some-service", ModelTier.GPT_4O_MINI)));
        assertEquals(Collections.singletonList("GPT_5_NANO"), labels(LlmServiceChains.chainFor("other-service", ModelTier.GPT_5_NANO)));
    }

    // --- azure fallback is never configured away ---

    @Test
    void aConfiguredChainAlwaysEndsWithTheHandlersOwnEnvBasedTier() {
        configure(SVC, anthropic("haiku", "claude-haiku-5-5", "k"));
        assertEquals(Arrays.asList("haiku", "GPT_5_NANO"), labels(LlmServiceChains.chainFor(SVC, NANO)));
    }

    @Test
    void listedAzureTiersKeepTheOperatorsOrderAndAreNotDuplicated() {
        configure(SVC, anthropic("haiku", "claude-haiku-5-5", "k"), azure("mini", "GPT_4O_MINI"));
        assertEquals(Arrays.asList("haiku", "mini", "GPT_5_NANO"), labels(LlmServiceChains.chainFor(SVC, NANO)));
        LlmServiceChains.reset();
        configure(SVC, anthropic("haiku", "claude-haiku-5-5", "k"), azure("nano", "GPT_5_NANO"), azure("mini", "GPT_4O_MINI"));
        assertEquals(Arrays.asList("haiku", "nano", "mini"), labels(LlmServiceChains.chainFor(SVC, NANO)));
    }

    @Test
    void forAnOrdinaryServiceTheAppendedSafetyNetIsTheHandlersOwnTier() {
        configure("tool-capability", anthropic("haiku", "claude-haiku-5-5", "k"));
        assertEquals(Arrays.asList("haiku", "GPT_5_NANO"), labels(LlmServiceChains.chainFor("tool-capability", NANO)));
        LlmServiceChains.reset();
        configure("mcp-tool-maliciousness", anthropic("haiku", "claude-haiku-5-5", "k"));
        assertEquals(Arrays.asList("haiku", "GPT_4O_MINI"), labels(LlmServiceChains.chainFor("mcp-tool-maliciousness", ModelTier.GPT_4O_MINI)));
    }

    @Test
    void anAnthropicEntryWithoutAKeyIsDroppedAndAzureServesInstead() {
        configure(SVC, anthropic("haiku", "claude-haiku-5-5", null));
        assertEquals(Collections.singletonList("GPT_5_NANO"), labels(LlmServiceChains.chainFor(SVC, NANO)));
        LlmServiceChains.reset();
        configure(SVC, anthropic("haiku", "claude-haiku-5-5", " "), azure("mini", "GPT_4O_MINI"));
        assertEquals(Arrays.asList("mini", "GPT_5_NANO"), labels(LlmServiceChains.chainFor(SVC, NANO)));
    }

    // --- a provider failure skips the entry for a while ---

    private int callsTo(String name, int attempts) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < attempts; i++) {
            LlmServiceChains.call(SVC, NANO, t -> {
                if (t.getName().equals(name)) { calls.incrementAndGet(); throw LlmCallException.provider("HTTP 529"); }
                return "ok-" + t.getName();
            });
        }
        return calls.get();
    }

    @Test
    void aProviderFailureSkipsTheEntryForTheWindow() throws Exception {
        configure(SVC, anthropic("a", "m", "k"), azure("b", "GPT_5_NANO"));
        assertEquals(1, callsTo("a", 10));
    }

    @Test
    void theEntryIsTriedAgainOnlyAfterTheWindowAndFailingAgainSkipsItAgain() throws Exception {
        configure(SVC, anthropic("a", "m", "k"), azure("b", "GPT_5_NANO"));
        assertEquals(1, callsTo("a", 1));
        now.addAndGet(LlmServiceChains.SKIP_MS - 1);
        assertEquals(0, callsTo("a", 3));
        now.addAndGet(2);
        assertEquals(1, callsTo("a", 5)); // one retry, fails, skipped again
    }

    @Test
    void aProviderThatRecoversGetsTrafficBackAfterTheWindow() throws Exception {
        configure(SVC, anthropic("a", "m", "k"), azure("b", "GPT_5_NANO"));
        callsTo("a", 1);
        assertEquals("ok-b", LlmServiceChains.call(SVC, NANO, t -> "ok-" + t.getName()));
        now.addAndGet(LlmServiceChains.SKIP_MS + 1);
        assertEquals("ok-a", LlmServiceChains.call(SVC, NANO, t -> "ok-" + t.getName()));
    }

    @Test
    void perRequestFailuresFallBackButNeverSkipTheEntry() throws Exception {
        configure(SVC, anthropic("a", "m", "k"), azure("b", "GPT_5_NANO"));
        AtomicInteger aCalls = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            String result = LlmServiceChains.call(SVC, NANO, t -> {
                if (t.getName().equals("a")) { aCalls.incrementAndGet(); throw LlmCallException.request("stop_reason refusal"); }
                return "from-b";
            });
            assertEquals("from-b", result);
        }
        assertEquals(10, aCalls.get()); // 'a' is still tried every time
    }

    @Test
    void blankAnswerIsPerRequestButNoAnswerAtAllIsAProviderFailure() throws Exception {
        configure(SVC, anthropic("a", "m", "k"), azure("b", "GPT_5_NANO"));
        AtomicInteger blank = new AtomicInteger();
        for (int i = 0; i < 6; i++) {
            LlmServiceChains.call(SVC, NANO, t -> { if (t.getName().equals("a")) { blank.incrementAndGet(); return ""; } return "ok"; });
        }
        assertEquals(6, blank.get());

        LlmServiceChains.reset();
        AtomicInteger none = new AtomicInteger();
        for (int i = 0; i < 6; i++) {
            LlmServiceChains.call(SVC, NANO, t -> { if (t.getName().equals("a")) { none.incrementAndGet(); return null; } return "ok"; });
        }
        assertEquals(1, none.get());
    }

    @Test
    void plainExceptionsAreProviderFailures() throws Exception {
        configure(SVC, anthropic("a", "m", "k"), azure("b", "GPT_5_NANO"));
        AtomicInteger aCalls = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            LlmServiceChains.call(SVC, NANO, t -> {
                if (t.getName().equals("a")) { aCalls.incrementAndGet(); throw new java.io.IOException("timeout"); }
                return "ok";
            });
        }
        assertEquals(1, aCalls.get());
    }

    @Test
    void theLastEntryIsNeverSkippedEvenAfterItFails() {
        configure(SVC, anthropic("a", "m", "k"), azure("b", "GPT_5_NANO"));
        AtomicInteger bCalls = new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            assertThrows(LlmCallException.class, () -> LlmServiceChains.call(SVC, NANO, t -> {
                if (t.getName().equals("b")) bCalls.incrementAndGet();
                throw LlmCallException.provider("down");
            }));
        }
        assertEquals(5, bCalls.get());
    }

    @Test
    void skipWindowsAreIsolatedPerService() throws Exception {
        Model shared = anthropic("a", "m", "k");
        LlmServiceChains.loader = s -> new LlmServiceConfig(s, Arrays.asList(shared, azure("b", "GPT_5_NANO")));
        LlmServiceChains.call("svc-one", NANO, t -> { if (t.getName().equals("a")) throw LlmCallException.provider("x"); return "ok"; });
        AtomicInteger tried = new AtomicInteger();
        LlmServiceChains.call("svc-two", NANO, t -> { if (t.getName().equals("a")) tried.incrementAndGet(); return "ok"; });
        assertEquals(1, tried.get());
    }
}
