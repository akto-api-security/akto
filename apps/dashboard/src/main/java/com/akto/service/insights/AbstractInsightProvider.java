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
        r.setStatus(InsightResult.Status.NO_DATA.name());
        return r;
    }
}
