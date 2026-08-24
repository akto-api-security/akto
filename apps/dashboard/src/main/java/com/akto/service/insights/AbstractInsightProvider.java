package com.akto.service.insights;

/**
 * Shared boilerplate for the 10 providers: id()/providerVersion() plumbing and a
 * pre-filled InsightResult skeleton, so each provider file only contains the logic
 * that actually differs between insights.
 */
public abstract class AbstractInsightProvider implements InsightProvider {

    private final InsightId id;
    private final int version;

    protected AbstractInsightProvider(InsightId id, int version) {
        this.id = id;
        this.version = version;
    }

    @Override
    public final InsightId id() { return id; }

    @Override
    public final int providerVersion() { return version; }

    protected InsightResult skeleton() {
        InsightResult r = new InsightResult();
        r.setInsightId(id.name());
        r.setTitle(id.getTitle());
        r.setCategory(id.getCategory().name());
        r.setGroup(id.getGroup().name());
        r.setStatus(InsightResult.Status.NO_DATA.name());
        return r;
    }

    /** A skeleton with a specific, reportable reason a required read failed — more useful to a
     *  reader than the generic "This insight could not be computed." InsightService falls back to
     *  on an uncaught exception. */
    protected InsightResult failed(String source, String reason) {
        InsightResult r = skeleton();
        r.addDataGap(new InsightResult.Gap(source, "REQUEST_FAILED", reason));
        r.setHeadline(reason);
        return r;
    }
}
