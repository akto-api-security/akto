package com.akto.dao;

import com.akto.DaoInit;
import com.akto.dao.metrics.MetricDataDao;
import com.akto.dao.monitoring.EndpointShieldLogsDao;
import com.akto.dao.traffic_metrics.TrafficMetricsDao;
import com.akto.util.DbNames;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoTimeoutException;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.connection.ClusterDescription;
import com.mongodb.connection.ServerDescription;
import com.mongodb.event.ClusterClosedEvent;
import com.mongodb.event.ClusterDescriptionChangedEvent;
import com.mongodb.event.ClusterListener;
import com.mongodb.event.ClusterOpeningEvent;
import com.akto.util.DbMode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional separate MongoDB for logs and traffic metrics, selected by env LOG_MONGO_CONN (a Mongo
 * connection string). Unset/blank => disabled and every DAO behaves exactly as before (clients[0]).
 *
 * When set, ONLY the DAOs in ROUTED (exact classes, not subclasses) resolve their collection from the logs
 * Mongo: all log DAOs except AwsApiGatewayLogsDao and GuardrailsServiceLogsDao, plus TrafficMetricsDao.
 * Database-per-account naming and collection names are unchanged. The connection string is never logged.
 */
public class LogsMongo {
    private static final Logger logger = LoggerFactory.getLogger(LogsMongo.class);

    public static final String ENV = "LOG_MONGO_CONN";
    private static final String CONN = resolve();
    private static final boolean ENABLED = CONN != null;

    // exact classes only (no subclass matching). Deliberately NOT routed: AwsApiGatewayLogsDao, GuardrailsServiceLogsDao (nothing writes them any more)
    private static final Set<Class<?>> ROUTED = new HashSet<>(Arrays.asList(
            LogsDao.class, RuntimeLogsDao.class, ProtectionLogsDao.class, EndpointShieldLogsDao.class, TrafficMetricsDao.class,
            DashboardLogsDao.class, BillingLogsDao.class, DataIngestionLogsDao.class, AnalyserLogsDao.class,
            AgenticTestingLogsDao.class, PupeteerLogsDao.class, SmartTestingLogsDao.class, DastLogsDao.class,
            MiniTestingOrchestratorLogsDao.class,
            MetricDataDao.class));

    private static volatile MongoClient client;

    /**
     * False once the cluster monitor has seen NO usable server (logs Mongo down). While false, routed DAOs fail
     * immediately instead of each call waiting for server selection, so a logs-Mongo outage cannot stall request
     * threads (e.g. LoggerMaker writing dashboard logs). Starts true; flips back by itself when the server returns.
     */
    private static volatile ClusterDescription lastDescription = null;
    private static volatile long clientCreatedAt = 0L;
    private static final long CONNECT_GRACE_MS = 3000L;

    /** Healthy = still connecting (grace period), or the latest cluster view has at least one usable server. */
    static boolean isHealthy() {
        ClusterDescription d = lastDescription;
        if (d == null) return true;
        if (System.currentTimeMillis() - clientCreatedAt < CONNECT_GRACE_MS) return true;
        for (ServerDescription sd : d.getServerDescriptions()) {
            if (sd.isOk()) return true;
        }
        return false;
    }

    // Dashboard/Billing logs are written by the dashboard process itself, so nobody else creates them in the logs Mongo:
    // create them CAPPED (same caps as before) on first use, so they cannot grow without limit.
    private static final Map<Class<?>, int[]> CAPS = new HashMap<>();
    static {
        CAPS.put(DashboardLogsDao.class, new int[]{DashboardLogsDao.maxDocuments, DashboardLogsDao.sizeInBytes});
        CAPS.put(BillingLogsDao.class, new int[]{BillingLogsDao.maxDocuments, BillingLogsDao.sizeInBytes});
    }
    private static final Set<String> ensured = ConcurrentHashMap.newKeySet();

    private LogsMongo() {}

    private static String resolve() {
        String v = System.getenv(ENV);
        return (v == null || v.trim().isEmpty()) ? null : v.trim();
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    /** True when this DAO instance must use the logs Mongo (feature on AND exact class is one of the routed DAOs). */
    public static boolean isRouted(MCollection<?> dao) {
        if (!ENABLED) return false;
        Class<?> c = dao.getClass();
        return ROUTED.contains(c);
    }

    public static <T> MongoCollection<T> getMCollection(String dbName, String collName, Class<T> classT) {
        return getClient().getDatabase(dbName).getCollection(collName, classT);
    }

    /** Entry point used by MCollection for routed DAOs: fail fast when the logs Mongo is down, ensure capped collections. */
    public static <T> MongoCollection<T> getMCollection(MCollection<T> dao) {
        if (!isHealthy()) {
            throw new IllegalStateException("Logs Mongo unavailable");
        }
        String db = dao.getDBName();
        String coll = dao.getCollName();
        int[] caps = CAPS.get(dao.getClass());
        if (caps != null) {
            ensureCapped(db, coll, caps[0], caps[1]);
        }
        return getMCollection(db, coll, dao.getClassT());
    }

    private static void ensureCapped(String dbName, String collName, int maxDocs, int sizeBytes) {
        String key = dbName + "." + collName;
        if (ensured.contains(key)) {
            return;
        }
        try {
            MongoDatabase d = getClient().getDatabase(dbName);
            boolean exists = false;
            for (String c : d.listCollectionNames()) {
                if (collName.equalsIgnoreCase(c)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                if (DbMode.allowCappedCollections()) {
                    d.createCollection(collName, new CreateCollectionOptions().capped(true).maxDocuments(maxDocs).sizeInBytes(sizeBytes));
                } else {
                    d.createCollection(collName);
                }
            }
            d.getCollection(collName).createIndex(Indexes.ascending("timestamp"));
            ensured.add(key);
        } catch (MongoTimeoutException e) {
            // logs Mongo unreachable: do not wait a second time for the write itself, fail this call now
            throw e;
        } catch (Exception e) {
            // not fatal: retried on the next call; worst case the first write auto-creates an uncapped collection
            logger.warn("Could not ensure capped collection {}: {}", key, e.getClass().getSimpleName());
        }
    }

    private static MongoClient getClient() {
        MongoClient c = client;
        if (c != null) return c;
        synchronized (LogsMongo.class) {
            if (client == null) {
                try {
                    ConnectionString cs = new ConnectionString(CONN);
                    MongoClientSettings.Builder b = MongoClientSettings.builder()
                            .readPreference(ReadPreference.primaryPreferred())
                            .writeConcern(WriteConcern.ACKNOWLEDGED)
                            .applyConnectionString(cs)
                            .codecRegistry(DaoInit.createCodecRegistry());
                    b.applyToClusterSettings(s -> s.addClusterListener(new ClusterListener() {
                        @Override
                        public void clusterOpening(ClusterOpeningEvent event) {
                        }

                        @Override
                        public void clusterClosed(ClusterClosedEvent event) {
                        }

                        @Override
                        public void clusterDescriptionChanged(ClusterDescriptionChangedEvent event) {
                            boolean before = isHealthy();
                            lastDescription = event.getNewDescription();
                            boolean after = isHealthy();
                            if (after != before) {
                                logger.warn("Logs Mongo is now {}", after ? "AVAILABLE" : "UNAVAILABLE (log and metric reads/writes fail fast)");
                            }
                        }
                    }));
                    if (cs.getServerSelectionTimeout() == null) {
                        // fail fast (UI request) when the logs Mongo is down, instead of the 30s driver default
                        b.applyToClusterSettings(s -> s.serverSelectionTimeout(5, TimeUnit.SECONDS));
                    }
                    clientCreatedAt = System.currentTimeMillis();
                    client = DbNames.wrap(MongoClients.create(b.build()));
                    logger.info("Logs Mongo client created ({} is set)", ENV);
                } catch (RuntimeException e) {
                    // do not include e.getMessage(): it can echo the connection string
                    logger.error("Could not create logs Mongo client from {}: {}", ENV, e.getClass().getSimpleName());
                    throw new IllegalStateException("Invalid " + ENV);
                }
            }
            return client;
        }
    }
}
