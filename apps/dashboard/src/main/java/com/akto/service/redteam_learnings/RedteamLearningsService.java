package com.akto.service.redteam_learnings;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.redteam_learnings.AgentRedteamProfileDao;
import com.akto.dao.redteam_learnings.AgentRedteamTemplateDraftDao;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.AccountSettings;
import com.akto.dto.redteam_learnings.AgentRedteamProfile;
import com.akto.dto.redteam_learnings.AgentRedteamRecord;
import com.akto.dto.redteam_learnings.AgentRedteamTemplateDraft;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.util.enums.GlobalEnums.YamlTemplateSource;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.conversions.Bson;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the dashboard shows of red-team learnings, per agent ({apiCollectionId, url, method}) and per run, and the
 * draft status a user sets. The agent server writes these collections through the database-abstractor; this class
 * only reads them, apart from a draft's status. Access to a collection is decided by the caller-supplied list of
 * collection ids the user may see (null: all), on top of the RBAC filter the DAOs already apply.
 */
public class RedteamLearningsService {

    private static final LoggerMaker logger = new LoggerMaker(RedteamLearningsService.class, LogDb.DASHBOARD);

    public static final String STATE_NONE = "NONE";
    public static final String STATE_READY = "READY";

    static final int MAX_AGENT_VERSIONS = 20;
    static final int MAX_OPEN_DRAFTS = 20;
    public static final int MAX_RUN_VERSIONS = 200;
    public static final int MAX_RUN_AGENTS = 20;
    static final int MAX_RUN_DRAFTS = 50;

    public static final Set<String> DRAFT_STATUSES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            AgentRedteamTemplateDraft.STATUS_DRAFT, AgentRedteamTemplateDraft.STATUS_SAVED, AgentRedteamTemplateDraft.STATUS_DISMISSED)));

    public enum DraftUpdate { UPDATED, NOT_FOUND, FORBIDDEN, TEMPLATE_NOT_FOUND, CONFLICT }

    @Getter
    public static class AgentLearnings {
        private final String state;
        private final AgentRedteamProfile latestProfile;
        /** Newest first, without cells and insights. */
        private final List<AgentRedteamProfile> versions;
        private final List<AgentRedteamTemplateDraft> openDrafts;

        AgentLearnings(AgentRedteamProfile latestProfile, List<AgentRedteamProfile> versions, List<AgentRedteamTemplateDraft> openDrafts) {
            this.state = latestProfile == null ? STATE_NONE : STATE_READY;
            this.latestProfile = latestProfile;
            this.versions = versions;
            this.openDrafts = openDrafts;
        }
    }

    @Getter
    public static class RunLearnings {
        private final String state;
        /** Each tested agent's newest version from this run, in full, newest first. */
        private final List<AgentRedteamProfile> agentProfiles;
        /** Every version this run wrote, without cells and insights, latest batch first. */
        private final List<AgentRedteamProfile> versions;
        private final List<AgentRedteamTemplateDraft> drafts;
        /** True when the run wrote more versions than are read, so the list above is not complete. */
        private final boolean versionsTruncated;
        /** True when the run tested more agents than agentProfiles holds. */
        private final boolean agentsTruncated;

        RunLearnings(List<AgentRedteamProfile> agentProfiles, List<AgentRedteamProfile> versions,
                     List<AgentRedteamTemplateDraft> drafts, boolean versionsTruncated, boolean agentsTruncated) {
            this.state = versions.isEmpty() && drafts.isEmpty() ? STATE_NONE : STATE_READY;
            this.agentProfiles = agentProfiles;
            this.versions = versions;
            this.drafts = drafts;
            this.versionsTruncated = versionsTruncated;
            this.agentsTruncated = agentsTruncated;
        }
    }

    public static boolean canSee(List<Integer> allowedCollectionIds, int apiCollectionId) {
        return allowedCollectionIds == null || allowedCollectionIds.contains(apiCollectionId);
    }

    /** The caller has checked that the user may see this collection. */
    public AgentLearnings forAgent(int apiCollectionId, String url, String method) {
        AgentRedteamProfile latest = AgentRedteamProfileDao.instance.findLatest(apiCollectionId, url, method);
        List<AgentRedteamProfile> versions = AgentRedteamProfileDao.instance.findVersions(apiCollectionId, url, method, MAX_AGENT_VERSIONS);
        List<AgentRedteamTemplateDraft> drafts = AgentRedteamTemplateDraftDao.instance.findByIdentity(
                apiCollectionId, url, method, AgentRedteamTemplateDraft.STATUS_DRAFT, MAX_OPEN_DRAFTS);
        return new AgentLearnings(latest, versions, drafts);
    }

    /** Only learnings about collections the user may see. */
    public RunLearnings forRun(String testingRunResultSummaryId, List<Integer> allowedCollectionIds) {
        // One more than the limit is read, so a run with exactly MAX_RUN_VERSIONS versions is not called truncated.
        List<AgentRedteamProfile> read = AgentRedteamProfileDao.instance.findByRun(testingRunResultSummaryId, MAX_RUN_VERSIONS + 1);
        boolean truncated = read.size() > MAX_RUN_VERSIONS;
        List<AgentRedteamProfile> versions = visible(truncated ? read.subList(0, MAX_RUN_VERSIONS) : read, allowedCollectionIds);
        List<AgentRedteamTemplateDraft> drafts = visible(
                AgentRedteamTemplateDraftDao.instance.findByRun(testingRunResultSummaryId, MAX_RUN_DRAFTS), allowedCollectionIds);
        NewestPerAgent newest = newestPerAgent(versions);
        return new RunLearnings(newest.profiles, versions, drafts, truncated, newest.truncated);
    }

    /**
     * Sets a draft's status. SAVED needs the id of the template the user saved it as, and that template must exist
     * and be a custom one (a draft is saved through the editor, which always creates a custom template), so a draft is
     * never shown as saved against an unrelated Akto template or none; for the other statuses a given template id is
     * ignored. A saved draft is not put back to DRAFT.
     */
    public DraftUpdate updateDraftStatus(String draftId, String status, String savedTemplateId, List<Integer> allowedCollectionIds, long nowMs) {
        AgentRedteamTemplateDraft draft = AgentRedteamTemplateDraftDao.instance.findOne(Filters.eq("_id", draftId),
                Projections.include(AgentRedteamRecord.API_COLLECTION_ID, AgentRedteamTemplateDraft.STATUS));
        if (draft == null) return DraftUpdate.NOT_FOUND;
        if (!canSee(allowedCollectionIds, draft.getApiCollectionId())) return DraftUpdate.FORBIDDEN;
        if (AgentRedteamTemplateDraft.STATUS_DRAFT.equals(status) && AgentRedteamTemplateDraft.STATUS_SAVED.equals(draft.getStatus())) {
            return DraftUpdate.CONFLICT;
        }
        String templateId = null;
        if (AgentRedteamTemplateDraft.STATUS_SAVED.equals(status)) {
            Bson custom = Filters.and(Filters.eq(Constants.ID, savedTemplateId),
                    Filters.eq(YamlTemplate.SOURCE, YamlTemplateSource.CUSTOM));
            if (YamlTemplateDao.instance.findOne(custom, Projections.include(Constants.ID)) == null) {
                return DraftUpdate.TEMPLATE_NOT_FOUND;
            }
            templateId = savedTemplateId;
        }
        AgentRedteamTemplateDraft before = AgentRedteamTemplateDraftDao.instance.updateStatus(draftId, status, templateId, nowMs);
        // The draft was just read, so nothing updated means it changed in between (it was saved meanwhile).
        return before == null ? DraftUpdate.CONFLICT : DraftUpdate.UPDATED;
    }

    /** The account's setting: never set counts as on. Null when it could not be read, which is shown as unknown. */
    public Boolean accountLearningsEnabled() {
        try {
            AccountSettings settings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter(),
                    Projections.include(AccountSettings.REDTEAM_LEARNINGS_ENABLED));
            return settings == null || !Boolean.FALSE.equals(settings.getRedteamLearningsEnabled());
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Could not read the red-team learnings setting: " + e.getMessage());
            return null;
        }
    }

    private static <T extends AgentRedteamRecord> List<T> visible(List<T> records, List<Integer> allowedCollectionIds) {
        List<T> kept = new ArrayList<>();
        for (T record : records) {
            if (canSee(allowedCollectionIds, record.getApiCollectionId())) kept.add(record);
        }
        return kept;
    }

    private static class NewestPerAgent {
        final List<AgentRedteamProfile> profiles;
        final boolean truncated;

        NewestPerAgent(List<AgentRedteamProfile> profiles, boolean truncated) {
            this.profiles = profiles;
            this.truncated = truncated;
        }
    }

    /** The full newest version (by write time) of each agent the run tested, at most MAX_RUN_AGENTS of them. */
    private static NewestPerAgent newestPerAgent(List<AgentRedteamProfile> versions) {
        List<AgentRedteamProfile> byTime = new ArrayList<>(versions);
        byTime.sort(Comparator.comparingLong(AgentRedteamProfile::getCreatedAtMs).reversed());
        Map<String, String> newestIdByAgent = new LinkedHashMap<>();
        boolean truncated = false;
        for (AgentRedteamProfile version : byTime) {
            String agent = version.getApiCollectionId() + "|" + version.getMethod() + "|" + version.getUrl();
            if (newestIdByAgent.containsKey(agent)) continue;
            if (newestIdByAgent.size() < MAX_RUN_AGENTS) newestIdByAgent.put(agent, version.getId());
            else truncated = true;
        }
        Map<String, AgentRedteamProfile> fullById = new HashMap<>();
        for (AgentRedteamProfile full : AgentRedteamProfileDao.instance.findByIds(new ArrayList<>(newestIdByAgent.values()))) {
            fullById.put(full.getId(), full);
        }
        List<AgentRedteamProfile> ordered = new ArrayList<>();
        for (String id : newestIdByAgent.values()) {
            if (fullById.containsKey(id)) ordered.add(fullById.get(id));
        }
        return new NewestPerAgent(ordered, truncated);
    }
}
