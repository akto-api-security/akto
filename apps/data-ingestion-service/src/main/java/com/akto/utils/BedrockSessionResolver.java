package com.akto.utils;

import com.akto.data_actor.ClientActor;
import com.akto.dto.IngestDataBatch;
import com.akto.log.LoggerMaker;
import com.mongodb.BasicDBObject;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gives Bedrock ingest payloads a conversation id when the caller sent none.
 *
 * Model-invocation logs carry no session or conversation id, so every call lands as its own
 * trace and one agent run shows up as many rows. The caller's identity ARN
 * ({@code bedrock-identity-arn}) stands in for it: calls from the same ARN with no more than
 * {@link #SESSION_GAP_SECONDS} between them share one session id. For an agent role the ARN's
 * session name is already per task (assumed-role/aria-usertask-role/&lt;hex&gt;); for an SSO user it
 * is the person, so the gap is what splits their conversations.
 *
 * The id goes into the same request header installers send it in, so mini-runtime stores it as
 * sessionIdentifier and guardrails see it, without either learning about Bedrock.
 *
 * State is in memory: a restart starts fresh sessions for callers that were mid-conversation.
 */
public final class BedrockSessionResolver {

    static final String SESSION_HEADER = "x-akto-installer-akto_session_id";
    static final long SESSION_GAP_SECONDS = 20 * 60;
    static final String SESSION_ID_PREFIX = "bedrock-";
    private static final String OPERATION_HEADER = "bedrock-operation";
    private static final String DISCOVERY_OPERATION = "DISCOVERY";
    private static final int MAX_TRACKED_CALLERS = 10_000;

    private static final int TAPESTRY_ACCOUNT_ID = 1703087742;
    private static final Integer ACCOUNT_ID = ClientActor.getAbstractorAccountIdFromEnvOrNull();
    private static final BedrockSessionResolver INSTANCE = new BedrockSessionResolver();
    private static final LoggerMaker loggerMaker = new LoggerMaker(BedrockSessionResolver.class, LoggerMaker.LogDb.DATA_INGESTION);

    private final Map<String, Session> sessionsByArn =
            new LinkedHashMap<String, Session>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Session> eldest) {
                    return size() > MAX_TRACKED_CALLERS;
                }
            };

    public static void apply(IngestDataBatch payload) {
        if (ACCOUNT_ID == null || ACCOUNT_ID != TAPESTRY_ACCOUNT_ID) {
            return;
        }
        try {
            INSTANCE.resolve(payload);
        } catch (RuntimeException e) {
            // Grouping is best effort: a failure here must never fail the ingest batch.
            loggerMaker.warn("Bedrock session resolve failed: " + e.getMessage());
        }
    }

    void resolve(IngestDataBatch payload) {
        if (payload == null) {
            return;
        }
        String arn = BedrockIdentityActor.firstIdentityArn(payload.getRequestHeaders(), payload.getTag());
        if (arn.isEmpty()) {
            return;
        }
        BasicDBObject headers = BedrockIdentityActor.parseObject(payload.getRequestHeaders());
        if (headers == null
                || !BedrockIdentityActor.lookupIgnoreCase(headers, SESSION_HEADER).isEmpty()
                // Discovery messages describe a resource, not a call, so they must not extend a session.
                || DISCOVERY_OPERATION.equalsIgnoreCase(BedrockIdentityActor.lookupIgnoreCase(headers, OPERATION_HEADER))) {
            return;
        }
        headers.put(SESSION_HEADER, sessionIdFor(arn, callTimeSeconds(payload.getTime())));
        payload.setRequestHeaders(headers.toJson());
    }

    synchronized String sessionIdFor(String arn, long callTime) {
        Session session = sessionsByArn.get(arn);
        if (session != null && callTime < session.lastCallTime - SESSION_GAP_SECONDS) {
            // A late-delivered call from an older conversation must not replace the live one.
            return newSessionId(arn, callTime);
        }
        if (session == null || callTime - session.lastCallTime > SESSION_GAP_SECONDS) {
            session = new Session(newSessionId(arn, callTime), callTime);
            sessionsByArn.put(arn, session);
        }
        session.lastCallTime = Math.max(session.lastCallTime, callTime);
        return session.id;
    }

    private static String newSessionId(String arn, long startTime) {
        return SESSION_ID_PREFIX
                + UUID.nameUUIDFromBytes((arn + "|" + startTime).getBytes(StandardCharsets.UTF_8));
    }

    /** The log's own timestamp (seconds); ingest time only when the payload has none. */
    private static long callTimeSeconds(String time) {
        try {
            return Long.parseLong(time.trim());
        } catch (RuntimeException e) {
            return System.currentTimeMillis() / 1000L;
        }
    }

    private static final class Session {
        final String id;
        long lastCallTime;

        Session(String id, long lastCallTime) {
            this.id = id;
            this.lastCallTime = lastCallTime;
        }
    }
}
