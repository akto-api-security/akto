package com.akto.action.settings;

import com.akto.action.UserAction;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.AccountSettings;
import com.akto.dto.EndpointShieldSettings;
import com.akto.dto.PlatformShieldConfig;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.monitoring.ModuleInfo.ModuleType;
import com.akto.utils.ArgusCollectionScope;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import lombok.Getter;
import lombok.Setter;
import org.bson.conversions.Bson;
import org.json.JSONObject;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EndpointShieldSettingsAction extends UserAction {

    private static final long MANIFEST_CACHE_TTL_MS = 60 * 60 * 1000L; // 1 hour
    // Match ModuleInfoAction Endpoint Shield inactive threshold (post #6575/#6576).
    private static final long STALE_HEARTBEAT_SEC = 2L * 24 * 60 * 60;

    // Dot-notation prefix for nested fields: endpointShieldSettings.platforms.<platformKey>.<field>
    private static final String PLATFORMS_PREFIX =
        AccountSettings.ENDPOINT_SHIELD_SETTINGS + "." + EndpointShieldSettings.PLATFORMS + ".";

    private static final String S3_BUCKET = System.getenv().getOrDefault("AKTO_ENDPOINT_AGENTS_BUCKET", "akto-endpoint-agents");
    private static final String S3_REGION = System.getenv().getOrDefault("AKTO_ENDPOINT_AGENTS_REGION", "us-east-1");
    /** Local-only: point installer S3 feeds at another account (e.g. nginx-demo) without changing login account. */
    private static final String INSTALLER_ACCOUNT_ID_ENV = "AKTO_ENDPOINT_SHIELD_ACCOUNT_ID";

    private static final Pattern S3_HTTPS = Pattern.compile(
        "^https://([^.]+)\\.s3[.-][^/]+\\.amazonaws\\.com/(.+)$");

    /** S3 folder name under atlas-installers/<accountId>/ for each platform tab. */
    public static String platformFolder(String platformKey) {
        switch (platformKey) {
            case EndpointShieldSettings.PLATFORM_MACOS_MDM:
                return "macos-mdm";
            case EndpointShieldSettings.PLATFORM_WINDOWS_MDM:
                return "windows-mdm";
            case EndpointShieldSettings.PLATFORM_MACOS_DIRECT:
                return "macos-standalone";
            case EndpointShieldSettings.PLATFORM_WINDOWS_DIRECT:
                return "windows-standalone";
            default:
                return null;
        }
    }

    /**
     * Account id used in installer S3 paths.
     * Production: session {@link Context#accountId}.
     * Local: optional {@code AKTO_ENDPOINT_SHIELD_ACCOUNT_ID} (e.g. nginx-demo 1726615470).
     */
    static int installerAccountId() {
        String override = System.getenv(INSTALLER_ACCOUNT_ID_ENV);
        if (override != null && !override.trim().isEmpty()) {
            try {
                return Integer.parseInt(override.trim());
            } catch (NumberFormatException ignored) {
                // fall through to session account
            }
        }
        return Context.accountId.get();
    }

    private static boolean installerAccountOverrideActive() {
        String override = System.getenv(INSTALLER_ACCOUNT_ID_ENV);
        return override != null && !override.trim().isEmpty();
    }

    /**
     * Default layout: atlas-installers/&lt;accountId&gt;/&lt;type&gt;/.
     * Set AKTO_ENDPOINT_SHIELD_LEGACY_S3_LAYOUT=true only for the old
     * atlas-installers/&lt;type&gt;/&lt;accountId&gt;/ feeds.
     */
    private static boolean useLegacyInstallerS3Layout() {
        String v = System.getenv("AKTO_ENDPOINT_SHIELD_LEGACY_S3_LAYOUT");
        return v != null && ("1".equals(v.trim()) || "true".equalsIgnoreCase(v.trim()));
    }

    private static String defaultInstallerPrefix(String platformKey) {
        String folder = platformFolder(platformKey);
        if (folder == null) return null;
        if (useLegacyInstallerS3Layout()) {
            return "atlas-installers/" + folder + "/" + installerAccountId();
        }
        return "atlas-installers/" + installerAccountId() + "/" + folder;
    }

    // Account-scoped feeds (new or legacy layout depending on local env).
    private static String defaultManifestUrl(String platformKey) {
        String prefix = defaultInstallerPrefix(platformKey);
        if (prefix == null) return null;
        return "https://" + S3_BUCKET + ".s3." + S3_REGION + ".amazonaws.com/" + prefix + "/latest.json";
    }

    @Getter @Setter private EndpointShieldSettings endpointShieldSettings;
    @Getter @Setter private String platformKey;
    @Getter @Setter private PlatformShieldConfig platformConfig;
    @Getter @Setter private String version;
    @Getter @Setter private List<Map<String, Object>> releases;
    @Getter @Setter private Map<String, Object> versionControl;
    @Getter @Setter private String newestPublishedVersion;
    @Getter @Setter private String targetVersionLive;

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

        // Seed defaults for any platform missing or lacking a manifest URL.
        // When AKTO_ENDPOINT_SHIELD_ACCOUNT_ID is set (local), remap URLs to that account's feeds.
        boolean remapToInstallerAccount = installerAccountOverrideActive();
        for (String key : EndpointShieldSettings.ALL_PLATFORMS) {
            PlatformShieldConfig existing_cfg = platforms.get(key);
            boolean missingUrl = existing_cfg == null || existing_cfg.getManifestUrl() == null || existing_cfg.getManifestUrl().isEmpty();
            String desiredUrl = defaultManifestUrl(key);
            boolean shouldSet = missingUrl || (remapToInstallerAccount && desiredUrl != null
                && (existing_cfg == null || !desiredUrl.equals(existing_cfg.getManifestUrl())));
            if (shouldSet) {
                if (existing_cfg == null) {
                    existing_cfg = new PlatformShieldConfig();
                    existing_cfg.setAutoUpdateEnabled(true);
                    platforms.put(key, existing_cfg);
                }
                existing_cfg.setManifestUrl(desiredUrl);
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

    /**
     * Lists published releases under atlas-installers/&lt;accountId&gt;/&lt;type&gt;/releases/
     * and returns Target / Newest / Fleet visibility for the platform tab.
     */
    public String listEndpointShieldReleases() {
        if (platformKey == null || !EndpointShieldSettings.ALL_PLATFORMS.contains(platformKey)) {
            addActionError("Invalid platformKey");
            return ERROR.toUpperCase();
        }

        String manifestUrl = resolveManifestUrl(platformKey);
        if (manifestUrl == null) {
            addActionError("No manifest URL configured for platform: " + platformKey);
            return ERROR.toUpperCase();
        }

        String prefix = prefixFromManifestUrl(manifestUrl);
        if (prefix == null) {
            prefix = defaultInstallerPrefix(platformKey);
        }

        String previousVersion = null;
        try (S3Client s3 = s3Client()) {
            releases = collectReleases(s3, prefix);
        } catch (Exception e) {
            addActionError("Failed to list releases from S3: " + e.getMessage());
            return ERROR.toUpperCase();
        }

        newestPublishedVersion = newestVersionOf(releases);

        JSONObject live = fetchManifestJson(manifestUrl);
        targetVersionLive = live != null ? live.optString("version", null) : null;
        previousVersion = previousVersionOf(live);
        if (targetVersionLive != null && !targetVersionLive.isEmpty()) {
            refreshPlatformFromManifest(platformKey, manifestUrl);
        }

        versionControl = buildVersionControl(platformKey, targetVersionLive, newestPublishedVersion);
        versionControl.put("previousVersion", previousVersion);

        endpointShieldSettings = AccountSettingsDao.instance
            .findOne(AccountSettingsDao.generateFilter())
            .getEndpointShieldSettings();
        return SUCCESS.toUpperCase();
    }

    /**
     * Rewrites the platform's latest.json to point at a published release under releases/&lt;version&gt;/.
     */
    public String deployEndpointShieldVersion() {
        if (platformKey == null || !EndpointShieldSettings.ALL_PLATFORMS.contains(platformKey)) {
            addActionError("Invalid platformKey");
            return ERROR.toUpperCase();
        }
        if (version == null || version.trim().isEmpty()) {
            addActionError("version is required");
            return ERROR.toUpperCase();
        }
        String deployVersion = version.trim();

        String manifestUrl = resolveManifestUrl(platformKey);
        if (manifestUrl == null) {
            addActionError("No manifest URL configured");
            return ERROR.toUpperCase();
        }
        String prefix = prefixFromManifestUrl(manifestUrl);
        if (prefix == null) {
            prefix = defaultInstallerPrefix(platformKey);
        }
        prefix = trimSlash(prefix);
        String replacedVersion = null;

        try (S3Client s3 = s3Client()) {
            JSONObject releaseMeta = readReleaseJson(s3, prefix + "/releases/" + deployVersion + "/release.json");
            if (releaseMeta == null) {
                releaseMeta = synthesizeReleaseJson(s3, prefix, deployVersion, platformKey);
            }
            if (releaseMeta == null) {
                addActionError("No release artifacts found for version: " + deployVersion);
                return ERROR.toUpperCase();
            }

            JSONObject current = fetchManifestJson(manifestUrl);
            if (current != null && current.has("version")) {
                replacedVersion = emptyToNull(current.optString("version", ""));
                JSONObject previous = new JSONObject();
                previous.put("version", current.optString("version", ""));
                String art = firstArtifactUrl(current);
                if (art != null) previous.put(artifactField(platformKey), art);
                if (current.has("sha256")) previous.put("sha256", current.optString("sha256", ""));
                releaseMeta.put("previous", previous);
            }
            releases = collectReleases(s3, prefix);
            newestPublishedVersion = newestVersionOf(releases);
            if (newestPublishedVersion == null) {
                newestPublishedVersion = deployVersion;
            }
            releaseMeta.put("newest", newestPublishedVersion);
            releaseMeta.put("version", releaseMeta.optString("version", deployVersion));
            if (!releaseMeta.has("customer")) {
                releaseMeta.put("customer", String.valueOf(installerAccountId()));
            }
            if (!releaseMeta.has("released_at")) {
                releaseMeta.put("released_at", Instant.now().toString().replaceAll("\\.\\d+Z$", "Z"));
            }

            String body = releaseMeta.toString(2) + "\n";
            String key = prefix + "/latest.json";
            RequestBody requestBody = RequestBody.fromString(body, StandardCharsets.UTF_8);
            try {
                s3.putObject(
                    PutObjectRequest.builder()
                        .bucket(S3_BUCKET)
                        .key(key)
                        .contentType("application/json")
                        .acl(ObjectCannedACL.PUBLIC_READ)
                        .build(),
                    requestBody
                );
            } catch (Exception aclErr) {
                // Bucket may enforce owner-controlled ACLs; retry without canned ACL.
                s3.putObject(
                    PutObjectRequest.builder()
                        .bucket(S3_BUCKET)
                        .key(key)
                        .contentType("application/json")
                        .build(),
                    RequestBody.fromString(body, StandardCharsets.UTF_8)
                );
            }

            AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.combine(
                    Updates.set(PLATFORMS_PREFIX + platformKey + "." + PlatformShieldConfig.TARGET_VERSION, deployVersion),
                    Updates.set(PLATFORMS_PREFIX + platformKey + "." + PlatformShieldConfig.LATEST_VERSION, deployVersion),
                    Updates.set(PLATFORMS_PREFIX + platformKey + "." + PlatformShieldConfig.LATEST_VERSION_FETCHED_AT, System.currentTimeMillis())
                )
            );
        } catch (Exception e) {
            addActionError("Failed to deploy version: " + e.getMessage());
            return ERROR.toUpperCase();
        }

        targetVersionLive = deployVersion;
        versionControl = buildVersionControl(platformKey, deployVersion, newestPublishedVersion);
        versionControl.put("previousVersion", replacedVersion);
        endpointShieldSettings = AccountSettingsDao.instance
            .findOne(AccountSettingsDao.generateFilter())
            .getEndpointShieldSettings();
        return SUCCESS.toUpperCase();
    }

    private static final class ReleaseFolder {
        String artifactKey;
        Instant artifactModified;
        boolean hasReleaseJson;
    }

    /** Releases ordered by publish time, newest first. Time is release.json released_at, else artifact LastModified. */
    private List<Map<String, Object>> collectReleases(S3Client s3, String prefix) {
        String releasesPrefix = trimSlash(prefix) + "/releases/";
        Map<String, ReleaseFolder> folders = new LinkedHashMap<>();
        ListObjectsV2Request req = ListObjectsV2Request.builder()
            .bucket(S3_BUCKET)
            .prefix(releasesPrefix)
            .build();
        ListObjectsV2Response resp;
        do {
            resp = s3.listObjectsV2(req);
            for (S3Object obj : resp.contents()) {
                noteReleaseObject(folders, releasesPrefix, obj);
            }
            req = req.toBuilder().continuationToken(resp.nextContinuationToken()).build();
        } while (Boolean.TRUE.equals(resp.isTruncated()));

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, ReleaseFolder> entry : folders.entrySet()) {
            out.add(toReleaseEntry(s3, releasesPrefix, entry.getKey(), entry.getValue()));
        }
        out.sort(EndpointShieldSettingsAction::compareByReleasedAtDesc);
        return out;
    }

    private static void noteReleaseObject(Map<String, ReleaseFolder> folders, String releasesPrefix, S3Object obj) {
        String key = obj.key();
        if (key == null || !key.startsWith(releasesPrefix)) return;
        String rest = key.substring(releasesPrefix.length());
        int slash = rest.indexOf('/');
        if (slash <= 0) return;
        String ver = rest.substring(0, slash);
        String leaf = rest.substring(slash + 1);
        if (ver.isEmpty() || leaf.isEmpty() || leaf.indexOf('/') >= 0) return;

        ReleaseFolder folder = folders.computeIfAbsent(ver, ignored -> new ReleaseFolder());
        if ("release.json".equals(leaf)) {
            folder.hasReleaseJson = true;
            return;
        }
        if (!isArtifactLeaf(leaf)) return;
        Instant modified = obj.lastModified();
        if (folder.artifactKey == null
                || (modified != null && (folder.artifactModified == null || modified.isAfter(folder.artifactModified)))) {
            folder.artifactKey = key;
            folder.artifactModified = modified;
        }
    }

    private Map<String, Object> toReleaseEntry(S3Client s3, String releasesPrefix, String version, ReleaseFolder folder) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("version", version);
        String releasedAt = null;
        if (folder.hasReleaseJson) {
            JSONObject releaseMeta = readReleaseJson(s3, releasesPrefix + version + "/release.json");
            if (releaseMeta != null) {
                entry.put("sha256", emptyToNull(releaseMeta.optString("sha256", "")));
                entry.put("artifactUrl", firstArtifactUrl(releaseMeta));
                releasedAt = emptyToNull(releaseMeta.optString("released_at", ""));
            }
        }
        if ((releasedAt == null || parseReleasedAt(releasedAt) == null) && folder.artifactModified != null) {
            releasedAt = folder.artifactModified.toString();
        }
        if (entry.get("artifactUrl") == null && folder.artifactKey != null) {
            entry.put("artifactUrl", "https://" + S3_BUCKET + ".s3." + S3_REGION + ".amazonaws.com/" + folder.artifactKey);
        }
        entry.put("releasedAt", releasedAt);
        return entry;
    }

    private static int compareByReleasedAtDesc(Map<String, Object> a, Map<String, Object> b) {
        Instant ia = parseReleasedAt(a.get("releasedAt"));
        Instant ib = parseReleasedAt(b.get("releasedAt"));
        if (ia == null && ib == null) return 0;
        if (ia == null) return 1;
        if (ib == null) return -1;
        return ib.compareTo(ia);
    }

    private static Instant parseReleasedAt(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "null".equals(text)) return null;
        try {
            return Instant.parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    private static String newestVersionOf(List<Map<String, Object>> catalog) {
        if (catalog == null || catalog.isEmpty()) return null;
        Object version = catalog.get(0).get("version");
        return version == null ? null : String.valueOf(version);
    }

    private static String previousVersionOf(JSONObject live) {
        if (live == null) return null;
        JSONObject previous = live.optJSONObject("previous");
        if (previous == null) return null;
        return emptyToNull(previous.optString("version", ""));
    }

    private static String emptyToNull(String value) {
        if (value == null || value.isEmpty()) return null;
        return value;
    }

    private static boolean isArtifactLeaf(String leaf) {
        String lower = leaf.toLowerCase(Locale.ROOT);
        return lower.endsWith(".exe") || lower.endsWith(".zip") || lower.endsWith(".pkg");
    }

    private Map<String, Object> buildVersionControl(String key, String target, String newest) {
        Map<String, Object> vc = new LinkedHashMap<>();
        vc.put("targetVersion", target);
        vc.put("newestPublishedVersion", newest);
        vc.put("pinnedToOlder", target != null && newest != null && !target.equals(newest));

        Map<String, Integer> fleetCounts = new LinkedHashMap<>();
        fleetCounts.put("onTarget", 0);
        fleetCounts.put("behind", 0);
        fleetCounts.put("ahead", 0);
        fleetCounts.put("staleHeartbeat", 0);
        fleetCounts.put("unknown", 0);
        fleetCounts.put("total", 0);

        Map<String, Integer> byVersion = new LinkedHashMap<>();
        // Argus collection-scoped users cannot attribute devices — same empty set as ModuleInfoAction.
        if (ArgusCollectionScope.isLimited(getSUser())) {
            vc.put("fleetCounts", fleetCounts);
            vc.put("fleetByVersion", byVersion);
            return vc;
        }

        String osFilter = key.startsWith("windows") ? "windows" : "mac";
        int now = Context.now();

        Bson filter = Filters.eq(ModuleInfo.MODULE_TYPE, ModuleType.MCP_ENDPOINT_SHIELD.toString());
        Bson projection = Projections.include(
            ModuleInfo.NAME,
            ModuleInfo.CURRENT_VERSION,
            ModuleInfo.LAST_HEARTBEAT_RECEIVED,
            ModuleInfo.ADDITIONAL_DATA + ".os"
        );
        // Dedupe by {name, os} keeping the freshest heartbeat — mirrors ModuleInfoAction #6575/#6576.
        Map<String, ModuleInfo> deduped = new LinkedHashMap<>();
        for (ModuleInfo m : ModuleInfoDao.instance.findAll(filter, projection)) {
            String os = null;
            if (m.getAdditionalData() != null && m.getAdditionalData().get("os") != null) {
                os = String.valueOf(m.getAdditionalData().get("os")).toLowerCase(Locale.ROOT);
            }
            if (os != null) {
                boolean isWin = os.contains("win");
                boolean isMac = os.contains("mac") || os.contains("darwin");
                if (osFilter.equals("windows") && !isWin) continue;
                if (osFilter.equals("mac") && !isMac) continue;
            }
            String host = m.getName() == null ? "" : m.getName();
            String dedupeKey = host + "\0" + (os == null ? "" : os);
            ModuleInfo prev = deduped.get(dedupeKey);
            if (prev == null || m.getLastHeartbeatReceived() >= prev.getLastHeartbeatReceived()) {
                deduped.put(dedupeKey, m);
            }
        }

        for (ModuleInfo m : deduped.values()) {
            fleetCounts.put("total", fleetCounts.get("total") + 1);
            String cv = m.getCurrentVersion();
            if (cv == null || cv.isEmpty()) {
                fleetCounts.put("unknown", fleetCounts.get("unknown") + 1);
            } else {
                byVersion.merge(cv, 1, Integer::sum);
                if (target != null && !target.isEmpty()) {
                    int cmp = compareLooseVersions(cv, target);
                    if (cmp == 0) fleetCounts.put("onTarget", fleetCounts.get("onTarget") + 1);
                    else if (cmp < 0) fleetCounts.put("behind", fleetCounts.get("behind") + 1);
                    else fleetCounts.put("ahead", fleetCounts.get("ahead") + 1);
                } else {
                    fleetCounts.put("unknown", fleetCounts.get("unknown") + 1);
                }
            }
            if (m.getLastHeartbeatReceived() <= 0
                    || (now - m.getLastHeartbeatReceived()) > STALE_HEARTBEAT_SEC) {
                fleetCounts.put("staleHeartbeat", fleetCounts.get("staleHeartbeat") + 1);
            }
        }

        vc.put("fleetCounts", fleetCounts);
        vc.put("fleetByVersion", byVersion);
        return vc;
    }

    private static int compareLooseVersions(String a, String b) {
        if (a == null || b == null) return 0;
        if (a.equals(b)) return 0;
        String[] as = a.replaceAll("[^0-9A-Za-z._-]", "").split("[._-]");
        String[] bs = b.replaceAll("[^0-9A-Za-z._-]", "").split("[._-]");
        int n = Math.max(as.length, bs.length);
        for (int i = 0; i < n; i++) {
            String x = i < as.length ? as[i] : "0";
            String y = i < bs.length ? bs[i] : "0";
            boolean xn = x.matches("\\d+");
            boolean yn = y.matches("\\d+");
            if (xn && yn) {
                int c = Integer.compare(Integer.parseInt(x), Integer.parseInt(y));
                if (c != 0) return c;
            } else {
                int c = x.compareToIgnoreCase(y);
                if (c != 0) return c;
            }
        }
        return 0;
    }

    private String resolveManifestUrl(String key) {
        AccountSettings account = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        EndpointShieldSettings existing = account != null ? account.getEndpointShieldSettings() : null;
        PlatformShieldConfig cfg = (existing != null && existing.getPlatforms() != null)
            ? existing.getPlatforms().get(key) : null;
        if (cfg != null && cfg.getManifestUrl() != null && !cfg.getManifestUrl().isEmpty()) {
            return cfg.getManifestUrl();
        }
        return defaultManifestUrl(key);
    }

    private static String prefixFromManifestUrl(String manifestUrl) {
        if (manifestUrl == null) return null;
        Matcher m = S3_HTTPS.matcher(manifestUrl.trim());
        if (!m.matches()) return null;
        String key = m.group(2);
        if (key.endsWith("/latest.json")) {
            return key.substring(0, key.length() - "/latest.json".length());
        }
        if (key.endsWith("latest.json")) {
            return key.substring(0, key.length() - "latest.json".length()).replaceAll("/$", "");
        }
        return null;
    }

    private static String trimSlash(String s) {
        if (s == null) return "";
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static S3Client s3Client() {
        return S3Client.builder()
            .region(Region.of(S3_REGION))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }

    private static JSONObject readReleaseJson(S3Client s3, String key) {
        try {
            String body = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(S3_BUCKET).key(key).build())
                .asString(StandardCharsets.UTF_8);
            return new JSONObject(body);
        } catch (Exception e) {
            return null;
        }
    }

    private static String findArtifactKey(S3Client s3, String prefix) {
        ListObjectsV2Response resp = s3.listObjectsV2(ListObjectsV2Request.builder()
            .bucket(S3_BUCKET).prefix(prefix).maxKeys(50).build());
        for (S3Object obj : resp.contents()) {
            String k = obj.key();
            String leaf = k.substring(k.lastIndexOf('/') + 1);
            if (isArtifactLeaf(leaf)) {
                return k;
            }
        }
        return null;
    }

    private JSONObject synthesizeReleaseJson(S3Client s3, String prefix, String ver, String key) {
        String folder = prefix + "/releases/" + ver + "/";
        String artifactKey = findArtifactKey(s3, folder);
        if (artifactKey == null) return null;
        String url = "https://" + S3_BUCKET + ".s3." + S3_REGION + ".amazonaws.com/" + artifactKey;
        JSONObject o = new JSONObject();
        o.put("version", ver);
        o.put(artifactField(key), url);
        o.put("customer", String.valueOf(installerAccountId()));
        return o;
    }

    private static String artifactField(String platformKey) {
        if (EndpointShieldSettings.PLATFORM_WINDOWS_DIRECT.equals(platformKey)) return "setup_url";
        if (EndpointShieldSettings.PLATFORM_WINDOWS_MDM.equals(platformKey)) return "zip_url";
        return "pkg_url";
    }

    private static String firstArtifactUrl(JSONObject o) {
        if (o == null) return null;
        for (String f : new String[]{"setup_url", "zip_url", "pkg_url"}) {
            if (o.has(f) && !o.optString(f).isEmpty()) return o.optString(f);
        }
        return null;
    }

    private static JSONObject fetchManifestJson(String manifestUrl) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(manifestUrl).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }
            return new JSONObject(sb.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private boolean refreshPlatformFromManifest(String key, String manifestUrl) {
        try {
            JSONObject json = fetchManifestJson(manifestUrl);
            if (json == null) return false;
            String latest = json.optString("version", null);
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
