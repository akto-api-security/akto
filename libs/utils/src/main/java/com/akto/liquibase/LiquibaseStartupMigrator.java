package com.akto.liquibase;

import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.util.AccountTask;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.resource.ClassLoaderResourceAccessor;

// Runs Liquibase forward migrations at startup (when AKTO_RUN_DB_MIGRATIONS is set),
// per DB scope: common and billing once each, account once per active account DB.
public final class LiquibaseStartupMigrator {

    private static final LoggerMaker logger =
            new LoggerMaker(LiquibaseStartupMigrator.class, LoggerMaker.LogDb.DASHBOARD);

    private static final String ENV_FLAG = "AKTO_RUN_DB_MIGRATIONS";
    private static final String CHANGELOG_DIR = "db/changelog";
    private static final ClassLoaderResourceAccessor RESOURCE_ACCESSOR = new ClassLoaderResourceAccessor();

    private LiquibaseStartupMigrator() {
    }

    public static void runIfEnabled(String mongoUrl) {
        if (!isMigrationEnabled()) {
            return;
        }
        if (mongoUrl == null || mongoUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("MongoDB connection string is empty.");
        }

        logger.info(ENV_FLAG + " enabled, checking for DB migrations");

        // common and billing: scope name == DB name
        runScope(mongoUrl, "common", "common", true);
        runScope(mongoUrl, "billing", "billing", true);

        // account: one DB per active account (DB name = accountId)
        if (hasMigrations(folderFor("account"))) {
            AccountTask.instance.executeTask(
                    account -> runScope(mongoUrl, "account", Context.accountId.get() + "", false),
                    "liquibase-account-migrations");
        } else {
            logger.info("No account migrations, skipping account scope");
        }
    }

    private static void runScope(String baseUrl, String scope, String dbName, boolean checkFolder) {
        if (checkFolder && !hasMigrations(folderFor(scope))) {
            logger.info("No " + scope + " migrations, skipping DB " + dbName);
            return;
        }
        String changelog = folderFor(scope) + ".xml";
        logger.info("Applying " + scope + " migrations to DB " + dbName);
        try (Database database = DatabaseFactory.getInstance()
                .openDatabase(buildUrlForDb(baseUrl, dbName), null, null, null, RESOURCE_ACCESSOR)) {
            new Liquibase(changelog, RESOURCE_ACCESSOR, database).update(new Contexts());
        } catch (Exception e) {
            logger.errorAndAddToDb("Liquibase migrations failed for DB " + dbName + ": " + e.getMessage(),
                    LoggerMaker.LogDb.DASHBOARD);
            throw new RuntimeException("Liquibase migrations failed for DB " + dbName, e);
        }
    }

    private static String folderFor(String scope) {
        return CHANGELOG_DIR + "/" + scope;
    }

    private static boolean isMigrationEnabled() {
        String flag = System.getenv(ENV_FLAG);
        return "true".equalsIgnoreCase(flag) || "1".equals(flag);
    }

    private static boolean hasMigrations(String folder) {
        try {
            return RESOURCE_ACCESSOR.search(folder, true).stream()
                    .anyMatch(r -> r.getPath().endsWith(".xml"));
        } catch (Exception e) {
            return false;
        }
    }

    // Swaps the DB name in the connection string, keeping credentials, host(s) and options.
    private static String buildUrlForDb(String baseUrl, String dbName) {
        int qIdx = baseUrl.indexOf('?');
        String options = qIdx >= 0 ? baseUrl.substring(qIdx) : "";
        String withoutOptions = qIdx >= 0 ? baseUrl.substring(0, qIdx) : baseUrl;

        int authStart = withoutOptions.indexOf("://") + 3;
        int dbSlash = withoutOptions.indexOf('/', authStart);
        String hostPart = dbSlash >= 0 ? withoutOptions.substring(0, dbSlash) : withoutOptions;
        return hostPart + "/" + dbName + options;
    }
}
