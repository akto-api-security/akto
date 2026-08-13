package com.akto.utils.crons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.akto.dao.AgentUsersDao;
import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.Config.OktaConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.task.Cluster;
import com.akto.util.AccountTask;
import com.akto.util.Constants;
import com.akto.util.http_request.OktaApiClient;
import com.akto.util.http_request.OktaApiClient.OktaGroupRef;
import com.akto.util.http_request.OktaApiClient.OktaUserRef;
import com.mongodb.client.model.Filters;

import static com.akto.task.Cluster.callDibs;

/**
 * Periodically syncs every Okta org user's group membership into AgenticUsers as "group" device
 * tags — not just the people who happen to log into Akto via Okta. This is the *only* place
 * device tags get written from Okta group data; SignupAction.registerViaOkta still resolves
 * groups at login, but only to drive Akto RBAC role assignment, not device tags.
 *
 * Only runs for accounts whose OktaConfig has syncGroupsToUserTags=true AND a managementApiToken
 * set — the dashboard UI (OktaSsoAction.saveOktaGroupRoleMapping) rejects enabling the former
 * without the latter, but this cron re-checks both since config can change between the UI
 * validation and this cron's next tick.
 */
public class OktaUserSyncCron {
    private static final LoggerMaker logger = new LoggerMaker(OktaUserSyncCron.class, LogDb.DASHBOARD);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static final String SYNC_SOURCE = "okta";
    private static final String GROUP_TAG_KEY = "group";

    public void setUpOktaUserSyncScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                try {
                    Context.accountId.set(1_000_000);
                    // expiryPeriod (55 min) comfortably covers a slow run without overlapping the
                    // next hourly tick; freqInSeconds (30 min) just needs to be under that.
                    boolean dibs = callDibs(Cluster.OKTA_USER_SYNC_CRON, 55 * 60, 30 * 60);
                    if (!dibs) {
                        logger.debugAndAddToDb("Okta user sync cron dibs not acquired, skipping", LogDb.DASHBOARD);
                        return;
                    }
                    AccountTask.instance.executeTask(new Consumer<Account>() {
                        @Override
                        public void accept(Account account) {
                            syncAccountIfEnabled(account.getId());
                        }
                    }, "okta-user-sync");
                } catch (Exception e) {
                    logger.errorAndAddToDb(e, "Error in Okta user sync scheduler", LogDb.DASHBOARD);
                }
            }
        }, 0, 1, TimeUnit.HOURS);
    }

    private void syncAccountIfEnabled(int accountId) {
        try {
            OktaConfig oktaConfig = (OktaConfig) ConfigsDao.instance.findOne(
                    Filters.eq(Constants.ID, OktaConfig.getOktaId(accountId)));
            if (oktaConfig == null || !oktaConfig.isSyncGroupsToUserTags()) return;
            String token = oktaConfig.getManagementApiToken();
            if (token == null || token.trim().isEmpty()) {
                logger.errorAndAddToDb(
                        "[Okta] group sync enabled for account " + accountId + " but no management token set — skipping",
                        LogDb.DASHBOARD);
                return;
            }
            syncAccount(accountId, oktaConfig, token);
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error syncing Okta users for account " + accountId, LogDb.DASHBOARD);
        }
    }

    private void syncAccount(int accountId, OktaConfig oktaConfig, String token) {
        String baseUrl = oktaConfig.getManagementBaseUrl();

        List<OktaGroupRef> groups = OktaApiClient.fetchAllGroups(baseUrl, token);

        // Okta user id -> group names, accumulated across every group's member roster. Fetching
        // group-by-group (rather than per-user) keeps call volume proportional to group count,
        // which is normally far smaller than user count.
        Map<String, List<String>> groupNamesByUserId = new HashMap<>();
        for (OktaGroupRef group : groups) {
            List<OktaUserRef> members = OktaApiClient.fetchGroupMembers(baseUrl, token, group.id);
            for (OktaUserRef member : members) {
                if (member.id == null) continue;
                groupNamesByUserId.computeIfAbsent(member.id, k -> new ArrayList<>()).add(group.name);
            }
        }

        // Every active org user is visited — including those in zero groups — so
        // upsertDeviceTags' full-replace-by-source semantics correctly clear a user's stale
        // "okta" group tag once they've left every group, not just add new ones.
        List<OktaUserRef> allUsers = OktaApiClient.fetchAllUsers(baseUrl, token);
        int syncedCount = 0;
        for (OktaUserRef user : allUsers) {
            String username = user.login != null && !user.login.trim().isEmpty() ? user.login : user.email;
            if (username == null || username.trim().isEmpty()) continue;

            String identityUserName = AgentUsersDao.instance.syncSsoIdentity(username, user.email, SYNC_SOURCE);
            if (identityUserName == null) continue;

            // syncAccountIfEnabled already confirmed isSyncGroupsToUserTags() before calling here.
            List<String> groupNames = groupNamesByUserId.getOrDefault(user.id, Collections.emptyList());
            AgentUsersDao.instance.upsertDeviceTags(identityUserName, SYNC_SOURCE,
                    Collections.singletonMap(GROUP_TAG_KEY, groupNames), SYNC_SOURCE);
            syncedCount++;
        }

        logger.infoAndAddToDb("[Okta] periodic sync complete for account " + accountId + ": "
                + syncedCount + " users, " + groups.size() + " groups", LogDb.DASHBOARD);
    }
}
