package com.akto.utils;

import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.monitoring.ModuleInfo.ModuleType;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrafficCollectorAlert {

    private static final LoggerMaker loggerMaker =
            new LoggerMaker(TrafficCollectorAlert.class, LogDb.DB_ABS);
    private static final ExecutorService executorService = Executors.newFixedThreadPool(2);

    private static final List<Integer> MONITORED_ACCOUNT_IDS = Arrays.asList(1772780065, 1000000, 1726615470);
    private static final int CHECK_INTERVAL_SECONDS = 5 * 60;
    private static final float SPIKE_THRESHOLD = 0.30f;

    private static final ConcurrentHashMap<Integer, Integer> lastCheckTs = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, List<Float>> metricsHistory = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> activeAlert = new ConcurrentHashMap<>();

    public static void checkOnHeartbeat(List<ModuleInfo> moduleInfoList) {
        int accountId = Context.accountId.get();
        if (!MONITORED_ACCOUNT_IDS.contains(accountId)) {
            return;
        }
        List<ModuleInfo> snapshot = moduleInfoList == null ? null : new ArrayList<>(moduleInfoList);
        try {
            executorService.submit(() -> {
                Context.accountId.set(accountId);
                try {
                    runChecks(accountId, snapshot);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e,
                            "Error checking traffic collector alerts for account " + accountId);
                }
            });
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,
                    "Error submitting traffic collector alert check for account " + accountId);
        }
    }

    private static void runChecks(int accountId, List<ModuleInfo> moduleInfoList) {
        int now = Context.now();
        Integer lastCheck = lastCheckTs.get(accountId);
        if (lastCheck == null || (now - lastCheck) >= CHECK_INTERVAL_SECONDS) {
            lastCheckTs.put(accountId, now);
            checkStaleHeartbeats(accountId, now);
        }
        if (moduleInfoList != null) {
            checkMetricsSpikes(accountId, moduleInfoList);
        }
    }

    private static void checkStaleHeartbeats(int accountId, int now) {
        int staleCutoff = now - CHECK_INTERVAL_SECONDS;
        List<ModuleInfo> modules = ModuleInfoDao.instance.findAll(
                Filters.eq(ModuleInfo.MODULE_TYPE, ModuleType.TRAFFIC_COLLECTOR));
        if (modules == null) {
            return;
        }
        for (ModuleInfo module : modules) {
            String instance = module.getName() == null ? "unknown" : module.getName();
            String key = accountId + ":hb:" + instance;
            if (module.getLastHeartbeatReceived() >= staleCutoff) {
                activeAlert.remove(key);
                continue;
            }
            if (Boolean.TRUE.equals(activeAlert.get(key))) {
                continue;
            }
            String message = String.format(
                    "Traffic collector heartbeat stale for account %d, instance %s: last heartbeat %d minutes ago",
                    accountId, instance, (now - module.getLastHeartbeatReceived()) / 60);
            loggerMaker.infoAndAddToDb(message);
            RedactAlert.sendToCyborgSlack(message);
            activeAlert.put(key, true);
        }
    }

    private static void checkMetricsSpikes(int accountId, List<ModuleInfo> moduleInfoList) {
        for (ModuleInfo module : moduleInfoList) {
            if (module == null || module.getModuleType() != ModuleType.TRAFFIC_COLLECTOR) {
                continue;
            }
            String instance = module.getName();
            if (instance == null || instance.isEmpty()) {
                continue;
            }
            Map<String, Object> additionalData = module.getAdditionalData();
            if (additionalData == null) {
                continue;
            }
            Object profilingObj = additionalData.get("profiling");
            if (!(profilingObj instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> profiling = (Map<String, Object>) profilingObj;
            if (profiling.containsKey("system_cpu_percent")) {
                checkSpike(accountId, instance, "CPU", toFloat(profiling.get("system_cpu_percent")));
            }
            if (profiling.containsKey("host_memory_used_mb")) {
                checkSpike(accountId, instance, "memory", toFloat(profiling.get("host_memory_used_mb")));
            }
        }
    }

    private static void checkSpike(int accountId, String instance, String label, float current) {
        String key = accountId + ":metric:" + instance + ":" + label;
        List<Float> history = metricsHistory.computeIfAbsent(key, k -> new ArrayList<>());
        history.add(0, current);
        if (history.size() > 6) {
            history.remove(history.size() - 1);
        }
        if (history.size() < 2 || !isSpike(history)) {
            activeAlert.remove(key);
            return;
        }
        if (Boolean.TRUE.equals(activeAlert.get(key))) {
            return;
        }
        float previous = history.get(1);
        String message = String.format(
                "Traffic collector %s spike for account %d, instance %s: current=%.2f, previous=%.2f",
                label, accountId, instance, current, previous);
        loggerMaker.infoAndAddToDb(message);
        RedactAlert.sendToCyborgSlack(message);
        activeAlert.put(key, true);
    }

    private static boolean isSpike(List<Float> history) {
        float current = history.get(0);
        float previous = history.get(1);
        if (pctChange(current, previous) >= SPIKE_THRESHOLD) {
            return true;
        }
        int avgCount = Math.min(5, history.size() - 1);
        float sum = 0;
        for (int i = 1; i <= avgCount; i++) {
            sum += history.get(i);
        }
        return pctChange(current, sum / avgCount) >= SPIKE_THRESHOLD;
    }

    private static float pctChange(float current, float baseline) {
        return Math.abs(current - baseline) / Math.max(Math.abs(baseline), 1f);
    }

    private static float toFloat(Object value) {
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return 0f;
    }
}
