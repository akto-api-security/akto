package com.akto.utils;

import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.EndpointMcpConfig;
import com.akto.log.LoggerMaker;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class McpCollectionResolver {

    private static final LoggerMaker logger = new LoggerMaker(McpCollectionResolver.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final McpCollectionResolver INSTANCE = new McpCollectionResolver();
    private static final long REFRESH_INTERVAL_MINUTES = 1;

    private final ConcurrentHashMap<String, String> tempToReal = new ConcurrentHashMap<>();
    private volatile int lastUpdatedTs = 0;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;

    private McpCollectionResolver() {}

    public static McpCollectionResolver getInstance() {
        return INSTANCE;
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        try {
            int added = refreshOnce();
            logger.infoAndAddToDb("McpCollectionResolver initial load: total=" + tempToReal.size() + ", added=" + added,
                LoggerMaker.LogDb.DATA_INGESTION);
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "McpCollectionResolver initial load failed: " + e.getMessage(),
                LoggerMaker.LogDb.DATA_INGESTION);
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mcp-collection-resolver-refresh");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::refreshSafely,
            REFRESH_INTERVAL_MINUTES, REFRESH_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    private void refreshSafely() {
        try {
            int added = refreshOnce();
            logger.infoAndAddToDb("McpCollectionResolver refreshed: added=" + added + ", total=" + tempToReal.size()
                + ", lastUpdatedTs=" + lastUpdatedTs, LoggerMaker.LogDb.DATA_INGESTION);
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "McpCollectionResolver refresh failed: " + e.getMessage(),
                LoggerMaker.LogDb.DATA_INGESTION);
        }
    }

    private int refreshOnce() {
        DataActor dataActor = DataActorFactory.fetchInstance();
        List<EndpointMcpConfig> configs = dataActor.fetchEndpointMcpConfigs(null, lastUpdatedTs);
        if (configs == null || configs.isEmpty()) {
            return 0;
        }
        int added = 0;
        int maxTs = lastUpdatedTs;
        for (EndpointMcpConfig cfg : configs) {
            String tempName = cfg.getTempCollectionName();
            String realName = cfg.getCollectionName();
            if (tempName == null || tempName.isEmpty() || realName == null || realName.isEmpty()) {
                continue;
            }
            tempToReal.put(tempName.toLowerCase().trim(), realName);
            added++;
            if (cfg.getUpdatedDate() > maxTs) {
                maxTs = cfg.getUpdatedDate();
            }
        }
        lastUpdatedTs = maxTs;
        return added;
    }

    public String resolve(String tempCollectionName) {
        if (tempCollectionName == null || tempCollectionName.isEmpty()) {
            return null;
        }
        return tempToReal.get(tempCollectionName.toLowerCase().trim());
    }

    public static boolean isMcpTag(String tagJson) {
        if (tagJson == null || tagJson.isEmpty()) {
            return false;
        }
        try {
            BasicDBObject tagObj = BasicDBObject.parse(tagJson);
            for (String key : tagObj.keySet()) {
                if (Constants.AKTO_MCP_SERVER_TAG.equals(key)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // malformed tag — treat as non-MCP
        }
        return false;
    }

    public static String extractHost(String headersJson) {
        if (headersJson == null || headersJson.isEmpty()) {
            return null;
        }
        try {
            BasicDBObject headersObj = BasicDBObject.parse(headersJson);
            for (String key : headersObj.keySet()) {
                if ("host".equalsIgnoreCase(key)) {
                    Object val = headersObj.get(key);
                    return val != null ? val.toString() : null;
                }
            }
        } catch (Exception e) {
            // malformed headers — caller will skip rewrite
        }
        return null;
    }

    public static String rewriteHostInHeaders(String headersJson, String newHost) {
        if (headersJson == null || headersJson.isEmpty() || newHost == null || newHost.isEmpty()) {
            return headersJson;
        }
        try {
            BasicDBObject headersObj = BasicDBObject.parse(headersJson);
            String hostKey = null;
            for (String key : headersObj.keySet()) {
                if ("host".equalsIgnoreCase(key)) {
                    hostKey = key;
                    break;
                }
            }
            if (hostKey == null) {
                return headersJson;
            }
            headersObj.put(hostKey, newHost);
            return headersObj.toJson();
        } catch (Exception e) {
            return headersJson;
        }
    }
}
