package com.akto.service.insights;

import com.akto.dao.context.Context;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

/**
 * Request-scoped identity + window for one insights call. Captured once on the calling
 * (Struts action) thread via fromThreadLocals, then explicitly re-applied inside any worker
 * thread a provider spawns — Context.accountId/userId/contextSource are plain ThreadLocals and
 * do not propagate across an executor boundary on their own.
 */
public class InsightContext {

    private final int accountId;
    private final int userId;
    private final CONTEXT_SOURCE contextSource;
    private final int startTs;
    private final int endTs;

    public InsightContext(int accountId, int userId, CONTEXT_SOURCE contextSource, int startTs, int endTs) {
        this.accountId = accountId;
        this.userId = userId;
        this.contextSource = contextSource;
        this.startTs = startTs;
        this.endTs = endTs;
    }

    public static InsightContext fromThreadLocals(int startTs, int endTs) {
        Integer accountId = Context.accountId.get();
        Integer userId = Context.userId.get();
        return new InsightContext(
                accountId != null ? accountId : 0,
                userId != null ? userId : 0,
                Context.contextSource.get(),
                startTs,
                endTs);
    }

    /** Sets this context's fields onto the calling thread's Context ThreadLocals. */
    public void applyToCurrentThread() {
        Context.accountId.set(accountId);
        Context.userId.set(userId);
        Context.contextSource.set(contextSource);
    }

    public int getAccountId() {
        return accountId;
    }

    public int getUserId() {
        return userId;
    }

    public CONTEXT_SOURCE getContextSource() {
        return contextSource;
    }

    public int getStartTs() {
        return startTs;
    }

    public int getEndTs() {
        return endTs;
    }
}
