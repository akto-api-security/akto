package com.akto.jobs.executors;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dao.jobs.JobsDao;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.data_actor.ClientActor;
import com.akto.database_abstractor_authenticator.JwtAuthenticator;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.JobStatus;
import com.akto.dto.jobs.SkillRevalidationJobParams;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic.SampleData;
import com.akto.jobs.JobExecutor;
import com.akto.log.LoggerMaker;
import com.akto.util.http_util.CoreHTTPClient;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Validates the skills already stored for Endpoint Shield agents again, through cyborg's
 * api/validateSkillV3, and writes the result onto each stored skill in place of the old one:
 * the latest sample gets the new skill_annotations and skill_verdict, and the endpoint's skill-tags
 * and malicious-skill-tag are replaced. Only SKILL.md is stored, so the scripts a skill runs are not
 * part of the validation.
 */
public class SkillRevalidationJobExecutor extends JobExecutor<SkillRevalidationJobParams> {

    private static final LoggerMaker logger = new LoggerMaker(SkillRevalidationJobExecutor.class, LoggerMaker.LogDb.DASHBOARD);
    public static final SkillRevalidationJobExecutor INSTANCE = new SkillRevalidationJobExecutor();

    private static final String SKILL_TAGS = "skill-tags";
    private static final String MALICIOUS_SKILL_TAG = "malicious-skill-tag";
    // ApiInfo does not map tagsList on every branch, so the field is named here
    private static final String API_INFO_TAGS_LIST = "tagsList";

    private static final String CYBORG_URL = System.getenv().getOrDefault("SKILL_REVALIDATION_CYBORG_URL", ClientActor.CYBORG_URL)
        .replaceAll("/+$", "");

    // Each call is a synchronous LLM call in cyborg; back to back they trip the provider's rate limit.
    // Same spacing as the endpoint-shield agent's skill validation worker (skillValidationInterval).
    private static final long CALL_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(
        Long.parseLong(System.getenv().getOrDefault("SKILL_REVALIDATION_INTERVAL_SECONDS", "5")));

    private static final MediaType JSON = MediaType.parse("application/json");

    // one LLM call per skill, so allow for a slow reply
    private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();

    public SkillRevalidationJobExecutor() {
        super(SkillRevalidationJobParams.class);
    }

    @Override
    protected void runJob(Job job) throws Exception {
        SkillRevalidationJobParams params = paramClass.cast(job.getJobParams());
        List<String> agentIds = params.getAgentIds() == null ? Collections.emptyList() : params.getAgentIds();

        List<SampleData> skills = findSkillSamples(agentIds);
        params.setSkillsTotal(skills.size());
        params.setSkillsRevalidated(0);
        params.setSkillsFailed(0);
        updateJobParams(job, params);
        logger.infoAndAddToDb("Skill revalidation job " + job.getId() + ": " + skills.size() + " skills on " + agentIds.size() + " agents");
        if (skills.isEmpty()) return;

        // no scope claim: cyborg turns a scoped token away unless it has been exchanged first
        Map<String, Object> claims = new HashMap<>();
        claims.put("accountId", Context.accountId.get());
        String token = JwtAuthenticator.createJWT(claims, "Akto", "invite_user", Calendar.DATE, 1);

        for (int i = 0; i < skills.size(); i++) {
            SampleData skill = skills.get(i);
            if (i > 0) Thread.sleep(CALL_INTERVAL_MILLIS);
            if (!isStillRunning(job)) {
                logger.infoAndAddToDb("Skill revalidation job " + job.getId() + " was stopped");
                return;
            }
            try {
                if (revalidate(skill, token)) {
                    params.setSkillsRevalidated(params.getSkillsRevalidated() + 1);
                } else {
                    params.setSkillsFailed(params.getSkillsFailed() + 1);
                }
            } catch (Exception e) {
                params.setSkillsFailed(params.getSkillsFailed() + 1);
                logger.errorAndAddToDb(e, "Skill revalidation failed for " + skill.getId().getUrl() + ": " + e.getMessage());
            }
            updateJobParams(job, params);
        }

        if (params.getSkillsRevalidated() == 0) {
            throw new Exception("None of the " + skills.size() + " skills could be revalidated");
        }
    }

    // sends a heartbeat; false once the job has been stopped
    private boolean isStillRunning(Job job) {
        updateJobHeartbeat(job);
        Job latest = JobsDao.instance.findOne(Filters.eq(Job.ID, job.getId()));
        return latest != null && latest.getJobStatus() == JobStatus.RUNNING;
    }

    /**
     * The stored skill endpoints of the agents' collections. An agent's collections are named after
     * its device, "{device}.ai-agent.{agent}" or "{device}.{agent}.{plugin}", and the device is the
     * agent's module_info name.
     */
    static List<SampleData> findSkillSamples(List<String> agentIds) {
        if (agentIds.isEmpty()) return Collections.emptyList();
        List<ModuleInfo> agents = ModuleInfoDao.instance.findAll(Filters.and(
            Filters.in("_id", agentIds),
            Filters.eq(ModuleInfo.MODULE_TYPE, ModuleInfo.ModuleType.MCP_ENDPOINT_SHIELD)
        ));

        List<Bson> hostFilters = new ArrayList<>();
        for (ModuleInfo agent : agents) {
            if (StringUtils.isBlank(agent.getName())) continue;
            hostFilters.add(Filters.regex(ApiCollection.HOST_NAME, "^" + Pattern.quote(agent.getName() + ".")));
        }
        if (hostFilters.isEmpty()) return Collections.emptyList();

        List<Integer> collectionIds = new ArrayList<>();
        for (ApiCollection collection : ApiCollectionsDao.instance.findAll(Filters.or(hostFilters), Projections.include(ApiCollection.ID))) {
            collectionIds.add(collection.getId());
        }
        if (collectionIds.isEmpty()) return Collections.emptyList();

        return SampleDataDao.instance.findAll(Filters.and(
            Filters.in("_id.apiCollectionId", collectionIds),
            Filters.regex("_id.url", "/skills/"),
            Filters.eq("_id.method", "POST")
        ));
    }

    /** Revalidates one stored skill and writes the result back. False when there is nothing to revalidate. */
    private boolean revalidate(SampleData skill, String token) throws Exception {
        List<String> samples = skill.getSamples();
        if (samples == null || samples.isEmpty()) return false;

        // the runtime appends samples, so the last one is the latest
        String latest = samples.get(samples.size() - 1);
        BasicDBObject message = BasicDBObject.parse(latest);
        BasicDBObject body = BasicDBObject.parse(StringUtils.defaultIfBlank(message.getString("requestPayload"), "{}"));
        BasicDBObject headers = BasicDBObject.parse(StringUtils.defaultIfBlank(message.getString("requestHeaders"), "{}"));
        if (StringUtils.isBlank(body.getString("skill_name")) || StringUtils.isBlank(body.getString("skill_content"))) {
            logger.infoAndAddToDb("Skipping " + skill.getId().getUrl() + ": its sample has no skill content");
            return false;
        }

        BasicDBObject request = new BasicDBObject()
            .append("skillName", body.getString("skill_name"))
            .append("skillDescription", body.getString("skill_description", ""))
            .append("skillContent", body.getString("skill_content"))
            .append("agentName", body.getString("agent", ""))
            .append("filePath", body.getString("file_path", ""))
            .append("collectionName", headers.getString("host", ""));
        BasicDBObject result = callCyborg(request, token);

        BasicDBList tagLabels = (BasicDBList) result.get("tagLabels");
        BasicDBObject verdict = (BasicDBObject) result.get("verdict");
        boolean malicious = verdict != null && verdict.getBoolean("isMalicious", false);

        // the latest sample, carrying the new evidence, becomes the only one so the dashboard reads it
        body.put("skill_annotations", result.get("annotations"));
        body.put("skill_verdict", verdict);
        message.put("requestPayload", body.toJson());
        List<String> labels = new ArrayList<>();
        if (tagLabels != null) for (Object label : tagLabels) labels.add(String.valueOf(label));
        if (labels.isEmpty()) headers.remove(SKILL_TAGS); else headers.put(SKILL_TAGS, String.join(",", labels));
        message.put("requestHeaders", headers.toJson());
        // field by field: stored _ids don't all have their fields in the order a whole Key is encoded in
        SampleData updated = SampleDataDao.instance.updateOneNoUpsert(Filters.and(
                Filters.eq("_id.apiCollectionId", skill.getId().getApiCollectionId()),
                Filters.eq("_id.url", skill.getId().getUrl()),
                Filters.eq("_id.method", skill.getId().getMethod().name()),
                Filters.eq("_id.responseCode", skill.getId().getResponseCode()),
                Filters.eq("_id.bucketStartEpoch", skill.getId().getBucketStartEpoch()),
                Filters.eq("_id.bucketEndEpoch", skill.getId().getBucketEndEpoch())),
            Updates.set(SampleData.SAMPLES, Collections.singletonList(message.toJson())));
        if (updated == null) {
            throw new Exception("its stored sample could not be updated");
        }

        replaceTags(skill, labels, malicious);
        logger.infoAndAddToDb("Skill revalidated " + skill.getId().getUrl() + " in collection " + skill.getId().getApiCollectionId()
            + ": malicious=" + malicious + " tags=" + labels);
        return true;
    }

    /** The old skill-tags (but not the agent's version= ones) and malicious flag give way to the new ones. */
    private static void replaceTags(SampleData skill, List<String> labels, boolean malicious) {
        Bson filter = Filters.and(
            Filters.eq(ApiInfo.ID_API_COLLECTION_ID, skill.getId().getApiCollectionId()),
            Filters.eq(ApiInfo.ID_URL, skill.getId().getUrl()),
            Filters.eq(ApiInfo.ID_METHOD, skill.getId().getMethod().name())
        );

        Bson oldTags = Filters.or(
            Filters.and(Filters.eq(CollectionTags.KEY_NAME, SKILL_TAGS),
                Filters.not(Filters.regex(CollectionTags.VALUE, Pattern.compile("^version=", Pattern.CASE_INSENSITIVE)))),
            Filters.eq(CollectionTags.KEY_NAME, MALICIOUS_SKILL_TAG)
        );
        // a field can't be pulled from and pushed to in one update
        ApiInfoDao.instance.updateManyNoUpsert(filter, Updates.pull(API_INFO_TAGS_LIST, oldTags));

        int now = Context.now();
        List<CollectionTags> newTags = new ArrayList<>();
        for (String label : labels) {
            newTags.add(new CollectionTags(now, SKILL_TAGS, label, CollectionTags.TagSource.AKTO));
        }
        if (malicious) {
            newTags.add(new CollectionTags(now, MALICIOUS_SKILL_TAG, "true", CollectionTags.TagSource.AKTO));
        }
        List<Bson> updates = new ArrayList<>();
        if (!newTags.isEmpty()) updates.add(Updates.addEachToSet(API_INFO_TAGS_LIST, newTags));
        // the threat score comes from the malicious verdict; the threat sync sets it again for a malicious skill
        if (!malicious) updates.add(Updates.set(ApiInfo.THREAT_SCORE, 0f));
        if (!updates.isEmpty()) ApiInfoDao.instance.updateManyNoUpsert(filter, Updates.combine(updates));
    }

    private static BasicDBObject callCyborg(BasicDBObject requestBody, String token) throws Exception {
        Request request = new Request.Builder()
            .url(CYBORG_URL + "/api/validateSkillV3")
            .post(RequestBody.create(requestBody.toJson(), JSON))
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", token)
            .build();
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String text = responseBody == null ? "" : responseBody.string();
            if (!response.isSuccessful()) {
                throw new Exception("cyborg returned HTTP " + response.code() + ": " + StringUtils.abbreviate(text, 300));
            }
            return BasicDBObject.parse(text);
        }
    }
}
