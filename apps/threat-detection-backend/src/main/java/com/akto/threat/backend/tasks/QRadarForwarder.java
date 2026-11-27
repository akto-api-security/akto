package com.akto.threat.backend.tasks;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

import com.akto.dao.context.Context;
import com.akto.dto.threat_detection_backend.MaliciousEventDto;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.threat.backend.constants.MongoDBCollection;
import com.akto.threat.backend.dao.MaliciousEventDao;
import com.akto.util.DbNames;
import com.akto.util.http_util.CoreHTTPClient;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.ReadPreference;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWriter;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.codecs.EncoderContext;
import org.bson.conversions.Bson;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;

/**
 * Forwards malicious events to an IBM QRadar HTTP Receiver log source, as {"events":[...]} batches.
 *
 * Events are queued in the per-account siem_outbox collection before they are stored, and leave it only after
 * QRadar answers 2xx, so a QRadar outage or a pod restart never loses one. A batch keeps the same events across
 * retries (pinned by batchId), and one pod sends at a time (siem_forwarder_lock). A batch is resent only if we
 * crash or lose QRadar's reply after it accepted the batch; every event carries its _id so repeats are identifiable.
 */
public class QRadarForwarder {

  private static final LoggerMaker logger = new LoggerMaker(QRadarForwarder.class, LogDb.THREAT_DETECTION);

  private static final String DESTINATION = "qradar";
  private static final String PENDING = "PENDING", SENDING = "SENDING", FAILED = "FAILED";
  private static final int BATCH_SIZE = 200;
  // a full batch stays under 1 MB: 200 x 4900 bytes plus the {"events":[...]} wrapper
  private static final int MAX_EVENT_BYTES = 4900;
  private static final String TRUNCATED = "...[truncated]";
  private static final int MAX_ATTEMPTS = 3; // only for batches QRadar rejects; outages never use up attempts
  private static final int MAX_BATCHES_PER_TICK = 25;
  private static final int TICK_SECONDS = 5;
  private static final int LOCK_SECONDS = 60; // well above one send (callTimeout 15s), renewed before every batch
  private static final int PAUSE_SECONDS = 300;
  private static final int CONFIG_TTL_SECONDS = 60;

  private static final MediaType JSON = MediaType.parse("application/json");
  private static final JsonWriterSettings RELAXED = JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build();
  private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder()
      .connectTimeout(5, TimeUnit.SECONDS)
      .readTimeout(10, TimeUnit.SECONDS)
      .callTimeout(15, TimeUnit.SECONDS)
      .build();
  private static final String OWNER = System.getenv().getOrDefault("HOSTNAME", "threat-backend") + ":" + UUID.randomUUID();

  private static MongoClient mongoClient;
  private static volatile Map<String, Document> enabledConfigs = Collections.emptyMap();
  private static volatile int configsFetchedAt = 0;
  private static final Set<String> indexedAccounts = ConcurrentHashMap.newKeySet();
  private static final Map<String, Integer> pausedUntil = new ConcurrentHashMap<>();

  public static void start(MongoClient client) {
    mongoClient = client;
    Executors.newSingleThreadScheduledExecutor()
        .scheduleWithFixedDelay(QRadarForwarder::tick, TICK_SECONDS, TICK_SECONDS, TimeUnit.SECONDS);
  }

  /** Called before the event is stored. Never throws: a QRadar problem must not stop Akto storing the event. */
  public static void enqueue(String accountId, MaliciousEventDto event) {
    if (mongoClient == null) return;
    try {
      if (!enabledConfigs().containsKey(accountId)) return;
      if (event.getId() == null) event.setId(UUID.randomUUID().toString()); // outbox key; same as the builder assigns
      ensureIndexes(accountId);
      outbox(accountId).insertOne(new Document("_id", event.getId() + ":" + DESTINATION)
          .append("destination", DESTINATION)
          .append("status", PENDING)
          .append("attempts", 0)
          .append("createdAt", Context.now())
          .append("event", toSiemEvent(accountId, event)));
    } catch (MongoWriteException e) {
      if (e.getError().getCategory() != ErrorCategory.DUPLICATE_KEY) { // duplicate = already queued
        logger.errorAndAddToDb("QRadar: could not queue event " + event.getId() + " for account " + accountId + ": " + e.getMessage());
      }
    } catch (Exception e) {
      logger.errorAndAddToDb("QRadar: could not queue event " + event.getId() + " for account " + accountId + ": " + e.getMessage());
    }
  }

  // ---- settings, used by DashboardRouter ----

  public static Document fetchConfig(String accountId) {
    Document config = integrations().find(eq("_id", configId(accountId))).first();
    if (config == null) return new Document();
    config.remove("authToken");
    MongoCollection<Document> outbox = outbox(accountId);
    config.append("pendingCount", outbox.countDocuments(and(eq("destination", DESTINATION), in("status", PENDING, SENDING))));
    config.append("failedCount", outbox.countDocuments(and(eq("destination", DESTINATION), eq("status", FAILED))));
    return config;
  }

  /** A blank authToken keeps the saved one, since fetchConfig never returns it. */
  public static void saveConfig(String accountId, String url, String authToken, boolean enabled) {
    List<Bson> updates = new ArrayList<>();
    updates.add(Updates.set("accountId", Integer.parseInt(accountId)));
    updates.add(Updates.set("type", DESTINATION));
    updates.add(Updates.set("url", url));
    updates.add(Updates.set("enabled", enabled));
    if (authToken != null && !authToken.isEmpty()) updates.add(Updates.set("authToken", authToken));
    integrations().updateOne(eq("_id", configId(accountId)), Updates.combine(updates), new UpdateOptions().upsert(true));
    if (!enabled) outbox(accountId).deleteMany(eq("destination", DESTINATION));
    configsFetchedAt = 0;
  }

  /** Sends one sample event; returns null on success, else the error. */
  public static String sendTestEvent(String accountId, String url, String authToken) {
    if (authToken == null || authToken.isEmpty()) {
      Document saved = integrations().find(eq("_id", configId(accountId))).first();
      authToken = saved != null ? saved.getString("authToken") : null;
    }
    MaliciousEventDto sample = MaliciousEventDto.newBuilder()
        .setFilterId("AktoTestEvent").setCategory("TEST").setSubCategory("TEST").setSeverity("LOW")
        .setActor("0.0.0.0").setLatestApiIp("0.0.0.0").setLatestApiEndpoint("/akto/test")
        .setDetectedAt(Context.now()).setStatus(MaliciousEventDto.Status.ACTIVE).build();
    return post(url, authToken, toBody(Collections.singletonList(toSiemEvent(accountId, sample)))).error;
  }

  // ---- forwarding loop ----

  private static void tick() {
    try {
      Map<String, Document> configs = enabledConfigs();
      if (configs.isEmpty()) return;
      for (Map.Entry<String, Document> entry : configs.entrySet()) {
        if (Context.now() < pausedUntil.getOrDefault(entry.getKey(), 0)) continue;
        drain(entry.getKey(), entry.getValue());
      }
    } catch (Throwable t) {
      logger.errorAndAddToDb("QRadar forwarder tick failed: " + t.getMessage());
    }
  }

  private static void drain(String accountId, Document config) {
    for (int i = 0; i < MAX_BATCHES_PER_TICK && acquireLock(); i++) {
      String batchId = claimBatch(accountId);
      if (batchId == null || !sendBatch(accountId, config, batchId)) return;
    }
  }

  /** Resumes an unfinished batch if there is one, else pins the oldest pending events to a new batchId. */
  private static String claimBatch(String accountId) {
    MongoCollection<Document> outbox = outbox(accountId);
    Document inFlight = outbox.find(and(eq("destination", DESTINATION), eq("status", SENDING)))
        .projection(Projections.include("batchId")).first();
    if (inFlight != null) return inFlight.getString("batchId");

    List<Object> ids = new ArrayList<>();
    outbox.find(and(eq("destination", DESTINATION), eq("status", PENDING)))
        .sort(Sorts.ascending("createdAt")).limit(BATCH_SIZE)
        .projection(Projections.include("_id"))
        .forEach(d -> ids.add(d.get("_id")));
    if (ids.isEmpty()) return null;

    String batchId = UUID.randomUUID().toString();
    outbox.updateMany(and(in("_id", ids), eq("status", PENDING)),
        Updates.combine(Updates.set("status", SENDING), Updates.set("batchId", batchId)));
    return batchId;
  }

  /** Returns true if the next batch can go out right away. */
  private static boolean sendBatch(String accountId, Document config, String batchId) {
    MongoCollection<Document> outbox = outbox(accountId);
    List<Document> items = outbox.find(eq("batchId", batchId)).sort(Sorts.ascending("_id")).into(new ArrayList<>());
    if (items.isEmpty()) return true;

    List<Document> events = new ArrayList<>();
    for (Document item : items) events.add(item.get("event", Document.class));

    SendResult result = post(config.getString("url"), config.getString("authToken"), toBody(events));
    if (result.error == null) {
      outbox.deleteMany(eq("batchId", batchId));
      integrations().updateOne(eq("_id", configId(accountId)), Updates.set("lastSuccessAt", Context.now()));
      return true;
    }

    if (result.isOutage()) {
      pausedUntil.put(accountId, Context.now() + PAUSE_SECONDS);
      recordError(accountId, result.error);
      return false;
    }

    int attempts = items.get(0).getInteger("attempts", 0) + 1;
    if (attempts >= MAX_ATTEMPTS) {
      outbox.updateMany(eq("batchId", batchId), Updates.combine(Updates.set("attempts", attempts), Updates.set("status", FAILED)));
      recordError(accountId, result.error + " (" + items.size() + " events marked FAILED after " + attempts + " attempts)");
    } else {
      outbox.updateMany(eq("batchId", batchId), Updates.set("attempts", attempts));
      recordError(accountId, result.error);
    }
    return false;
  }

  /** {"events":[...]}: the log source uses Event Parsing Method "Event Per JSON Array" with JSON Path /"events". */
  private static String toBody(List<?> events) {
    return new Document("events", events).toJson(RELAXED);
  }

  private static SendResult post(String url, String authToken, String body) {
    Request.Builder request = new Request.Builder().url(url).post(RequestBody.create(body, JSON));
    if (authToken != null && !authToken.isEmpty()) request.addHeader("Authorization", authToken);
    try (Response response = httpClient.newCall(request.build()).execute()) {
      if (response.isSuccessful()) return new SendResult(response.code(), null);
      return new SendResult(response.code(), "QRadar responded " + response.code());
    } catch (Exception e) {
      return new SendResult(-1, "QRadar unreachable: " + e.getMessage());
    }
  }

  private static final class SendResult {
    final int status;
    final String error;

    SendResult(int status, String error) {
      this.status = status;
      this.error = error;
    }

    /** QRadar down, overloaded or rejecting our credentials: nothing is wrong with the batch itself. */
    boolean isOutage() {
      return status < 0 || status >= 500 || status == 401 || status == 403 || status == 408 || status == 429;
    }
  }

  // ---- helpers ----

  private static Map<String, Document> enabledConfigs() {
    if (Context.now() - configsFetchedAt >= CONFIG_TTL_SECONDS) {
      Map<String, Document> configs = new HashMap<>();
      for (Document config : integrations().find(and(eq("type", DESTINATION), eq("enabled", true)))) {
        configs.put(String.valueOf(config.getInteger("accountId")), config);
      }
      enabledConfigs = configs;
      configsFetchedAt = Context.now();
    }
    return enabledConfigs;
  }

  /** The upsert inserts the lock when it's free; a duplicate key means another pod holds it. */
  private static boolean acquireLock() {
    int now = Context.now();
    try {
      common(MongoDBCollection.ThreatDetection.SIEM_FORWARDER_LOCK).updateOne(
          and(eq("_id", DESTINATION), Filters.or(Filters.lt("expiresAt", now), eq("owner", OWNER))),
          Updates.combine(Updates.set("owner", OWNER), Updates.set("expiresAt", now + LOCK_SECONDS)),
          new UpdateOptions().upsert(true));
      return true;
    } catch (MongoWriteException e) {
      if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) return false;
      throw e;
    }
  }

  private static void ensureIndexes(String accountId) {
    if (!indexedAccounts.add(accountId)) return;
    try {
      outbox(accountId).createIndex(Indexes.ascending("destination", "status", "createdAt"));
      outbox(accountId).createIndex(Indexes.ascending("batchId"));
    } catch (Exception e) {
      indexedAccounts.remove(accountId);
      throw e;
    }
  }

  private static void recordError(String accountId, String error) {
    logger.errorAndAddToDb("QRadar forwarding failed for account " + accountId + ": " + error);
    integrations().updateOne(eq("_id", configId(accountId)),
        Updates.combine(Updates.set("lastError", error), Updates.set("lastErrorAt", Context.now())));
  }

  /**
   * The document stored in malicious_events, encoded with the same codec MaliciousEventDao writes with.
   * Only latestApiOrig may be shortened, so the event fits in MAX_EVENT_BYTES; Mongo keeps the full copy.
   */
  private static BsonDocument toSiemEvent(String accountId, MaliciousEventDto event) {
    BsonDocument encoded = new BsonDocument();
    MaliciousEventDao.instance.getCollection(accountId).getCodecRegistry().get(MaliciousEventDto.class)
        .encode(new BsonDocumentWriter(encoded), event, EncoderContext.builder().build());
    // latestApiOrig holds the full request/response; it is the only field that can grow, so trim it to fit
    String orig = event.getLatestApiOrig();
    if (orig != null && jsonBytes(encoded) > MAX_EVENT_BYTES) {
      // longest prefix that fits; escapes and non-ASCII make bytes != chars, so search rather than compute
      int lo = 0, hi = orig.length();
      while (lo < hi) {
        int mid = (lo + hi + 1) >>> 1;
        encoded.put("latestApiOrig", new BsonString(orig.substring(0, mid) + TRUNCATED));
        if (jsonBytes(encoded) <= MAX_EVENT_BYTES) lo = mid; else hi = mid - 1;
      }
      if (lo > 0 && Character.isHighSurrogate(orig.charAt(lo - 1))) lo--; // don't split an emoji or similar
      encoded.put("latestApiOrig", new BsonString(orig.substring(0, lo) + TRUNCATED));
    }
    return encoded;
  }

  private static int jsonBytes(BsonDocument doc) {
    return doc.toJson(RELAXED).getBytes(StandardCharsets.UTF_8).length;
  }

  private static String configId(String accountId) {
    return accountId + "_" + DESTINATION;
  }

  private static MongoCollection<Document> integrations() {
    return common(MongoDBCollection.ThreatDetection.SIEM_INTEGRATIONS);
  }

  private static MongoCollection<Document> common(String collection) {
    return mongoClient.getDatabase(DbNames.COMMON).getCollection(collection).withReadPreference(ReadPreference.primary());
  }

  // Primary reads: the client defaults to secondaries, and a lagging read of a just-claimed batch would send part
  // of it and then delete all of it.
  private static MongoCollection<Document> outbox(String accountId) {
    return mongoClient.getDatabase(accountId).getCollection(MongoDBCollection.ThreatDetection.SIEM_OUTBOX)
        .withReadPreference(ReadPreference.primary());
  }
}
