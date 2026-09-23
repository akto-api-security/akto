package com.akto.action.threat_detection;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.posture.ComplianceClauseScanService;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;

import lombok.Getter;
import lombok.Setter;

/**
 * Triggers the Framework readiness compliance-clause scan: reads real guardrail-violation traffic
 * for the given window and asks an LLM which sub-clauses of each mapped compliance framework were
 * actually exercised (see ComplianceClauseScanService). Fire-and-forget — the request only starts
 * the background job and returns; there is no run registry or poll endpoint. Progress and outcome
 * are logged (see ComplianceClauseScanService), not surfaced back to the caller, so losing the
 * in-flight state on a dashboard restart costs nothing to check — the next scheduled/manual run
 * simply reads the logs or the stored ComplianceClauseCoverage docs directly.
 */
public class ComplianceClauseScanAction extends AbstractThreatDetectionAction {

    private static final LoggerMaker loggerMaker =
        new LoggerMaker(ComplianceClauseScanAction.class, LogDb.DASHBOARD);

    /** Bounded so several accounts scanning at once cannot stampede the LLM provider. */
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Setter
    private Integer startTimestamp;

    @Setter
    private Integer endTimestamp;

    @Getter
    private BasicDBObject scanResult;

    public String startComplianceClauseScan() {
        if (startTimestamp == null || endTimestamp == null || endTimestamp <= startTimestamp) {
            addActionError("A valid startTimestamp/endTimestamp window is required");
            return ERROR.toUpperCase();
        }

        int accountId = Context.accountId.get();
        CONTEXT_SOURCE contextSource = Context.contextSource.get() != null
                ? Context.contextSource.get() : CONTEXT_SOURCE.ENDPOINT;
        int startTs = startTimestamp;
        int endTs = endTimestamp;

        executor.submit(() -> {
            // The worker runs outside the request, so the account context has to be re-established
            // or every Mongo/HTTP call inside the scan resolves against the wrong tenant.
            Context.accountId.set(accountId);
            Context.contextSource.set(contextSource);
            try {
                ComplianceClauseScanService.run(accountId, contextSource, startTs, endTs);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Compliance clause scan failed: " + e.getMessage());
            }
        });

        scanResult = new BasicDBObject("status", "STARTED");
        return SUCCESS.toUpperCase();
    }
}
