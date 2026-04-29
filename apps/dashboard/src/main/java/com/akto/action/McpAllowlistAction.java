package com.akto.action;

import com.akto.dao.McpAllowlistDao;
import com.akto.dao.McpRegistryConfigDao;
import com.akto.dao.context.Context;
import com.akto.dto.McpAllowlist;
import com.akto.dto.McpRegistryConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.opensymphony.xwork2.Action;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.util.concurrent.TimeUnit;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.bson.types.ObjectId;

import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class McpAllowlistAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(McpAllowlistAction.class, LogDb.DASHBOARD);
    private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private String registryUrl;
    private Map<String, String> headers;
    private String registryId;
    private McpRegistryConfig.RegistryType registryType;

    public String addRegistry() {
        if (registryUrl == null || registryUrl.trim().isEmpty()) {
            addActionError("registryUrl is required");
            return Action.ERROR.toUpperCase();
        }

        if (McpRegistryConfigDao.instance.findOne(Filters.eq(McpRegistryConfig.REGISTRY_TYPE, McpRegistryConfig.RegistryType.CSV_URL)) != null) {
            addActionError("A CSV_URL registry already exists");
            return Action.ERROR.toUpperCase();
        }

        int now = Context.now();
        String hash = sha256Hex(registryUrl.trim());
        McpRegistryConfig config = new McpRegistryConfig(registryUrl.trim(), headers, hash, now, now, registryType);
        String insertedId = McpRegistryConfigDao.instance.insertOne(config)
                .getInsertedId().asObjectId().getValue().toHexString();

        loggerMaker.infoAndAddToDb("MCP registry added: " + registryUrl + " id=" + insertedId);

        registryId = insertedId;
        return syncRegistryInternal(insertedId);
    }

    public String syncRegistry() {
        if (registryId == null || registryId.trim().isEmpty()) {
            addActionError("registryId is required");
            return Action.ERROR.toUpperCase();
        }
        return syncRegistryInternal(registryId.trim());
    }

    private String syncRegistryInternal(String id) {
        McpRegistryConfig config = McpRegistryConfigDao.instance.findOne(
                Filters.eq("_id", new ObjectId(id)));
        if (config == null) {
            loggerMaker.errorAndAddToDb("MCP registry not found for id=" + id);
            addActionError("Registry not found");
            return Action.ERROR.toUpperCase();
        }

        loggerMaker.infoAndAddToDb("Syncing MCP registry id=" + id + " url=" + config.getUrl());

        String csvBody;
        try {
            Request.Builder rb = new Request.Builder().url(config.getUrl()).get();
            if (config.getHeaders() != null) {
                config.getHeaders().forEach((k, v) -> rb.addHeader(k, v));
            }
            try (Response response = httpClient.newCall(rb.build()).execute()) {
                if (!response.isSuccessful()) {
                    String errBody = response.body() != null ? response.body().string() : "null";
                    loggerMaker.errorAndAddToDb("Failed to fetch registry URL=" + config.getUrl() + " HTTP=" + response.code() + " body=" + errBody);
                    addActionError("Failed to fetch registry URL: HTTP " + response.code());
                    return Action.ERROR.toUpperCase();
                }
                csvBody = response.body().string();
                loggerMaker.infoAndAddToDb("Fetched CSV from registry id=" + id + " size=" + csvBody.length());
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Exception fetching registry URL=" + config.getUrl() + " error=" + e.getMessage());
            addActionError("Failed to fetch registry URL: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }

        String addedBy = getSUser().getLogin();
        int now = Context.now();
        List<McpAllowlist> entries = new ArrayList<>();

        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build()
                .parse(new StringReader(csvBody))) {
            for (CSVRecord record : parser.getRecords()) {
                if (record.size() == 0) continue;
                String entryUrl = record.get("mcp_server_name").trim();
                if (entryUrl.isEmpty()) continue;
                String name = extractHost(entryUrl);
                loggerMaker.infoAndAddToDb("Parsed MCP entry url=" + entryUrl + " name=" + name);
                entries.add(new McpAllowlist(name, entryUrl, id, addedBy, now, false, McpAllowlist.Source.REGISTRY));
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Failed to parse CSV for registry id=" + id + " error=" + e.getMessage());
            addActionError("Failed to parse CSV: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }

        McpAllowlistDao.instance.getMCollection().deleteMany(
                Filters.and(
                        Filters.eq(McpAllowlist.REGISTRY_ID, id),
                        Filters.ne(McpAllowlist.MANUALLY_ADDED, true)
                ));

        if (!entries.isEmpty()) {
            List<WriteModel<McpAllowlist>> bulkOps = new ArrayList<>();
            for (McpAllowlist entry : entries) {
                bulkOps.add(new UpdateOneModel<>(
                        Filters.and(Filters.eq(McpAllowlist.NAME, entry.getName()), Filters.eq(McpAllowlist.REGISTRY_ID, entry.getRegistryId())),
                        Updates.combine(
                                Updates.setOnInsert(McpAllowlist.URL, entry.getUrl()),
                                Updates.setOnInsert(McpAllowlist.REGISTRY_ID, entry.getRegistryId()),
                                Updates.setOnInsert(McpAllowlist.ADDED_BY, entry.getAddedBy()),
                                Updates.setOnInsert(McpAllowlist.MANUALLY_ADDED, false),
                                Updates.setOnInsert(McpAllowlist.CREATED_AT, entry.getCreatedAt()),
                                Updates.setOnInsert(McpAllowlist.SOURCE, McpAllowlist.Source.REGISTRY.name())
                        ),
                        new UpdateOptions().upsert(true)
                ));
            }
            McpAllowlistDao.instance.getMCollection().bulkWrite(bulkOps);
        }
        loggerMaker.infoAndAddToDb("Synced " + entries.size() + " MCP allowlist entries for registry id=" + id);

        McpRegistryConfigDao.instance.updateOneNoUpsert(
                Filters.eq("_id", new ObjectId(id)),
                Updates.set(McpRegistryConfig.UPDATED_AT, now));

        return Action.SUCCESS.toUpperCase();
    }

    private String extractHost(String rawUrl) {
        try {
            return new URL(rawUrl).getHost();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Failed to extract name from url=" + rawUrl + " error=" + e.getMessage());
            return rawUrl;
        }
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Failed to compute SHA-256 hash error=" + e.getMessage());
            return input;
        }
    }

    private List<McpRegistryConfig> mcpRegistries;
    private List<McpAllowlist> mcpAllowlistEntries;

    public String fetchRegistries() {
        mcpRegistries = McpRegistryConfigDao.instance.findAll(new BasicDBObject());
        return Action.SUCCESS.toUpperCase();
    }

    private List<String> mcpServerUrls;

    public String addEntry() {
        Set<String> urlSet = new LinkedHashSet<>();
        if (mcpServerUrls != null) {
            for (String u : mcpServerUrls) {
                if (u != null && !u.trim().isEmpty()) {
                    urlSet.add(u.trim());
                }
            }
        }
        if (urlSet.isEmpty()) {
            loggerMaker.errorAndAddToDb("addEntry called with empty mcpServerUrls");
            addActionError("mcpServerUrls is required");
            return Action.ERROR.toUpperCase();
        }

        McpRegistryConfig csvRegistry = McpRegistryConfigDao.instance.findOne(
                Filters.eq(McpRegistryConfig.REGISTRY_TYPE, McpRegistryConfig.RegistryType.CSV_URL));
        if (csvRegistry == null) {
            loggerMaker.errorAndAddToDb("addEntry failed: no CSV_URL registry configured");
            addActionError("No CSV_URL registry configured");
            return Action.ERROR.toUpperCase();
        }

        String regId = csvRegistry.getHexId();
        String addedBy = getSUser().getLogin();
        int now = Context.now();

        List<WriteModel<McpAllowlist>> bulkOps = new ArrayList<>();
        for (String entryUrl : urlSet) {
            String name = extractHost(entryUrl);
            bulkOps.add(new UpdateOneModel<>(
                    Filters.and(Filters.eq(McpAllowlist.NAME, name), Filters.eq(McpAllowlist.REGISTRY_ID, regId)),
                    Updates.combine(
                            Updates.setOnInsert(McpAllowlist.URL, entryUrl),
                            Updates.setOnInsert(McpAllowlist.REGISTRY_ID, regId),
                            Updates.setOnInsert(McpAllowlist.ADDED_BY, addedBy),
                            Updates.setOnInsert(McpAllowlist.MANUALLY_ADDED, true),
                            Updates.setOnInsert(McpAllowlist.CREATED_AT, now),
                            Updates.setOnInsert(McpAllowlist.SOURCE, McpAllowlist.Source.AUDIT_DATA.name())
                    ),
                    new UpdateOptions().upsert(true)
            ));
        }
        if (!bulkOps.isEmpty()) {
            McpAllowlistDao.instance.getMCollection().bulkWrite(bulkOps);
        }
        loggerMaker.infoAndAddToDb("Upserted " + urlSet.size() + " MCP allowlist entries registryId=" + regId);

        return Action.SUCCESS.toUpperCase();
    }

    public String deleteRegistry() {
        if (registryId == null || registryId.trim().isEmpty()) {
            addActionError("registryId is required");
            return Action.ERROR.toUpperCase();
        }
        String id = registryId.trim();
        McpAllowlistDao.instance.getMCollection().deleteMany(Filters.eq(McpAllowlist.REGISTRY_ID, id));
        McpRegistryConfigDao.instance.getMCollection().deleteOne(Filters.eq("_id", new ObjectId(id)));
        loggerMaker.infoAndAddToDb("Deleted MCP registry id=" + id + " and all associated allowlist entries");
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchEntries() {
        if (registryId == null || registryId.trim().isEmpty()) {
            addActionError("registryId is required");
            return Action.ERROR.toUpperCase();
        }
        mcpAllowlistEntries = McpAllowlistDao.instance.findAll(
                Filters.eq(McpAllowlist.REGISTRY_ID, registryId.trim()));
        return Action.SUCCESS.toUpperCase();
    }

    public String getRegistryUrl() { return registryUrl; }
    public void setRegistryUrl(String registryUrl) { this.registryUrl = registryUrl; }

    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }

    public String getRegistryId() { return registryId; }
    public void setRegistryId(String registryId) { this.registryId = registryId; }

    public McpRegistryConfig.RegistryType getRegistryType() { return registryType; }
    public void setRegistryType(McpRegistryConfig.RegistryType registryType) { this.registryType = registryType; }

    public List<String> getMcpServerUrls() { return mcpServerUrls; }
    public void setMcpServerUrls(List<String> mcpServerUrls) { this.mcpServerUrls = mcpServerUrls; }

    public List<McpRegistryConfig> getMcpRegistries() { return mcpRegistries; }
    public List<McpAllowlist> getMcpAllowlistEntries() { return mcpAllowlistEntries; }
}
