package com.akto.action.settings;

import com.akto.action.UserAction;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dto.EndpointShieldSettings;
import com.akto.dto.PlatformShieldConfig;
import com.mongodb.client.model.Updates;
import lombok.Getter;
import lombok.Setter;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class EndpointShieldSettingsAction extends UserAction {

    private static final long MANIFEST_CACHE_TTL_MS = 60 * 60 * 1000L; // 1 hour

    // Dot-notation prefix for nested fields: endpointShieldSettings.platforms.<platformKey>.<field>
    private static final String PLATFORMS_PREFIX =
        AccountSettings.ENDPOINT_SHIELD_SETTINGS + "." + EndpointShieldSettings.PLATFORMS + ".";

    private static final String S3_BASE = "https://akto-endpoint-agents.s3.us-east-1.amazonaws.com/atlas-installers";

    // MDM URLs are shared across all accounts; direct installer URLs are per-account
    private static String defaultManifestUrl(String platformKey) {
        int accountId = Context.accountId.get();
        switch (platformKey) {
            case EndpointShieldSettings.PLATFORM_MACOS_MDM:
                return S3_BASE + "/jamf-releases/latest.json";
            case EndpointShieldSettings.PLATFORM_WINDOWS_MDM:
                return S3_BASE + "/windows-mdm-releases/latest.json";
            case EndpointShieldSettings.PLATFORM_MACOS_DIRECT:
                return S3_BASE + "/" + accountId + "/macos-direct-releases/latest.json";
            case EndpointShieldSettings.PLATFORM_WINDOWS_DIRECT:
                return S3_BASE + "/" + accountId + "/windows-direct-releases/latest.json";
            default:
                return null;
        }
    }

    @Getter @Setter private EndpointShieldSettings endpointShieldSettings;
    @Getter @Setter private String platformKey;
    @Getter @Setter private PlatformShieldConfig platformConfig;

    public String fetchEndpointShieldSettings() {
        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(
            AccountSettingsDao.generateFilter());

        EndpointShieldSettings existing = accountSettings != null
            ? accountSettings.getEndpointShieldSettings()
            : null;

        if (existing == null) {
            existing = new EndpointShieldSettings();
        }

        Map<String, PlatformShieldConfig> platforms = existing.getPlatforms();
        if (platforms == null) platforms = new HashMap<>();

        // Seed defaults for any platform missing or lacking a manifest URL
        for (String key : EndpointShieldSettings.ALL_PLATFORMS) {
            PlatformShieldConfig existing_cfg = platforms.get(key);
            boolean missingUrl = existing_cfg == null || existing_cfg.getManifestUrl() == null || existing_cfg.getManifestUrl().isEmpty();
            if (missingUrl) {
                if (existing_cfg == null) {
                    existing_cfg = new PlatformShieldConfig();
                    existing_cfg.setAutoUpdateEnabled(true);
                    platforms.put(key, existing_cfg);
                }
                existing_cfg.setManifestUrl(defaultManifestUrl(key));
                AccountSettingsDao.instance.updateOne(
                    AccountSettingsDao.generateFilter(),
                    Updates.set(PLATFORMS_PREFIX + key + "." + PlatformShieldConfig.MANIFEST_URL, existing_cfg.getManifestUrl())
                );
            }
        }

        // Refresh stale platform caches
        for (String key : EndpointShieldSettings.ALL_PLATFORMS) {
            PlatformShieldConfig cfg = platforms.get(key);
            if (cfg == null || cfg.getManifestUrl() == null) continue;
            long age = System.currentTimeMillis() - cfg.getLatestVersionFetchedAt();
            if (age > MANIFEST_CACHE_TTL_MS) {
                refreshPlatformFromManifest(key, cfg.getManifestUrl());
            }
        }

        endpointShieldSettings = AccountSettingsDao.instance
            .findOne(AccountSettingsDao.generateFilter())
            .getEndpointShieldSettings();

        return SUCCESS.toUpperCase();
    }

    // Saves a single platform's config; platformKey and platformConfig must be set
    public String saveEndpointShieldSettings() {
        if (platformKey == null || platformConfig == null) return ERROR.toUpperCase();
        if (!EndpointShieldSettings.ALL_PLATFORMS.contains(platformKey)) return ERROR.toUpperCase();

        AccountSettings account = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        EndpointShieldSettings existing = account != null ? account.getEndpointShieldSettings() : null;
        PlatformShieldConfig existingCfg = (existing != null && existing.getPlatforms() != null)
            ? existing.getPlatforms().get(platformKey)
            : null;

        String existingUrl = existingCfg != null ? existingCfg.getManifestUrl() : null;
        boolean urlChanged = !platformConfig.getManifestUrl().equals(existingUrl);

        if (urlChanged) {
            platformConfig.setLatestVersion(null);
            platformConfig.setLatestVersionFetchedAt(0L);
        } else if (existingCfg != null) {
            platformConfig.setLatestVersion(existingCfg.getLatestVersion());
            platformConfig.setLatestVersionFetchedAt(existingCfg.getLatestVersionFetchedAt());
        }

        AccountSettingsDao.instance.updateOne(
            AccountSettingsDao.generateFilter(),
            Updates.set(PLATFORMS_PREFIX + platformKey, platformConfig)
        );
        return SUCCESS.toUpperCase();
    }

    // Refreshes latestVersion for a single platform; platformKey must be set
    public String refreshLatestVersion() {
        if (platformKey == null || !EndpointShieldSettings.ALL_PLATFORMS.contains(platformKey)) {
            return ERROR.toUpperCase();
        }

        AccountSettings account = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        EndpointShieldSettings existing = account != null ? account.getEndpointShieldSettings() : null;
        PlatformShieldConfig cfg = (existing != null && existing.getPlatforms() != null)
            ? existing.getPlatforms().get(platformKey)
            : null;

        String manifestUrl = (cfg != null && cfg.getManifestUrl() != null)
            ? cfg.getManifestUrl()
            : defaultManifestUrl(platformKey);

        if (manifestUrl == null) {
            addActionError("No manifest URL configured for platform: " + platformKey);
            return ERROR.toUpperCase();
        }

        boolean success = refreshPlatformFromManifest(platformKey, manifestUrl);
        if (!success) {
            addActionError("Failed to fetch version from manifest URL. Please check the URL and try again.");
            return ERROR.toUpperCase();
        }

        endpointShieldSettings = AccountSettingsDao.instance
            .findOne(AccountSettingsDao.generateFilter())
            .getEndpointShieldSettings();
        return SUCCESS.toUpperCase();
    }

    private boolean refreshPlatformFromManifest(String key, String manifestUrl) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(manifestUrl).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != 200) return false;

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }

            String latest = new JSONObject(sb.toString()).optString("version", null);
            if (latest == null) return false;

            AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.combine(
                    Updates.set(PLATFORMS_PREFIX + key + "." + PlatformShieldConfig.LATEST_VERSION,            latest),
                    Updates.set(PLATFORMS_PREFIX + key + "." + PlatformShieldConfig.LATEST_VERSION_FETCHED_AT, System.currentTimeMillis())
                )
            );
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
