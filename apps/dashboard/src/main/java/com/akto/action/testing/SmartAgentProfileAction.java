package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.testing.SmartAgentProfileDao;
import com.akto.dto.User;
import com.akto.dto.testing.SmartAgentConfig;
import com.akto.dto.testing.SmartAgentKnowledgeFile;
import com.akto.dto.testing.SmartAgentProfile;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.blob.AzureBlobClient;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Smart agents (Testing > Smart agents). A run uses one when it is chosen in the Run test modal (api/startTest). */
public class SmartAgentProfileAction extends UserAction {

    static final int MAX_NAME_CHARS = 80;
    static final int MAX_DESCRIPTION_CHARS = 500;
    private static final Pattern SHA256 = Pattern.compile("^[a-f0-9]{64}$");
    private static final Pattern SKILL_ID = Pattern.compile("^[A-Z0-9_]{2,40}$");
    private static final LoggerMaker loggerMaker = new LoggerMaker(SmartAgentProfileAction.class, LogDb.DASHBOARD);

    // request
    private String hexId;
    private String name;
    private String description;
    private List<String> skills;
    private List<Map<String, Object>> customSkills;
    private boolean allowCreateIdentities;
    private String instructions;
    private List<Map<String, Object>> knowledgeFiles;
    private List<String> knowledgeLinks;

    // response
    private List<SmartAgentProfile> smartAgentProfiles;
    private SmartAgentProfile smartAgentProfile;

    public static SmartAgentProfile findProfile(String hexId) {
        if (!ObjectId.isValid(StringUtils.trimToEmpty(hexId))) {
            return null;
        }
        return SmartAgentProfileDao.instance.findOne(Filters.eq(SmartAgentProfile.ID, new ObjectId(hexId.trim())));
    }

    public String fetchSmartAgentProfiles() {
        smartAgentProfiles = SmartAgentProfileDao.instance.findAll(new BasicDBObject(), 0, 500, Sorts.descending(SmartAgentProfile.LAST_UPDATED_TS));
        return SUCCESS.toUpperCase();
    }

    public String fetchSmartAgentProfile() {
        smartAgentProfile = findProfile(hexId);
        if (smartAgentProfile == null) {
            addActionError("Smart agent not found");
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public String saveSmartAgentProfile() {
        SmartAgentProfile input = new SmartAgentProfile();
        String problem = fill(input);
        if (problem != null) {
            addActionError(problem);
            return ERROR.toUpperCase();
        }
        SmartAgentProfile existing = StringUtils.isBlank(hexId) ? null : findProfile(hexId);
        if (StringUtils.isNotBlank(hexId) && existing == null) {
            addActionError("Smart agent not found");
            return ERROR.toUpperCase();
        }
        Bson sameName = Filters.eq(SmartAgentProfile.NAME, input.getName());
        if (existing != null) {
            sameName = Filters.and(sameName, Filters.ne(SmartAgentProfile.ID, existing.getId()));
        }
        if (SmartAgentProfileDao.instance.findOne(sameName) != null) {
            addActionError("A smart agent named " + input.getName() + " already exists");
            return ERROR.toUpperCase();
        }
        User user = getSUser();
        int now = Context.now();
        if (existing == null) {
            input.setCreatedBy(user.getLogin());
            input.setCreatedTs(now);
            input.setLastUpdatedBy(user.getLogin());
            input.setLastUpdatedTs(now);
            SmartAgentProfileDao.instance.insertOne(input);
            smartAgentProfile = SmartAgentProfileDao.instance.findOne(Filters.eq(SmartAgentProfile.NAME, input.getName()));
        } else {
            SmartAgentProfileDao.instance.updateOne(Filters.eq(SmartAgentProfile.ID, existing.getId()), Updates.combine(
                    Updates.set(SmartAgentProfile.NAME, input.getName()),
                    Updates.set("description", input.getDescription()),
                    Updates.set("skills", input.getSkills()),
                    Updates.set("customSkills", input.getCustomSkills()),
                    Updates.set("allowCreateIdentities", input.getAllowCreateIdentities()),
                    Updates.set("instructions", input.getInstructions()),
                    Updates.set("knowledgeFiles", input.getKnowledgeFiles()),
                    Updates.set("knowledgeLinks", input.getKnowledgeLinks()),
                    Updates.set(SmartAgentProfile.LAST_UPDATED_BY, user.getLogin()),
                    Updates.set(SmartAgentProfile.LAST_UPDATED_TS, now)));
            smartAgentProfile = findProfile(existing.getId().toHexString());
        }
        return SUCCESS.toUpperCase();
    }

    public String deleteSmartAgentProfile() {
        SmartAgentProfile existing = findProfile(hexId);
        if (existing == null) {
            addActionError("Smart agent not found");
            return ERROR.toUpperCase();
        }
        SmartAgentProfileDao.instance.deleteAll(Filters.eq(SmartAgentProfile.ID, existing.getId()));
        return SUCCESS.toUpperCase();
    }

    /** Validates the request into the profile; the problem to show the user, or null. */
    String fill(SmartAgentProfile profile) {
        String cleanName = StringUtils.trimToEmpty(name);
        if (cleanName.isEmpty()) return "Name is required";
        if (cleanName.length() > MAX_NAME_CHARS) return "Name can be at most " + MAX_NAME_CHARS + " characters";
        profile.setName(cleanName);
        profile.setDescription(StringUtils.left(StringUtils.trimToNull(description), MAX_DESCRIPTION_CHARS));

        List<String> cleanSkills = new ArrayList<>();
        for (String skill : orEmpty(skills)) {
            String clean = StringUtils.trimToEmpty(skill).toUpperCase();
            if (clean.isEmpty() || cleanSkills.contains(clean)) continue;
            if (!SKILL_ID.matcher(clean).matches()) return "Unknown skill: " + skill;
            cleanSkills.add(clean);
        }
        profile.setSkills(cleanSkills);

        List<SmartAgentKnowledgeFile> ownSkills = new ArrayList<>();
        String problem = storeCustomSkills(customSkills, ownSkills);
        if (problem != null) return problem;
        profile.setCustomSkills(ownSkills);

        profile.setAllowCreateIdentities(allowCreateIdentities);

        String cleanInstructions = StringUtils.trimToNull(instructions);
        if (cleanInstructions != null && cleanInstructions.length() > SmartAgentConfig.MAX_INSTRUCTIONS_CHARS) {
            return "Instructions can be at most " + SmartAgentConfig.MAX_INSTRUCTIONS_CHARS + " characters";
        }
        profile.setInstructions(cleanInstructions);

        List<SmartAgentKnowledgeFile> files = new ArrayList<>();
        problem = storeKnowledgeFiles(knowledgeFiles, files);
        if (problem != null) return problem;
        profile.setKnowledgeFiles(files);

        List<String> links = new ArrayList<>();
        for (String link : orEmpty(knowledgeLinks)) {
            String clean = StringUtils.trimToEmpty(link);
            if (clean.isEmpty() || links.contains(clean)) continue;
            if (!isWebUrl(clean)) return "Not a web link: " + clean;
            links.add(clean);
        }
        if (links.size() > SmartAgentConfig.MAX_LIST_ITEMS) return "Add at most " + SmartAgentConfig.MAX_LIST_ITEMS + " links";
        profile.setKnowledgeLinks(links);
        return null;
    }

    /** Custom skills: skill files stored in blob storage exactly like knowledge files — only {name, sha256,
     * size} is kept in Mongo and only smart testing reads the content. The problem to show the user, or null. */
    private static String storeCustomSkills(List<Map<String, Object>> items, List<SmartAgentKnowledgeFile> out) {
        return storeBlobFiles(items, out, SmartAgentConfig.MAX_CUSTOM_SKILL_CHARS, SmartAgentConfig.MAX_SKILL_FILES, "skill files");
    }

    /** Where knowledge base files go: Azure blob storage, as for file inspection content. Tests replace it. */
    interface BlobStore {
        boolean exists(String blobName);
        void upload(String blobName, byte[] data);
    }

    static Supplier<BlobStore> blobStore = () -> {
        AzureBlobClient client = AzureBlobClient.getInstance(); // throws when blob storage is not configured
        return new BlobStore() {
            public boolean exists(String blobName) { return client.exists(blobName); }
            public void upload(String blobName, byte[] data) { client.upload(blobName, data); }
        };
    };

    private static String storeKnowledgeFiles(List<Map<String, Object>> items, List<SmartAgentKnowledgeFile> out) {
        return storeBlobFiles(items, out, SmartAgentConfig.MAX_KNOWLEDGE_FILE_CHARS, SmartAgentConfig.MAX_KNOWLEDGE_FILES, "files");
    }

    /**
     * Files (knowledge base files and skill files): a new file (with content) is stored in blob storage under its
     * sha256, like file inspection content, and only {name, sha256, size} is kept; a file saved before comes back as
     * {name, sha256, size}. Without blob storage (not configured) the content stays inline. The problem, or null.
     */
    private static String storeBlobFiles(List<Map<String, Object>> items, List<SmartAgentKnowledgeFile> out, int maxChars, int maxCount, String label) {
        BlobStore store = null;
        boolean blobsAvailable = true;
        for (Map<String, Object> item : orEmpty(items)) {
            if (item == null) continue;
            String fileName = StringUtils.left(StringUtils.trimToEmpty(String.valueOf(item.getOrDefault("name", ""))), SmartAgentConfig.MAX_FILE_NAME_CHARS);
            if (fileName.isEmpty()) continue;
            Object rawContent = item.get("content");
            String content = rawContent != null ? rawContent.toString() : null;
            String sha256 = StringUtils.trimToNull(item.get("sha256") != null ? item.get("sha256").toString().toLowerCase() : null);
            if (StringUtils.isBlank(content)) {
                if (sha256 == null) continue;
                if (!SHA256.matcher(sha256).matches()) return fileName + ": invalid file reference";
                int size = item.get("size") instanceof Number ? ((Number) item.get("size")).intValue() : 0;
                out.add(new SmartAgentKnowledgeFile(fileName, null, sha256, size));
                continue;
            }
            if (content.length() > maxChars) {
                return fileName + " is too long (at most " + maxChars + " characters)";
            }
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            String hash = sha256Hex(data);
            if (blobsAvailable && store == null) {
                try {
                    store = blobStore.get();
                } catch (IllegalStateException e) {
                    blobsAvailable = false; // no blob storage here: keep the content inline
                }
            }
            if (!blobsAvailable) {
                out.add(new SmartAgentKnowledgeFile(fileName, content, hash, content.length()));
                continue;
            }
            try {
                String blobName = AzureBlobClient.buildBlobName(Context.accountId.get(), hash);
                if (!store.exists(blobName)) {
                    store.upload(blobName, data);
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "smart agent: " + label + " upload failed: " + e.getMessage(), LogDb.DASHBOARD);
                return "Could not store " + fileName + ", try again";
            }
            out.add(new SmartAgentKnowledgeFile(fileName, null, hash, content.length()));
        }
        if (out.size() > maxCount) return "Add at most " + maxCount + " " + label;
        return null;
    }

    static String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean isWebUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            return (scheme.equals("https") || scheme.equals("http")) && StringUtils.isNotBlank(uri.getHost());
        } catch (Exception e) {
            return false;
        }
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list != null ? list : new ArrayList<>();
    }

    public void setHexId(String hexId) { this.hexId = hexId; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setSkills(List<String> skills) { this.skills = skills; }
    public void setCustomSkills(List<Map<String, Object>> customSkills) { this.customSkills = customSkills; }
    public void setAllowCreateIdentities(boolean allowCreateIdentities) { this.allowCreateIdentities = allowCreateIdentities; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public void setKnowledgeFiles(List<Map<String, Object>> knowledgeFiles) { this.knowledgeFiles = knowledgeFiles; }
    public void setKnowledgeLinks(List<String> knowledgeLinks) { this.knowledgeLinks = knowledgeLinks; }

    public List<SmartAgentProfile> getSmartAgentProfiles() { return smartAgentProfiles; }
    public SmartAgentProfile getSmartAgentProfile() { return smartAgentProfile; }
}
