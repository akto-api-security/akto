package com.akto.dao.insights.agentic;

import com.akto.dto.insights.agentic.AgentFindingGroup;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BsonField;
import com.mongodb.client.model.Projections;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The {@code $group + $project} stage pair behind every {@link AgentFindingGroup} read —
 * TestingRunIssuesDao#openIssueGroupsForDashboard, VulnerableTestingRunResultDao#redTeamAggregates
 * (its counts facet only — conversationId sampling needs its own $unwind, see that method), and
 * McpAuditInfoDao#auditGroupsForAgents. Each of those independently hand-rolled this exact
 * {@code $group by {collectionId,type[,secondary]}, count, max(lastSeen), addToSet(sample) capped}
 * shape before this was extracted — one implementation now, not three.
 */
public final class AgentFindingGroupAggregation {

    private AgentFindingGroupAggregation() {}

    /**
     * @param collectionIdField dotted path to the collection id field on the matched documents
     * @param typeField         dotted path to the "type" grouping field
     * @param secondaryField    dotted path to the secondary grouping field (severity/remarks), or
     *                          null when the source has none
     * @param lastSeenField     dotted path to a timestamp field to take the max of, or null
     * @param sampleField       dotted path to a per-document scalar field to addToSet (never an
     *                          array field — that needs its own $unwind, not this helper), or null
     * @param sampleCap         max sample values per group (only meaningful when sampleField != null)
     */
    public static List<Bson> groupAndProject(String collectionIdField, String typeField, String secondaryField,
                                              String lastSeenField, String sampleField, int sampleCap) {
        BasicDBObject groupId = new BasicDBObject("collectionId", "$" + collectionIdField).append("type", "$" + typeField);
        if (secondaryField != null) groupId.append("secondary", "$" + secondaryField);

        List<BsonField> accumulators = new ArrayList<>();
        accumulators.add(Accumulators.sum("count", 1));
        if (lastSeenField != null) accumulators.add(Accumulators.max("lastSeen", "$" + lastSeenField));
        if (sampleField != null) accumulators.add(Accumulators.addToSet("sample", "$" + sampleField));

        List<Bson> stages = new ArrayList<>();
        stages.add(Aggregates.group(groupId, accumulators));

        List<Bson> projectFields = new ArrayList<>();
        projectFields.add(Projections.excludeId());
        projectFields.add(Projections.include("count"));
        projectFields.add(Projections.computed("collectionId", "$_id.collectionId"));
        projectFields.add(Projections.computed("type", "$_id.type"));
        projectFields.add(secondaryField != null
                ? Projections.computed("secondary", "$_id.secondary")
                : Projections.computed("secondary", new BasicDBObject("$literal", null)));
        projectFields.add(lastSeenField != null
                ? Projections.include("lastSeen")
                : Projections.computed("lastSeen", new BasicDBObject("$literal", 0)));
        projectFields.add(sampleField != null
                ? Projections.computed("sample", new BasicDBObject("$slice", Arrays.asList("$sample", sampleCap)))
                : Projections.computed("sample", new BasicDBObject("$literal", new ArrayList<>())));
        stages.add(Aggregates.project(Projections.fields(projectFields)));
        return stages;
    }

    /** Parses the {@code $project} shape groupAndProject produces (also reused by
     *  redTeamAggregates' own counts facet, whose docs never carry a real "sample"). */
    public static List<AgentFindingGroup> parse(Iterable<BasicDBObject> docs) {
        List<AgentFindingGroup> out = new ArrayList<>();
        for (BasicDBObject doc : docs) {
            List<String> sample = new ArrayList<>();
            Object raw = doc.get("sample");
            if (raw instanceof List) {
                for (Object o : (List<?>) raw) if (o != null) sample.add(o.toString());
            }
            out.add(new AgentFindingGroup(doc.getInt("collectionId"), doc.getString("type"), doc.getString("secondary"),
                    doc.getInt("count"), doc.getInt("lastSeen"), sample));
        }
        return out;
    }
}
