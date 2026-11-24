package com.akto.utils.crons;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.bson.conversions.Bson;

import com.akto.action.InviteUserAction;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.User;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;

/**
 * Lets per-account crons skip accounts that are effectively dormant: no recent traffic AND no recent login by a
 * non-Akto user (except Ironheart, which is staff-only and counts @akto.io logins). Keeps heavy crons from
 * running for every account ever created.
 */
public class DormantAccountFilter {

    public static final int TRAFFIC_STALE_SECONDS = 15 * Constants.ONE_DAY_TIMESTAMP;
    public static final int LOGIN_STALE_SECONDS = 7 * Constants.ONE_DAY_TIMESTAMP;

    /** Ironheart demo: only @akto.io users, so those logins count as recent activity for dormant checks. */
    public static final int IRONHEART_ACCOUNT_ID = 1669322524;

    private static final Pattern AKTO_USER_LOGIN = Pattern.compile("@" + Pattern.quote(InviteUserAction.AKTO_DOMAIN) + "$", Pattern.CASE_INSENSITIVE);

    private static final LoggerMaker loggerMaker = new LoggerMaker(DormantAccountFilter.class, LogDb.DASHBOARD);

    private DormantAccountFilter() {}

    /**
     * Account ids with a recent login that should keep the account non-dormant: any non-Akto user within
     * {@link #LOGIN_STALE_SECONDS}, plus Ironheart when an @akto.io user on that account logged in in the same window.
     * One (or two) queries on the common users collection; call once per cron run, outside the per-account loop.
     * On failure returns an empty set, which falls back to the per-account traffic check alone.
     */
    public static Set<Integer> fetchAccountsWithRecentUserLogin() {
        long startMs = System.currentTimeMillis();
        Set<Integer> accountIds = new HashSet<>();
        try {
            Bson filter = Filters.and(
                    Filters.gte(User.LAST_LOGIN_TS, Context.now() - LOGIN_STALE_SECONDS),
                    Filters.not(Filters.regex(User.LOGIN, AKTO_USER_LOGIN))
            );
            try (MongoCursor<User> cursor = UsersDao.instance.getMCollection().find(filter)
                    .projection(Projections.include(User.LOGIN, User.ACCOUNTS)).cursor()) {
                while (cursor.hasNext()) {
                    User user = cursor.next();
                    if (user.getAccounts() == null) {
                        continue;
                    }
                    for (String accountId : user.getAccounts().keySet()) {
                        try {
                            accountIds.add(Integer.parseInt(accountId));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }

            // Ironheart is staff-only; count recent @akto.io logins for that account only.
            if (!accountIds.contains(IRONHEART_ACCOUNT_ID)) {
                Bson ironheartFilter = Filters.and(
                        Filters.gte(User.LAST_LOGIN_TS, Context.now() - LOGIN_STALE_SECONDS),
                        Filters.regex(User.LOGIN, AKTO_USER_LOGIN),
                        Filters.exists(User.ACCOUNTS + "." + IRONHEART_ACCOUNT_ID, true)
                );
                if (UsersDao.instance.getMCollection().find(ironheartFilter)
                        .projection(Projections.include(User.LOGIN))
                        .limit(1)
                        .first() != null) {
                    accountIds.add(IRONHEART_ACCOUNT_ID);
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error fetching accounts with recent user login: " + e.getMessage());
        }
        loggerMaker.info("Accounts with a recent user login in the last " + LOGIN_STALE_SECONDS / (24 * 60 * 60) + " days: "
            + accountIds.size() + ", fetched in " + (System.currentTimeMillis() - startMs) + " ms");
        return accountIds;
    }

    /**
     * Fetches the recent logins once and returns the test "this account is not dormant", for a cron that loops over
     * accounts itself. The test must run with Context.accountId set.
     */
    public static Predicate<Integer> fetchNotDormantTest() {
        Set<Integer> accountsWithRecentUserLogin = fetchAccountsWithRecentUserLogin();
        return accountId -> !isDormant(accountId, accountsWithRecentUserLogin);
    }

    /** Must be called with Context.accountId set. */
    public static boolean isDormant(int accountId, Set<Integer> accountsWithRecentUserLogin) {
        if (accountsWithRecentUserLogin.contains(accountId)) {
            return false;
        }
        SingleTypeInfo latest = SingleTypeInfoDao.instance.getMCollection().find()
                .sort(Sorts.descending(SingleTypeInfo.LAST_SEEN)).limit(1)
                .projection(Projections.include(SingleTypeInfo.LAST_SEEN)).first();
        return latest == null || latest.getLastSeen() < Context.now() - TRAFFIC_STALE_SECONDS;
    }
}
