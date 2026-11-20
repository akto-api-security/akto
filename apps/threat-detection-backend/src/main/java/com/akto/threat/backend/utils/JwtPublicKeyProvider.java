package com.akto.threat.backend.utils;

import com.akto.dao.ConfigsDao;
import com.akto.dto.Config;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

public final class JwtPublicKeyProvider {

  private static final LoggerMaker logger =
      new LoggerMaker(JwtPublicKeyProvider.class, LogDb.THREAT_DETECTION);

  private static final long TTL_MS = TimeUnit.MINUTES.toMillis(10);
  private static final Object lock = new Object();
  private static volatile CachedKey cached;

  private JwtPublicKeyProvider() {}

  public static PublicKey get()
      throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
    CachedKey current = cached;
    if (isFresh(current)) {
      return current.key;
    }

    synchronized (lock) {
      current = cached;
      if (isFresh(current)) {
        return current.key;
      }
      try {
        PublicKey loaded = load();
        cached = new CachedKey(loaded, System.currentTimeMillis());
        return loaded;
      } catch (Exception e) {
        if (current == null) {
          throw e;
        }
        logger.error("Public key reload failed, serving stale key", e);
        return current.key;
      }
    }
  }

  private static boolean isFresh(CachedKey entry) {
    return entry != null && System.currentTimeMillis() - entry.loadedAt < TTL_MS;
  }

  private static PublicKey load()
      throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
    Config.HybridSaasConfig config =
        (Config.HybridSaasConfig)
            ConfigsDao.instance.findOne("_id", Config.ConfigType.HYBRID_SAAS.name());
    if (config == null || config.getPublicKey() == null) {
      throw new IllegalStateException("HYBRID_SAAS public key is not configured");
    }

    String pem =
        config
            .getPublicKey()
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
    byte[] decoded = Base64.getDecoder().decode(pem);
    return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
  }

  private static final class CachedKey {
    final PublicKey key;
    final long loadedAt;

    CachedKey(PublicKey key, long loadedAt) {
      this.key = key;
      this.loadedAt = loadedAt;
    }
  }
}
