package com.akto.utils.crons;

import java.util.HashSet;
import java.util.Set;
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
 * non-Akto user. Keeps heavy crons from running for every account ever created.
 */
public class DormantAccountFilter {

    public static final int TRAFFIC_STALE_SECONDS = 15 * Constants.ONE_DAY_TIMESTAMP;
    public static final int LOGIN_STALE_SECONDS = 7 * Constants.ONE_DAY_TIMESTAMP;

    private static final Pattern AKTO_USER_LOGIN = Pattern.compile("@" + Pattern.quote(InviteUserAction.AKTO_DOMAIN) + "$", Pattern.CASE_INSENSITIVE);

    private static final LoggerMaker loggerMaker = new LoggerMaker(DormantAccountFilter.class, LogDb.DASHBOARD);

    private DormantAccountFilter() {}

    /**
     * Account ids that have at least one non-Akto user who logged in within {@link #LOGIN_STALE_SECONDS}.
     * One query on the common users collection; call once per cron run, outside the per-account loop.
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
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error fetching accounts with recent user login: " + e.getMessage());
        }
        loggerMaker.info("Accounts with a non-Akto user login in the last " + LOGIN_STALE_SECONDS / (24 * 60 * 60) + " days: "
            + accountIds.size() + ", fetched in " + (System.currentTimeMillis() - startMs) + " ms");
        return accountIds;
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
