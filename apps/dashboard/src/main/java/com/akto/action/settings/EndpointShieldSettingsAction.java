package com.akto.action.settings;

import com.akto.action.UserAction;
import com.akto.dao.EndpointShieldSettingsDao;
import com.akto.dto.EndpointShieldSettings;
import com.mongodb.client.model.Updates;
import lombok.Getter;
import lombok.Setter;
import org.bson.conversions.Bson;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class EndpointShieldSettingsAction extends UserAction {

    private static final long MANIFEST_CACHE_TTL_MS = 60 * 60 * 1000L; // 1 hour
    private static final String DEFAULT_MANIFEST_URL =
        "https://akto-endpoint-agents.s3.us-east-1.amazonaws.com/atlas-installers/jamf-releases/latest.json";

    @Getter @Setter private EndpointShieldSettings endpointShieldSettings;
    @Getter @Setter private boolean stale;

    public String fetchEndpointShieldSettings() {
        endpointShieldSettings = EndpointShieldSettingsDao.instance.findOne(
            EndpointShieldSettingsDao.generateFilter());

        if (endpointShieldSettings == null) {
            endpointShieldSettings = new EndpointShieldSettings();
        }

        // Persist defaults on first use so subsequent manifest upserts attach to a real document
        if (endpointShieldSettings.getManifestUrl() == null) {
            endpointShieldSettings.setManifestUrl(DEFAULT_MANIFEST_URL);
            endpointShieldSettings.setAutoUpdateEnabled(true);
            EndpointShieldSettingsDao.instance.updateOne(
                EndpointShieldSettingsDao.generateFilter(),
                Updates.combine(
                    Updates.set(EndpointShieldSettings.MANIFEST_URL,        DEFAULT_MANIFEST_URL),
                    Updates.set(EndpointShieldSettings.AUTO_UPDATE_ENABLED, true)
                )
            );
        }

        long age = System.currentTimeMillis() - endpointShieldSettings.getLatestVersionFetchedAt();
        stale = age > MANIFEST_CACHE_TTL_MS;
        if (stale) {
            refreshFromManifest(endpointShieldSettings.getManifestUrl());
            EndpointShieldSettings refreshed = EndpointShieldSettingsDao.instance.findOne(
                EndpointShieldSettingsDao.generateFilter());
            if (refreshed != null) endpointShieldSettings = refreshed;
        }
        return SUCCESS.toUpperCase();
    }

    public String saveEndpointShieldSettings() {
        if (endpointShieldSettings == null) return ERROR.toUpperCase();

        EndpointShieldSettings existing = EndpointShieldSettingsDao.instance.findOne(
            EndpointShieldSettingsDao.generateFilter());

        boolean manifestUrlChanged = existing == null
            || !endpointShieldSettings.getManifestUrl().equals(existing.getManifestUrl());

        List<Bson> updates = new ArrayList<>();
        updates.add(Updates.set(EndpointShieldSettings.AUTO_UPDATE_ENABLED, endpointShieldSettings.isAutoUpdateEnabled()));
        updates.add(Updates.set(EndpointShieldSettings.MANIFEST_URL,        endpointShieldSettings.getManifestUrl()));
        updates.add(Updates.set(EndpointShieldSettings.TARGET_VERSION,      endpointShieldSettings.getTargetVersion()));

        // Clear cached version when URL changes so stale version is not shown
        if (manifestUrlChanged) {
            updates.add(Updates.set(EndpointShieldSettings.LATEST_VERSION,            null));
            updates.add(Updates.set(EndpointShieldSettings.LATEST_VERSION_FETCHED_AT, 0L));
        }

        EndpointShieldSettingsDao.instance.updateOne(
            EndpointShieldSettingsDao.generateFilter(),
            Updates.combine(updates)
        );
        return SUCCESS.toUpperCase();
    }

    // Called by the [Refresh] button - always force-fetches from manifest
    public String refreshLatestVersion() {
        EndpointShieldSettings settings = EndpointShieldSettingsDao.instance.findOne(
            EndpointShieldSettingsDao.generateFilter());
        String manifestUrl = (settings != null && settings.getManifestUrl() != null)
            ? settings.getManifestUrl()
            : DEFAULT_MANIFEST_URL;
        boolean success = refreshFromManifest(manifestUrl);
        if (!success) {
            addActionError("Failed to fetch version from manifest URL. Please check the URL and try again.");
            return ERROR.toUpperCase();
        }
        endpointShieldSettings = EndpointShieldSettingsDao.instance.findOne(
            EndpointShieldSettingsDao.generateFilter());
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

            EndpointShieldSettingsDao.instance.updateOne(
                EndpointShieldSettingsDao.generateFilter(),
                Updates.combine(
                    Updates.set(EndpointShieldSettings.LATEST_VERSION,            latest),
                    Updates.set(EndpointShieldSettings.LATEST_VERSION_FETCHED_AT, System.currentTimeMillis())
                )
            );
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
