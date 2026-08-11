package com.akto.action;

import com.akto.dao.nhi_governance.NhiIdentityDao;
import com.akto.dto.nhi_governance.NhiIdentity;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class NhiGovernanceIdentitiesAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(NhiGovernanceIdentitiesAction.class, LogDb.DASHBOARD);

    @Getter
    private List<NhiIdentity> identities;

    @Getter
    private boolean success = false;

    @Setter
    private String identityId;

    @Setter
    private List<String> identityIds;

    @Setter
    private int startTimestamp;

    @Setter
    private int endTimestamp;

    // ---- Server-side pagination for fetchAllNhiIdentities (ATLAS NHI Governance) ----
    @Setter private int skip;
    @Setter private int limit;
    @Setter private String sortKey;
    @Setter private int sortOrder;      // 1 asc, -1 desc (Mongo convention)
    @Setter private String queryValue;  // search on identityName / agentName
    @Setter private String status;      // tab filter: "Expired" / "Disabled" / null-or-"All"
    @Getter private long total;

    // ---- Lightweight counts for fetchNhiIdentitiesStats (summary cards / tab badges) ----
    @Setter private List<String> identityNames; // identityNames known to have violations (from the
                                                 // already-fetched, cheap fetchViolationCountsByIdentity)
    @Getter private long statTotal;
    @Getter private long statExpired;
    @Getter private long statDisabled;
    @Getter private long statWithViolations;

    // Unlike NhiGovernanceViolationsAction.buildBaseMatchConditions, this does NOT add a
    // contextSource filter: NhiIdentityDao extends the plain AccountsContextDao (not
    // AccountsContextDaoWithContextSource like NhiViolationDao), which never auto-scopes by
    // contextSource for any of its queries — including the existing, unpaginated
    // fetchNhiIdentities() below, which has never filtered by it either. Matches the date-range
    // filter that method already applies.
    private List<Bson> buildIdentityMatchConditions() {
        List<Bson> matchConditions = new ArrayList<>();
        if (startTimestamp > 0 && endTimestamp > 0) {
            matchConditions.add(Filters.gte(NhiIdentity.CREATED_AT, startTimestamp));
            matchConditions.add(Filters.lte(NhiIdentity.CREATED_AT, endTimestamp));
        }
        return matchConditions;
    }

    private static Bson combineIdentityMatch(List<Bson> conditions) {
        if (conditions.isEmpty()) return Filters.empty();
        return conditions.size() == 1 ? conditions.get(0) : Filters.and(conditions);
    }

    private static String mapIdentitySortField(String key) {
        if (key == null) return NhiIdentity.CREATED_AT;
        switch (key) {
            case "identityName": return NhiIdentity.IDENTITY_NAME;
            case "expiryDate": return NhiIdentity.EXPIRY_DATE;
            case "lastUsedAt": return NhiIdentity.LAST_USED_AT;
            case "createdAt":
            default: return NhiIdentity.CREATED_AT;
        }
    }

    // Unpaginated — feeds IdentityOverviewGraph's topology graph, which needs every identity
    // (grouped by agent) to fan out correctly, not just one page. Mirrors DeviceEndpoints.jsx's
    // fetchDeviceChildren keeping a separate full-fetch alongside its own paginated grid.
    public String fetchNhiIdentities() {
        try {
            Bson filter = (startTimestamp > 0 && endTimestamp > 0)
                    ? Filters.and(
                            Filters.gte(NhiIdentity.CREATED_AT, startTimestamp),
                            Filters.lte(NhiIdentity.CREATED_AT, endTimestamp))
                    : Filters.empty();
            identities = NhiIdentityDao.instance.findAll(filter);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching NHI identities: " + e.getMessage());
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    /**
     * Server-side paginated identities list (ATLAS NHI Governance Identities page). Replaces the
     * old load-all-then-paginate-client-side behaviour for the table itself — fetchNhiIdentities()
     * above still exists unpaginated for the topology graph, which genuinely needs every row.
     * Default sort is createdAt descending (most recently discovered first) rather than the old
     * client-side "most violations first" ranking — replicating that server-side would need a
     * $lookup into nhi_violations (identities don't carry a denormalized violation count).
     */
    public String fetchAllNhiIdentities() {
        try {
            List<Bson> matchConditions = buildIdentityMatchConditions();
            if (StringUtils.isNotBlank(status) && !"All".equalsIgnoreCase(status)) {
                if ("Expired".equalsIgnoreCase(status)) {
                    int nowEpoch = (int) (System.currentTimeMillis() / 1000);
                    matchConditions.add(Filters.gt(NhiIdentity.EXPIRY_DATE, 0));
                    matchConditions.add(Filters.lt(NhiIdentity.EXPIRY_DATE, nowEpoch));
                } else if ("Disabled".equalsIgnoreCase(status)) {
                    matchConditions.add(Filters.eq(NhiIdentity.STATUS, "INACTIVE"));
                }
            }
            if (StringUtils.isNotBlank(queryValue)) {
                String q = Pattern.quote(queryValue.trim());
                matchConditions.add(Filters.or(
                        Filters.regex(NhiIdentity.IDENTITY_NAME, q, "i"),
                        Filters.regex(NhiIdentity.AGENT_NAME, q, "i")
                ));
            }
            Bson matchFilter = combineIdentityMatch(matchConditions);

            total = NhiIdentityDao.instance.count(matchFilter);

            String sortField = mapIdentitySortField(sortKey);
            int lim = (limit <= 0) ? 50 : Math.min(limit, 500);
            int sk = Math.max(skip, 0);

            List<Bson> pipeline = new ArrayList<>();
            pipeline.add(Aggregates.match(matchFilter));
            pipeline.add(Aggregates.sort((sortOrder < 0) ? Sorts.descending(sortField) : Sorts.ascending(sortField)));
            pipeline.add(Aggregates.skip(sk));
            pipeline.add(Aggregates.limit(lim));

            MongoCursor<NhiIdentity> cursor = NhiIdentityDao.instance.getMCollection()
                    .aggregate(pipeline, NhiIdentity.class).cursor();

            identities = new ArrayList<>();
            while (cursor.hasNext()) {
                identities.add(cursor.next());
            }

            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching paginated NHI identities: " + e.getMessage());
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    /**
     * Cheap counts-only companion to fetchAllNhiIdentities — feeds the Identities page's summary
     * cards and tab badges without pulling the full account's identity documents over the wire
     * (that used to happen via the old unpaginated fetchNhiIdentities(), which returns 13.8k+ docs
     * on the Atlas Scale Test account just to compute four numbers). Four independent count()
     * calls rather than one $facet — simplest correct fix; revisit as a single $facet if profiling
     * shows the round trips matter.
     */
    public String fetchNhiIdentitiesStats() {
        try {
            List<Bson> baseConditions = buildIdentityMatchConditions();
            Bson baseFilter = combineIdentityMatch(baseConditions);
            statTotal = NhiIdentityDao.instance.count(baseFilter);

            int nowEpoch = (int) (System.currentTimeMillis() / 1000);
            List<Bson> expiredConditions = new ArrayList<>(baseConditions);
            expiredConditions.add(Filters.gt(NhiIdentity.EXPIRY_DATE, 0));
            expiredConditions.add(Filters.lt(NhiIdentity.EXPIRY_DATE, nowEpoch));
            statExpired = NhiIdentityDao.instance.count(combineIdentityMatch(expiredConditions));

            List<Bson> disabledConditions = new ArrayList<>(baseConditions);
            disabledConditions.add(Filters.eq(NhiIdentity.STATUS, "INACTIVE"));
            statDisabled = NhiIdentityDao.instance.count(combineIdentityMatch(disabledConditions));

            if (identityNames != null && !identityNames.isEmpty()) {
                List<Bson> withViolationsConditions = new ArrayList<>(baseConditions);
                withViolationsConditions.add(Filters.in(NhiIdentity.IDENTITY_NAME, identityNames));
                statWithViolations = NhiIdentityDao.instance.count(combineIdentityMatch(withViolationsConditions));
            } else {
                statWithViolations = 0;
            }

            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching NHI identities stats: " + e.getMessage());
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String disableNhiIdentity() {
        try {
            long currentTime = System.currentTimeMillis() / 1000;

            if (identityId == null || identityId.isEmpty()) {
                addActionError("Identity ID is required");
                success = false;
                return Action.ERROR.toUpperCase();
            }

            Bson filter = Filters.eq(NhiIdentity.ID, new ObjectId(identityId));
            Bson update = Updates.combine(
                Updates.set(NhiIdentity.STATUS, "INACTIVE"),
                Updates.set(NhiIdentity.UPDATED_AT, (int)currentTime),
                Updates.set(NhiIdentity.UPDATED_BY, getSUser().getLogin())
            );

            NhiIdentityDao.instance.updateOne(filter, update);

            success = true;
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error disabling NHI identity: " + e.getMessage());
            addActionError(e.getMessage());
            success = false;
            return Action.ERROR.toUpperCase();
        }
    }

    public String deleteNhiIdentities() {
        try {
            if (identityIds == null || identityIds.isEmpty()) {
                addActionError("Identity IDs are required");
                success = false;
                return Action.ERROR.toUpperCase();
            }

            List<ObjectId> objectIds = new ArrayList<>();
            for (String id : identityIds) {
                objectIds.add(new ObjectId(id));
            }

            Bson filter = Filters.in(NhiIdentity.ID, objectIds);
            NhiIdentityDao.instance.deleteAll(filter);

            loggerMaker.infoAndAddToDb("Deleted " + identityIds.size() + " NHI identities by user: " + getSUser().getLogin());

            success = true;
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error deleting NHI identities: " + e.getMessage());
            addActionError(e.getMessage());
            success = false;
            return Action.ERROR.toUpperCase();
        }
    }
}
