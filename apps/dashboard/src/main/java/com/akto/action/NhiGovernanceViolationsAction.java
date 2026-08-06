package com.akto.action;

import com.akto.dao.context.Context;
import com.akto.dao.nhi_governance.NhiViolationDao;
import com.akto.dao.JiraIntegrationDao;
import com.akto.dto.nhi_governance.NhiViolation;
import com.akto.dto.nhi_governance.NhiPolicy;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.jira_integration.JiraIntegration;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.ApiExecutor;
import com.akto.util.Pair;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.utils.jira.Utils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Facet;
import com.mongodb.client.model.Field;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.Variable;
import com.opensymphony.xwork2.Action;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class NhiGovernanceViolationsAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(NhiGovernanceViolationsAction.class, LogDb.DASHBOARD);

    @Getter
    private List<NhiViolation> violations;

    @Getter
    @Setter
    private NhiViolation violation;

    @Setter
    private List<String> violationIds;

    @Setter
    private String violationId;

    @Setter
    private int startTimestamp;

    @Setter
    private int endTimestamp;

    // ---- Server-side pagination / stats (ATLAS NHI Governance) ----
    @Setter private int skip;
    @Setter private int limit;
    @Setter private String sortKey;
    @Setter private int sortOrder;      // 1 asc, -1 desc (Mongo convention)
    @Setter private String queryValue;  // search on agentName / violationType
    @Setter private String status;      // tab filter: "Open" (!= Fixed) / "Fixed" / null-or-"All"
    @Setter private String identityId;  // hex id, for fetchViolationsByIdentity
    @Getter private long total;
    @Getter private Map<String, Object> stats;
    @Getter private List<Map<String, Object>> identityViolationCounts;
    @Getter private List<Map<String, Object>> policyViolationCounts;

    @Setter
    private String projId;

    @Setter
    private String issueType;

    @Setter
    private String aktoDashboardHost;

    @Setter
    private Map<String, Object> jiraMetaData;

    @Getter
    private boolean success = false;

    @Getter
    private String errorMessage;

    // Aggregation bypasses AccountsContextDaoWithContextSource — apply contextSource + date-range
    // filters explicitly. Shared by every violations query below so all of them scope consistently.
    private List<Bson> buildBaseMatchConditions() {
        List<Bson> matchConditions = new ArrayList<>();
        if (Context.contextSource.get() != null) {
            matchConditions.add(Filters.eq(NhiViolation.CONTEXT_SOURCE, Context.contextSource.get().name()));
        }
        if (startTimestamp > 0 && endTimestamp > 0) {
            matchConditions.add(Filters.gte(NhiViolation.DISCOVERED_AT, startTimestamp));
            matchConditions.add(Filters.lte(NhiViolation.DISCOVERED_AT, endTimestamp));
        }
        return matchConditions;
    }

    private static Bson combineMatch(List<Bson> conditions) {
        if (conditions.isEmpty()) return Filters.empty();
        return conditions.size() == 1 ? conditions.get(0) : Filters.and(conditions);
    }

    private static String mapSortField(String key) {
        if (key == null) return NhiViolation.DISCOVERED_AT;
        switch (key) {
            case "severity": return NhiViolation.SEVERITY;
            case "status": return NhiViolation.STATUS;
            case "agent": return NhiViolation.AGENT_NAME;
            case "violationType": return NhiViolation.VIOLATION_TYPE;
            case "detected":
            default: return NhiViolation.DISCOVERED_AT;
        }
    }

    // Join nhi_policies (policyIds is List<String>, _id is ObjectId — match via $toString), replace the
    // policy field with the resolved name list, and drop heavy fields no UI consumer reads on the list view.
    private static List<Bson> policyLookupAndProjectStages() {
        List<Bson> stages = new ArrayList<>();
        stages.add(Aggregates.lookup(
                NhiPolicy.COLLECTION_NAME,
                Arrays.asList(new Variable<>("vPolicyIds", "$" + NhiViolation.POLICY_IDS)),
                Arrays.asList(
                        Aggregates.match(Filters.expr(new Document("$in", Arrays.asList(
                                new Document("$toString", "$_id"),
                                new Document("$ifNull", Arrays.asList("$$vPolicyIds", Collections.emptyList()))
                        )))),
                        Aggregates.project(Projections.fields(
                                Projections.excludeId(),
                                Projections.include(NhiPolicy.POLICY_NAME)
                        ))
                ),
                "policyDocs"
        ));
        stages.add(Aggregates.addFields(new Field<>(
                NhiViolation.POLICY,
                new Document("$map", new Document("input", "$policyDocs")
                        .append("as", "p")
                        .append("in", "$$p." + NhiPolicy.POLICY_NAME))
        )));
        stages.add(Aggregates.project(Projections.fields(
                Projections.exclude(
                        NhiViolation.ACKNOWLEDGED_AT, NhiViolation.ACKNOWLEDGED_BY,
                        NhiViolation.RESOLVED_AT, NhiViolation.RESOLVED_BY,
                        NhiViolation.AGENT_TYPE,
                        NhiViolation.ACKNOWLEDGMENT_NOTES,
                        NhiViolation.RESOLUTION_NOTES,
                        NhiViolation.RESOLUTION_TYPE,
                        NhiViolation.METADATA,
                        "policyDocs"
                )
        )));
        return stages;
    }

    /**
     * Server-side paginated violations list (ATLAS NHI Governance). Replaces the old load-all behaviour
     * that pulled every violation in the date range (an unbounded, usage-scaled collection) into the
     * browser for the table AND the severity donut AND the trend chart AND the tab counts — see
     * fetchViolationsStats() for those, which are now computed server-side instead.
     */
    public String fetchAllViolations() {
        try {
            List<Bson> matchConditions = buildBaseMatchConditions();
            if (status != null && !status.isEmpty() && !"All".equalsIgnoreCase(status)) {
                if ("Fixed".equalsIgnoreCase(status)) {
                    matchConditions.add(Filters.eq(NhiViolation.STATUS, "Fixed"));
                } else if ("Open".equalsIgnoreCase(status)) {
                    matchConditions.add(Filters.ne(NhiViolation.STATUS, "Fixed"));
                }
            }
            if (queryValue != null && !queryValue.trim().isEmpty()) {
                String q = Pattern.quote(queryValue.trim());
                matchConditions.add(Filters.or(
                        Filters.regex(NhiViolation.AGENT_NAME, q, "i"),
                        Filters.regex(NhiViolation.VIOLATION_TYPE, q, "i")
                ));
            }
            Bson matchFilter = combineMatch(matchConditions);

            total = NhiViolationDao.instance.count(matchFilter);

            String sortField = mapSortField(sortKey);
            int lim = (limit <= 0) ? 50 : Math.min(limit, 500);
            int sk = Math.max(skip, 0);

            List<Bson> pipeline = new ArrayList<>();
            pipeline.add(Aggregates.match(matchFilter));
            pipeline.add(Aggregates.sort((sortOrder < 0) ? Sorts.descending(sortField) : Sorts.ascending(sortField)));
            pipeline.add(Aggregates.skip(sk));
            pipeline.add(Aggregates.limit(lim));
            pipeline.addAll(policyLookupAndProjectStages());

            MongoCursor<NhiViolation> cursor = NhiViolationDao.instance.getMCollection()
                    .aggregate(pipeline, NhiViolation.class).cursor();

            violations = new ArrayList<>();
            while (cursor.hasNext()) {
                violations.add(cursor.next());
            }

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching NHI violations: " + e.getMessage());
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    /**
     * Server-computed aggregates for the Violations page: severity breakdown (open violations only,
     * matching what the donut showed before), day-bucketed counts (for the trend chart), and open/fixed
     * totals (for the tab badges). Replaces client-side reduction over the full violations array.
     */
    public String fetchViolationsStats() {
        try {
            Bson matchFilter = combineMatch(buildBaseMatchConditions());

            Document dayKeyExpr = new Document("$dateToString", new Document("format", "%Y-%m-%d")
                    .append("date", new Document("$toDate", new Document("$multiply",
                            Arrays.asList("$" + NhiViolation.DISCOVERED_AT, 1000)))));

            List<Bson> pipeline = Arrays.asList(
                    Aggregates.match(matchFilter),
                    Aggregates.facet(
                            new Facet("bySeverityOpen",
                                    Aggregates.match(Filters.ne(NhiViolation.STATUS, "Fixed")),
                                    Aggregates.group("$" + NhiViolation.SEVERITY, Accumulators.sum("count", 1))),
                            new Facet("byStatus",
                                    Aggregates.group("$" + NhiViolation.STATUS, Accumulators.sum("count", 1))),
                            new Facet("byDay",
                                    Aggregates.group(dayKeyExpr, Accumulators.sum("count", 1)))
                    )
            );

            Document result = NhiViolationDao.instance.getMCollection()
                    .aggregate(pipeline, Document.class).allowDiskUse(true).first();

            stats = new HashMap<>();
            stats.put("bySeverityOpen", groupToMap(result, "bySeverityOpen"));
            stats.put("byStatus", groupToMap(result, "byStatus"));
            stats.put("byDay", groupToMap(result, "byDay"));

            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching NHI violation stats: " + e.getMessage());
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> groupToMap(Document facetResult, String facetName) {
        Map<String, Object> out = new HashMap<>();
        if (facetResult == null) return out;
        List<Document> docs = (List<Document>) facetResult.get(facetName);
        if (docs == null) return out;
        for (Document d : docs) {
            Object key = d.get("_id");
            out.put(key != null ? String.valueOf(key) : "unknown", d.get("count"));
        }
        return out;
    }

    /**
     * Per-identity violation counts, grouped server-side (one row per identityName x severity) instead of
     * shipping every non-fixed violation document to the browser for client-side grouping.
     */
    public String fetchViolationCountsByIdentity() {
        try {
            List<Bson> pipeline = Arrays.asList(
                    Aggregates.match(Filters.ne(NhiViolation.STATUS, "Fixed")),
                    Aggregates.unwind("$" + NhiViolation.IDENTITIES),
                    Aggregates.group(
                            new Document("identityName", "$" + NhiViolation.IDENTITIES + ".identityName")
                                    .append("severity", "$" + NhiViolation.SEVERITY),
                            Accumulators.sum("count", 1))
            );
            identityViolationCounts = groupPairsToList(pipeline, "identityName");
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching violation counts: " + e.getMessage());
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    // Per-policy violation counts for the NHI Policies page, grouped server-side (one row per
    // policyId x severity) instead of shipping every non-fixed violation projected doc to the browser.
    public String fetchViolationCountsByPolicy() {
        try {
            List<Bson> pipeline = Arrays.asList(
                    Aggregates.match(Filters.ne(NhiViolation.STATUS, "Fixed")),
                    Aggregates.unwind("$" + NhiViolation.POLICY_IDS),
                    Aggregates.group(
                            new Document("policyId", "$" + NhiViolation.POLICY_IDS)
                                    .append("severity", "$" + NhiViolation.SEVERITY),
                            Accumulators.sum("count", 1))
            );
            policyViolationCounts = groupPairsToList(pipeline, "policyId");
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching violation counts by policy: " + e.getMessage());
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    private List<Map<String, Object>> groupPairsToList(List<Bson> pipeline, String keyField) {
        List<Map<String, Object>> out = new ArrayList<>();
        MongoCursor<Document> cursor = NhiViolationDao.instance.getMCollection()
                .aggregate(pipeline, Document.class).cursor();
        while (cursor.hasNext()) {
            Document d = cursor.next();
            Document id = (Document) d.get("_id");
            Map<String, Object> row = new HashMap<>();
            row.put(keyField, id != null ? id.get(keyField) : null);
            row.put("severity", id != null ? id.get("severity") : null);
            row.put("count", d.get("count"));
            out.add(row);
        }
        return out;
    }

    /**
     * Scoped, paginated violations for ONE identity (ATLAS NHI Governance identity-details flyout).
     * Replaces the previous pattern of fetching every violation account-wide with no time bound just to
     * client-side-filter down to a single identity on every panel open.
     */
    public String fetchViolationsByIdentity() {
        try {
            if (identityId == null || identityId.isEmpty()) {
                addActionError("Identity ID is required");
                return Action.ERROR.toUpperCase();
            }

            Bson identityFilter = Filters.eq(NhiViolation.IDENTITIES + ".id", new ObjectId(identityId));
            total = NhiViolationDao.instance.count(identityFilter);

            int lim = (limit <= 0) ? 50 : Math.min(limit, 500);
            int sk = Math.max(skip, 0);

            List<Bson> pipeline = new ArrayList<>();
            pipeline.add(Aggregates.match(identityFilter));
            pipeline.add(Aggregates.sort(Sorts.descending(NhiViolation.DISCOVERED_AT)));
            pipeline.add(Aggregates.skip(sk));
            pipeline.add(Aggregates.limit(lim));
            pipeline.addAll(policyLookupAndProjectStages());

            MongoCursor<NhiViolation> cursor = NhiViolationDao.instance.getMCollection()
                    .aggregate(pipeline, NhiViolation.class).cursor();
            violations = new ArrayList<>();
            while (cursor.hasNext()) {
                violations.add(cursor.next());
            }

            // Severity counts across ALL of this identity's violations (not just the current page).
            List<Bson> sevPipeline = Arrays.asList(
                    Aggregates.match(Filters.and(identityFilter, Filters.ne(NhiViolation.STATUS, "Fixed"))),
                    Aggregates.group("$" + NhiViolation.SEVERITY, Accumulators.sum("count", 1))
            );
            Map<String, Object> sevMap = new HashMap<>();
            MongoCursor<Document> sevCursor = NhiViolationDao.instance.getMCollection()
                    .aggregate(sevPipeline, Document.class).cursor();
            while (sevCursor.hasNext()) {
                Document d = sevCursor.next();
                sevMap.put(String.valueOf(d.get("_id")), d.get("count"));
            }
            stats = new HashMap<>();
            stats.put("bySeverityOpen", sevMap);

            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching violations by identity: " + e.getMessage());
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String fetchViolations() {
        try {
            int accountId = Context.accountId.get();

            // Handle single violation fetch
            if (violationId != null && !violationId.isEmpty()) {
                violation = NhiViolationDao.instance.findOne(NhiViolation.ID, new ObjectId(violationId));

                if (violation == null) {
                    loggerMaker.errorAndAddToDb("Violation not found: " + violationId);
                    addActionError("Violation not found");
                    return Action.ERROR.toUpperCase();
                }

                return Action.SUCCESS.toUpperCase();
            }

            // Handle multiple violations fetch
            if (violationIds != null && !violationIds.isEmpty()) {
                // Convert string IDs to ObjectIds using standard pattern
                List<ObjectId> objectIds = new ArrayList<>();
                for (String id : violationIds) {
                    objectIds.add(new ObjectId(id));
                }

                Bson filter = Filters.in(NhiViolation.ID, objectIds);
                violations = NhiViolationDao.instance.findAll(filter);
                return Action.SUCCESS.toUpperCase();
            }

            loggerMaker.errorAndAddToDb("No violation ID or IDs provided");
            addActionError("Violation ID or IDs is required");
            return Action.ERROR.toUpperCase();

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching violations: " + e.getMessage());
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String reopenViolation() {
        try {
            long currentTime = Context.now();

            if (violationId == null || violationId.isEmpty()) {
                addActionError("Violation ID is required");
                success = false;
                return Action.ERROR.toUpperCase();
            }

            Bson filter = Filters.eq(NhiViolation.ID, new ObjectId(violationId));
            Bson update = Updates.combine(
                Updates.set(NhiViolation.STATUS, "Open"),
                Updates.set(NhiViolation.UPDATED_AT, currentTime),
                Updates.set(NhiViolation.UPDATED_BY, getSUser().getLogin())
            );

            NhiViolationDao.instance.updateOne(filter, update);

            success = true;
            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error reopening violation: " + e.getMessage());
            addActionError(e.getMessage());
            success = false;
            return Action.ERROR.toUpperCase();
        }
    }

    public String markViolationAsFixed() {
        try {
            long currentTime = Context.now();

            if (violationId == null || violationId.isEmpty()) {
                addActionError("Violation ID is required");
                success = false;
                return Action.ERROR.toUpperCase();
            }

            // Update violation status to Fixed with updatedAt and updatedBy
            Bson filter = Filters.eq(NhiViolation.ID, new ObjectId(violationId));
            Bson update = Updates.combine(
                Updates.set(NhiViolation.STATUS, "Fixed"),
                Updates.set(NhiViolation.UPDATED_AT, currentTime),
                Updates.set(NhiViolation.UPDATED_BY, getSUser().getLogin())
            );

            NhiViolationDao.instance.updateOne(filter, update);

            success = true;
            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error marking violation as fixed: " + e.getMessage());
            addActionError(e.getMessage());
            success = false;
            return Action.ERROR.toUpperCase();
        }
    }

    public String createJiraTicketFromViolation() {
        try {
            if (violationId == null || violationId.isEmpty()) {
                loggerMaker.errorAndAddToDb("Violation ID not provided");
                addActionError("Violation ID is required");
                success = false;
                return Action.ERROR.toUpperCase();
            }

            if (projId == null || projId.isEmpty()) {
                loggerMaker.errorAndAddToDb("Project ID not provided");
                addActionError("Project ID is required");
                success = false;
                return Action.ERROR.toUpperCase();
            }

            if (issueType == null || issueType.isEmpty()) {
                loggerMaker.errorAndAddToDb("Issue type not provided");
                addActionError("Issue type is required");
                success = false;
                return Action.ERROR.toUpperCase();
            }

            JiraIntegration jiraIntegration = JiraIntegrationDao.instance.findOne(new BasicDBObject());
            if (jiraIntegration == null) {
                errorMessage = "Jira is not integrated";
                loggerMaker.errorAndAddToDb(errorMessage);
                addActionError(errorMessage);
                success = false;
                return Action.ERROR.toUpperCase();
            }

            NhiViolation violation = NhiViolationDao.instance.findOne(NhiViolation.ID, new ObjectId(violationId));
            if (violation == null) {
                errorMessage = "Violation not found";
                loggerMaker.errorAndAddToDb(errorMessage + ": " + violationId);
                addActionError(errorMessage);
                success = false;
                return Action.ERROR.toUpperCase();
            }

            JiraIntegration.JiraType jiraType = jiraIntegration.getJiraType();
            if (jiraType == null) {
                jiraType = JiraIntegration.JiraType.CLOUD;
            }

            List<BasicDBObject> baseContent = new ArrayList<>();
            baseContent.add(buildContentParagraph(violation.getDescription(), jiraType));
            baseContent.add(buildContentParagraph("Severity: " + violation.getSeverity(), jiraType));
            baseContent.add(buildContentParagraph("Agent: " + violation.getAgentName(), jiraType));
            if (violation.getAffectedResources() != null && !violation.getAffectedResources().isEmpty()) {
                baseContent.add(buildContentParagraph(
                        "Affected Resources: " + String.join(", ", violation.getAffectedResources()), jiraType));
            }
            baseContent.add(buildContentParagraph(
                    "Violation Details: " + aktoDashboardHost + "/dashboard/nhi/violations?id=" + violationId, jiraType));

            Object description = Utils.buildJiraDescription(baseContent, new ArrayList<>(), jiraType);

            Severity severity = null;
            try {
                if (violation.getSeverity() != null) {
                    severity = Severity.valueOf(violation.getSeverity().toUpperCase());
                }
            } catch (IllegalArgumentException ignored) {
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> additionalIssueFields = jiraMetaData != null
                    ? (Map<String, Object>) jiraMetaData.get("additionalIssueFields")
                    : null;

            BasicDBObject fields = Utils.buildPayloadForJiraTicket(
                    violation.getViolationType(), projId, issueType, description,
                    additionalIssueFields, severity, jiraIntegration);

            List<String> labelsList = parseJiraLabels(jiraMetaData);
            if (!labelsList.isEmpty()) {
                fields.put("labels", labelsList.toArray(new String[0]));
            }

            BasicDBObject reqPayload = new BasicDBObject();
            reqPayload.put("fields", fields);

            String url = jiraIntegration.getBaseUrl() + "/rest/api/3/issue";

            Map<String, List<String>> headers = new HashMap<>();
            if (Utils.isDataCenter(jiraType)) {
                headers.put("Authorization", Collections.singletonList("Bearer " + jiraIntegration.getApiToken()));
            } else {
                String authHeader = Base64.getEncoder()
                        .encodeToString((jiraIntegration.getUserEmail() + ":" + jiraIntegration.getApiToken()).getBytes());
                headers.put("Authorization", Collections.singletonList("Basic " + authHeader));
            }
            headers.put("Content-Type", Collections.singletonList("application/json"));

            OriginalHttpRequest request = new OriginalHttpRequest(url, "", "POST", reqPayload.toString(), headers, "");
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
            String responseBody = response.getBody();

            // Retry without labels if Jira rejected them
            if (response.getStatusCode() > 201 && Utils.isLabelsFieldError(responseBody)) {
                loggerMaker.infoAndAddToDb("Labels field error from Jira; retrying without labels for violation: " + violationId);
                fields.remove("labels");
                reqPayload.put("fields", fields);
                OriginalHttpRequest retry = new OriginalHttpRequest(url, "", "POST", reqPayload.toString(), headers, "");
                response = ApiExecutor.sendRequest(retry, true, null, false, new ArrayList<>());
                responseBody = response.getBody();
            }

            if (response.getStatusCode() > 201) {
                String parsedError = Utils.handleError(responseBody);
                errorMessage = "Error creating Jira ticket: "
                        + (parsedError != null ? parsedError : response.getStatusCode() + " - " + responseBody);
                loggerMaker.errorAndAddToDb(errorMessage);
                addActionError("Failed to create Jira ticket");
                success = false;
                return Action.ERROR.toUpperCase();
            }

            Pair<String, String> ticket = Utils.getJiraTicketUrlPair(responseBody, jiraIntegration.getBaseUrl());
            String ticketUrl = ticket != null ? ticket.getFirst() : "";
            loggerMaker.infoAndAddToDb("Created Jira ticket from violation " + violationId + ": " + ticketUrl);
            success = true;
            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            errorMessage = "Error creating Jira ticket from violation: " + e.getMessage();
            loggerMaker.errorAndAddToDb(errorMessage);
            addActionError(e.getMessage());
            success = false;
            return Action.ERROR.toUpperCase();
        }
    }

    // Build a paragraph node in the shape buildJiraDescription expects (ADF for Cloud, plain-text for Data Center).
    private static BasicDBObject buildContentParagraph(String text, JiraIntegration.JiraType jiraType) {
        String safe = StringUtils.defaultString(text, "");
        if (Utils.isDataCenter(jiraType)) {
            return new BasicDBObject("type", "paragraph").append("text", safe);
        }
        return new BasicDBObject("type", "paragraph").append("content",
                Collections.singletonList(new BasicDBObject("type", "text").append("text", safe)));
    }

    private static List<String> parseJiraLabels(Map<String, Object> jiraMetaData) {
        List<String> labelsList = new ArrayList<>();
        if (jiraMetaData == null) return labelsList;
        Object raw = jiraMetaData.get("labels");
        if (!(raw instanceof String) || ((String) raw).trim().isEmpty()) return labelsList;
        for (String label : ((String) raw).split(",")) {
            String trimmed = label.trim();
            if (trimmed.isEmpty()) continue;
            // Jira labels cannot contain spaces — replace with underscores.
            String sanitized = trimmed.replaceAll("\\s+", "_");
            if (!labelsList.contains(sanitized)) {
                labelsList.add(sanitized);
            }
        }
        return labelsList;
    }
}
