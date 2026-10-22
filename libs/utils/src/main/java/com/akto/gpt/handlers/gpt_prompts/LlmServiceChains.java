package com.akto.gpt.handlers.gpt_prompts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.LongSupplier;

import com.akto.dao.agents.LlmServiceConfigDao;
import com.akto.dto.agents.LlmServiceConfig;
import com.akto.dto.agents.Model;
import com.akto.dto.agents.ModelType;
import com.akto.gpt.handlers.gpt_prompts.AzureOpenAIPromptHandler.ModelTier;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

/**
 * Per-service ordered model chain: config from llm_service_config (cached, refreshed off the call
 * path) and a short skip window per entry after a provider failure. The default is the handler's own env-based Azure tier: it
 * is the whole chain when nothing is configured, and the last entry after a configured chain.
 */
final class LlmServiceChains {

    interface Call {
        String run(Model target) throws Exception;
    }

    static final long CONFIG_TTL_MS = 60_000;
    static final long SKIP_MS = 30_000;

    private static final LoggerMaker logger = new LoggerMaker(LlmServiceChains.class, LogDb.DASHBOARD);

    static volatile Function<String, LlmServiceConfig> loader = LlmServiceConfigDao.instance::findByService;
    static volatile LongSupplier clock = System::currentTimeMillis;
    static volatile Executor refresher = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "llm-chain-refresh");
        t.setDaemon(true);
        return t;
    });

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Breaker> BREAKERS = new ConcurrentHashMap<>();

    private LlmServiceChains() {}

    // Returns null when the last attempt got no usable answer; rethrows when it threw, so a chain
    // with nothing configured fails exactly like a plain Azure call.
    static String call(String service, ModelTier handlerTier, Call call) throws Exception {
        logger.info("llm chain " + service + ": called");
        List<Model> chain = chainFor(service, handlerTier);
        int last = chain.size() - 1;
        Exception lastException = null;
        for (int i = 0; i <= last; i++) {
            Model target = chain.get(i);
            Breaker breaker = BREAKERS.computeIfAbsent(service + "|" + label(target), k -> new Breaker());
            // a failed entry is skipped for a while, but the last one is always tried
            if (i < last && breaker.skipUntil > clock.getAsLong()) continue;
            logger.info("llm chain " + service + ": trying " + label(target));
            lastException = null;
            String failure;
            boolean perRequest = false;
            try {
                String result = call.run(target);
                if (result != null && !result.trim().isEmpty()) {
                    breaker.skipUntil = 0;
                    return result;
                }
                // null = the call itself failed; blank = it answered with nothing (e.g. truncated)
                failure = result == null ? "no response" : "empty content";
                perRequest = result != null;
            } catch (LlmCallException e) {
                failure = e.getMessage();
                perRequest = e.perRequest;
                lastException = e;
            } catch (Exception e) {
                failure = e.toString();
                lastException = e;
            }
            if (!perRequest) breaker.skipUntil = clock.getAsLong() + SKIP_MS;
            logger.warn("llm chain " + service + ": " + label(target) + " failed (" + failure + ")"
                    + (i < last ? ", falling back" : ""));
        }
        if (lastException != null) throw lastException;
        return null;
    }

    static List<Model> chainFor(String service, ModelTier handlerTier) {
        Entry entry = CACHE.computeIfAbsent(service, s -> new Entry(defaults(handlerTier)));
        if (clock.getAsLong() - entry.loadedAt >= CONFIG_TTL_MS && entry.loading.compareAndSet(false, true)) {
            try {
                refresher.execute(() -> refresh(service, entry));
            } catch (RuntimeException e) {
                entry.loading.set(false);
            }
        }
        return entry.chain;
    }

    static List<Model> parse(String service, LlmServiceConfig config, List<Model> defaults) {
        List<Model> chain = new ArrayList<>();
        if (config != null && config.getChain() != null) {
            for (Model model : config.getChain()) {
                if (usable(service, model)) chain.add(model);
            }
        }
        if (chain.isEmpty()) return defaults;
        Set<ModelTier> listed = new HashSet<>();
        for (Model model : chain) {
            if (model.getType() == ModelType.AZURE_OPENAI) listed.add(tierOf(model));
        }
        for (Model model : defaults) {
            if (!listed.contains(tierOf(model))) chain.add(model);
        }
        return chain;
    }

    static ModelTier tierOf(Model model) {
        try {
            return ModelTier.valueOf(model.getParams().get(Model.PARAM_TIER));
        } catch (Exception e) {
            return null;
        }
    }

    static void reset() {
        CACHE.clear();
        BREAKERS.clear();
    }

    private static void refresh(String service, Entry entry) {
        try {
            entry.chain = parse(service, loader.apply(service), entry.defaults);
            entry.failureLogged = false;
        } catch (Exception e) {
            if (!entry.failureLogged) {
                logger.warn("llm chain " + service + ": config load failed, keeping previous: " + e);
                entry.failureLogged = true;
            }
        } finally {
            entry.loadedAt = clock.getAsLong();
            entry.loading.set(false);
        }
    }

    private static boolean usable(String service, Model model) {
        boolean ok = false;
        if (model != null && model.getType() != null) {
            if (model.getType() == ModelType.ANTHROPIC) {
                ok = notBlank(model.getModelName()) && notBlank(model.readApiKey());
            } else if (model.getType() == ModelType.AZURE_OPENAI) {
                ok = tierOf(model) != null;
            }
        }
        if (!ok) logger.warn("llm chain " + service + ": ignoring unusable entry " + (model == null ? null : label(model)));
        return ok;
    }

    // The env-based default every handler already has: its own tier.
    private static List<Model> defaults(ModelTier handlerTier) {
        return Collections.singletonList(azure(handlerTier));
    }

    private static Model azure(ModelTier tier) {
        return new Model(tier.name(), ModelType.AZURE_OPENAI, Collections.singletonMap(Model.PARAM_TIER, tier.name()));
    }

    static String label(Model model) {
        if (notBlank(model.getName())) return model.getName();
        return model.getType() + ":" + (notBlank(model.getModelName()) ? model.getModelName() : model.getParams());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static final class Entry {
        final List<Model> defaults;
        volatile List<Model> chain;
        volatile long loadedAt = Long.MIN_VALUE / 2;
        volatile boolean failureLogged;
        final AtomicBoolean loading = new AtomicBoolean();

        Entry(List<Model> defaults) {
            this.defaults = defaults;
            this.chain = defaults;
        }
    }

    // An entry that hit a provider failure is skipped until skipUntil.
    private static final class Breaker {
        volatile long skipUntil;
    }
}
