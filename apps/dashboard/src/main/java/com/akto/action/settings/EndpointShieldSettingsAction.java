package com.akto.action.settings;

import com.akto.action.UserAction;
import com.akto.dao.AccountSettingsDao;
import com.akto.dto.AccountSettings;
import com.akto.dto.EndpointShieldSettings;
import com.mongodb.client.model.Updates;
import lombok.Getter;
import lombok.Setter;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class EndpointShieldSettingsAction extends UserAction {

    private static final long MANIFEST_CACHE_TTL_MS = 60 * 60 * 1000L; // 1 hour
    private static final String DEFAULT_MANIFEST_URL =
        "https://akto-endpoint-agents.s3.us-east-1.amazonaws.com/atlas-installers/jamf-releases/latest.json";

    // Dot-notation prefix for nested sub-document fields
    private static final String FIELD_PREFIX = AccountSettings.ENDPOINT_SHIELD_SETTINGS + ".";

    @Getter @Setter private EndpointShieldSettings endpointShieldSettings;
    @Getter @Setter private boolean stale;

    public String fetchEndpointShieldSettings() {
        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(
            AccountSettingsDao.generateFilter());

        if (accountSettings == null) {
            endpointShieldSettings = new EndpointShieldSettings();
            return SUCCESS.toUpperCase();
        }

        EndpointShieldSettings existing = accountSettings.getEndpointShieldSettings();

        // Persist defaults on first use
        if (existing == null || existing.getManifestUrl() == null) {
            EndpointShieldSettings defaults = new EndpointShieldSettings();
            defaults.setManifestUrl(DEFAULT_MANIFEST_URL);
            defaults.setAutoUpdateEnabled(true);

            AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.ENDPOINT_SHIELD_SETTINGS, defaults)
            );
            accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
            existing = accountSettings.getEndpointShieldSettings();
        }

        long age = System.currentTimeMillis() - existing.getLatestVersionFetchedAt();
        stale = age > MANIFEST_CACHE_TTL_MS;
        if (stale) {
            refreshFromManifest(existing.getManifestUrl());
            accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
            existing = accountSettings.getEndpointShieldSettings();
        }

        endpointShieldSettings = existing;
        return SUCCESS.toUpperCase();
    }

    public String saveEndpointShieldSettings() {
        if (endpointShieldSettings == null) return ERROR.toUpperCase();

        AccountSettings account = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        EndpointShieldSettings existing = account != null ? account.getEndpointShieldSettings() : null;
        String existingUrl = existing != null ? existing.getManifestUrl() : null;
        boolean manifestUrlChanged = !endpointShieldSettings.getManifestUrl().equals(existingUrl);

        // When URL changes, reset cached version so stale version is not shown
        if (manifestUrlChanged) {
            endpointShieldSettings.setLatestVersion(null);
            endpointShieldSettings.setLatestVersionFetchedAt(0L);
        } else if (existing != null) {
            // Preserve cached version/timestamp fields not sent from frontend
            endpointShieldSettings.setLatestVersion(existing.getLatestVersion());
            endpointShieldSettings.setLatestVersionFetchedAt(existing.getLatestVersionFetchedAt());
        }

        AccountSettingsDao.instance.updateOne(
            AccountSettingsDao.generateFilter(),
            Updates.set(AccountSettings.ENDPOINT_SHIELD_SETTINGS, endpointShieldSettings)
        );
        return SUCCESS.toUpperCase();
    }

    // Called by the [Refresh] button - always force-fetches from manifest
    public String refreshLatestVersion() {
        AccountSettings account = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        EndpointShieldSettings existing = account != null ? account.getEndpointShieldSettings() : null;
        String manifestUrl = (existing != null && existing.getManifestUrl() != null)
            ? existing.getManifestUrl()
            : DEFAULT_MANIFEST_URL;

        boolean success = refreshFromManifest(manifestUrl);
        if (!success) {
            addActionError("Failed to fetch version from manifest URL. Please check the URL and try again.");
            return ERROR.toUpperCase();
        }
        endpointShieldSettings = AccountSettingsDao.instance
            .findOne(AccountSettingsDao.generateFilter())
            .getEndpointShieldSettings();
        return SUCCESS.toUpperCase();
    }

    // Returns true if version was successfully fetched and stored
    private boolean refreshFromManifest(String manifestUrl) {
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
                    Updates.set(FIELD_PREFIX + EndpointShieldSettings.LATEST_VERSION,            latest),
                    Updates.set(FIELD_PREFIX + EndpointShieldSettings.LATEST_VERSION_FETCHED_AT, System.currentTimeMillis())
                )
            );
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
