package com.akto.utils;

import com.akto.data_actor.DataActorFactory;
import com.akto.dto.AgenticUsers;
import com.akto.log.LoggerMaker;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Makes users seen in Atlas LiteLLM traffic pickable as Users in the guardrail policy UI.
 *
 * The dashboard's user picker lists agent_users. This adds the traffic's email there through the
 * database-abstractor APIs the Copilot Studio sync already uses (fetchAllAgentUsers and
 * bulkUpsertAgentUserExternalIdentities, via DataActor), so no database access is needed here.
 * Guardrail matching needs nothing from here: it reads the email header of each request.
 *
 * Those APIs set userId / userName / userEmail only, so the row has no devices or device tags:
 * the user is pickable under Users (matched on email), not under Devices.
 *
 * An email already in agent_users (e.g. from the native Atlas agent) is left alone, so the person
 * is not listed twice. agent_users is read once per process; each new email is then upserted once.
 */
public final class LitellmUserRegistry {

    private static final LoggerMaker loggerMaker = new LoggerMaker(LitellmUserRegistry.class, LoggerMaker.LogDb.DATA_INGESTION);
    static final String LAST_UPDATED_BY = "litellm";

    // The database-abstractor calls; replaceable in tests.
    static Supplier<List<AgenticUsers>> fetchAllAgentUsers = () -> DataActorFactory.fetchInstance().fetchAllAgentUsers();
    static Consumer<List<AgenticUsers>> upsertAgentUsers = users -> DataActorFactory.fetchInstance().bulkUpsertAgentUserExternalIdentities(users);

    // Lowercased emails already in agent_users or already upserted by this process.
    static final Set<String> knownEmails = ConcurrentHashMap.newKeySet();
    private static volatile boolean loaded = false;

    private LitellmUserRegistry() {}

    /** Adds email to agent_users unless it is already there; at most one upsert per email per process; fail-open. */
    public static void register(String email) {
        if (email == null || email.trim().isEmpty()) {
            return;
        }
        String trimmed = email.trim();
        loadKnownEmails();
        if (!knownEmails.add(trimmed.toLowerCase())) {
            return;
        }
        try {
            upsertAgentUsers.accept(Collections.singletonList(identityFor(trimmed)));
        } catch (Exception e) {
            loggerMaker.error("LiteLLM user registration failed for " + trimmed + ": " + e.getMessage(), e);
        }
    }

    /** agent_users row for an email: keyed by the email, named by its host identity segment. */
    static AgenticUsers identityFor(String email) {
        AgenticUsers user = new AgenticUsers();
        user.setUserId(email);
        user.setUserName(AgentHostUtils.identitySlug(AgentHostUtils.emailLocalPart(email)));
        user.setUserEmail(email);
        user.setLastUpdatedBy(LAST_UPDATED_BY);
        return user;
    }

    private static void loadKnownEmails() {
        if (loaded) {
            return;
        }
        synchronized (LitellmUserRegistry.class) {
            if (loaded) {
                return;
            }
            try {
                List<AgenticUsers> users = fetchAllAgentUsers.get();
                if (users != null) {
                    for (AgenticUsers u : users) {
                        // Rows picked by an email-shaped username carry the address there instead.
                        for (String e : new String[]{u.getUserEmail(), u.getUserName()}) {
                            if (e != null && e.contains("@")) {
                                knownEmails.add(e.trim().toLowerCase());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                loggerMaker.error("LiteLLM user registry: could not read agent_users: " + e.getMessage(), e);
            }
            loaded = true;
        }
    }

    /** Test hook: forget what was loaded and registered. */
    static void reset() {
        knownEmails.clear();
        loaded = false;
    }
}
