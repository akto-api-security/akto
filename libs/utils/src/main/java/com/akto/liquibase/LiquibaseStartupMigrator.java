package com.akto.liquibase;

import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.util.AccountTask;
import com.akto.util.DashboardMode;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.resource.ClassLoaderResourceAccessor;

// Runs Liquibase forward migrations at startup (when AKTO_RUN_DB_MIGRATIONS=true).
// Scopes: common DB once, billing DB once, account DB once per active account (or fixed 1_000_000 on-prem).
public final class LiquibaseStartupMigrator {

    private static final LoggerMaker logger =
            new LoggerMaker(LiquibaseStartupMigrator.class, LoggerMaker.LogDb.DASHBOARD);

    private static final String ENV_FLAG = "AKTO_RUN_DB_MIGRATIONS";
    private static final String CHANGELOG_DIR = "db/changelog";
    private static final ClassLoaderResourceAccessor RESOURCE_ACCESSOR = new ClassLoaderResourceAccessor();
    private static final int ON_PREM_ACCOUNT_ID = 1_000_000;

    private LiquibaseStartupMigrator() {
    }

    public static boolean isMigrationEnabled() {
        String flag = System.getenv(ENV_FLAG);
        return "true".equalsIgnoreCase(flag) || "1".equals(flag);
    }

    public static void runIfEnabled(String mongoUrl) {
        if (!isMigrationEnabled()) {
            return;
        }
        if (mongoUrl == null || mongoUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("MongoDB connection string is empty.");
        }

        logger.infoAndAddToDb(ENV_FLAG + "=true, running DB migrations");

        runScope(mongoUrl, "common", "common");
        runScope(mongoUrl, "billing", "billing");

        // On-prem: single fixed account; SaaS/multi-tenant: iterate active accounts via AccountTask.
        if (DashboardMode.isOnPremDeployment()) {
            runScope(mongoUrl, "account", String.valueOf(ON_PREM_ACCOUNT_ID));
        } else {
            AccountTask.instance.executeTask(
                    account -> runScope(mongoUrl, "account", Context.accountId.get() + ""),
                    "liquibase-account-migrations");
        }

        logger.infoAndAddToDb("DB migrations complete");
    }

    private static void runScope(String baseUrl, String scope, String dbName) {
        String changelog = CHANGELOG_DIR + "/" + scope + ".xml";
        logger.infoAndAddToDb("Applying " + scope + " migrations to DB " + dbName);
        try (Database database = DatabaseFactory.getInstance()
                .openDatabase(buildUrlForDb(baseUrl, dbName), null, null, null, RESOURCE_ACCESSOR);
             Liquibase liquibase = new Liquibase(changelog, RESOURCE_ACCESSOR, database)) {
            liquibase.update(new Contexts());
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Liquibase migrations failed for scope=" + scope + " db=" + dbName);
            throw new RuntimeException("Liquibase migrations failed for scope=" + scope + " db=" + dbName, e);
        }
    }

    // Swaps the DB name in the connection string, keeping credentials, host(s) and options.
    static String buildUrlForDb(String baseUrl, String dbName) {
        int schemeEnd = baseUrl.indexOf("://");
        if (schemeEnd < 0) {
            throw new IllegalArgumentException("Invalid MongoDB URI (missing ://): " + baseUrl);
        }
        int qIdx = baseUrl.indexOf('?');
        String options = qIdx >= 0 ? baseUrl.substring(qIdx) : "";
        String withoutOptions = qIdx >= 0 ? baseUrl.substring(0, qIdx) : baseUrl;

        int authStart = schemeEnd + 3;
        int dbSlash = withoutOptions.indexOf('/', authStart);
        String hostPart = dbSlash >= 0 ? withoutOptions.substring(0, dbSlash) : withoutOptions;
        return hostPart + "/" + dbName + options;
    }
}
