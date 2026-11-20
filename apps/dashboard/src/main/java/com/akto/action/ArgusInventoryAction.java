package com.akto.action;

import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.insights.InsightContext;
import com.akto.service.posture.ArgusInventoryService;
import com.akto.service.posture.ArgusInventorySource;
import com.akto.utils.ArgusCollectionScope;
import com.mongodb.BasicDBObject;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class ArgusInventoryAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ArgusInventoryAction.class, LogDb.DASHBOARD);

    private static final int WEEK_SECONDS = 7 * 86400;
    private static final int MAX_PAGE_SIZE = 500;
    private static final int DEFAULT_PAGE_SIZE = 50;

    private final ArgusInventorySource inventorySource = new ArgusInventorySource();

    @Getter @Setter private String flag;
    @Getter @Setter private String platform;
    @Getter @Setter private List<String> platforms;
    @Getter @Setter private String team;
    @Getter @Setter private String type;
    @Getter @Setter private String quad;
    @Getter @Setter private String used;
    @Getter @Setter private String tool;
    @Getter @Setter private Integer week;
    @Getter @Setter private String queryValue;
    @Getter @Setter private String sortKey;
    @Getter @Setter private int sortOrder;
    @Getter @Setter private int skip;
    @Getter @Setter private int limit;

    @Getter private BasicDBObject response = new BasicDBObject();

    /** The Summary and Analytics roll-up, with the quiet identities read live. */
    public String fetchArgusInventory() {
        return serve("Failed to load the agent inventory", service -> {
            BasicDBObject summary = service.buildSummary();
            summary.put("quiet", quiet());
            return summary;
        });
    }

    /** One page of the agents table. */
    public String fetchArgusInventoryAgents() {
        return serve("Failed to load the agents", service -> service.pageAgents(query()));
    }

    private String serve(String failureMessage, Function<ArgusInventoryService, BasicDBObject> read) {
        try {
            ArgusInventorySource.Snapshot snapshot = inventorySource.load(context());
            response = read.apply(new ArgusInventoryService(snapshot.getRows(), snapshot.getNow()));
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Argus inventory: " + failureMessage + ": " + e.getMessage());
            addActionError(failureMessage);
            return ERROR.toUpperCase();
        }
    }

    private InsightContext context() {
        int now = Context.now();
        return new InsightContext(Context.accountId.get(), Context.userId.get(), Context.contextSource.get(), now - WEEK_SECONDS, now);
    }

    // Identities are account-wide, so users limited to some collections get none.
    private BasicDBObject quiet() {
        if (ArgusCollectionScope.isLimited(getSUser())) return ArgusInventorySource.quietOf(0, Collections.emptyList());
        return inventorySource.quiet(Context.now());
    }

    private ArgusInventoryService.Query query() {
        ArgusInventoryService.Query query = new ArgusInventoryService.Query();
        query.setFlag(flag);
        query.setPlatform(platform);
        query.setPlatforms(platforms);
        query.setTeam(team);
        query.setType(type);
        query.setQuad(quad);
        query.setUsed(used);
        query.setTool(tool);
        query.setWeek(week);
        query.setText(queryValue);
        query.setSortKey(sortKey);
        query.setSortOrder(sortOrder);
        query.setSkip(skip);
        query.setLimit(limit <= 0 ? DEFAULT_PAGE_SIZE : Math.min(limit, MAX_PAGE_SIZE));
        return query;
    }

    @Override
    public String execute() {
        return SUCCESS.toUpperCase();
    }
}
