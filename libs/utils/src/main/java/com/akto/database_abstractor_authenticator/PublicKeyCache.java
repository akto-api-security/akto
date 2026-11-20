package com.akto.database_abstractor_authenticator;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.TimeUnit;

// Caches the JWT public key so it isn't read from the configs collection and parsed on every request
class PublicKeyCache {
    private static final LoggerMaker loggerMaker = new LoggerMaker(PublicKeyCache.class, LogDb.DB_ABS);

    private static final long TTL_MS = TimeUnit.MINUTES.toMillis(10);
    // Forced reloads are capped so forged tokens can't trigger a DB read each
    private static final long MIN_RELOAD_MS = TimeUnit.SECONDS.toMillis(30);

    interface Loader {
        PublicKey load() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException;
    }

    private final Loader loader;
    private final Object lock = new Object();
    private volatile PublicKey cachedKey;
    private volatile long loadedAt;
    private volatile long lastForcedReloadAt;

    PublicKeyCache(Loader loader) {
        this.loader = loader;
    }

    PublicKey get() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        return fetch(false);
    }

    // Used after a bad signature, since the key may have been rotated
    PublicKey reload() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        return fetch(true);
    }

    private boolean isUsable(boolean forceReload, long now) {
        if (cachedKey == null) {
            return false;
        }
        return forceReload
                ? now - lastForcedReloadAt < MIN_RELOAD_MS
                : now - loadedAt < TTL_MS;
    }

    private PublicKey fetch(boolean forceReload) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        long now = System.currentTimeMillis();
        if (isUsable(forceReload, now)) {
            return cachedKey;
        }

        synchronized (lock) {
            // Another thread may have refreshed (or tried to) while this one waited for the lock
            if (isUsable(forceReload, now)) {
                return cachedKey;
            }
            if (forceReload) {
                lastForcedReloadAt = now;
            }
            try {
                cachedKey = loader.load();
            } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException | RuntimeException e) {
                if (cachedKey == null) {
                    throw e;
                }
                // Keep authenticating with the last good key and retry after the TTL instead of failing every request
                loggerMaker.errorAndAddToDb(e, "Failed to refresh JWT public key, using cached key");
            }
            loadedAt = now;
            return cachedKey;
        }
    }
}
