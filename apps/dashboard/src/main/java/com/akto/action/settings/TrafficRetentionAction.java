package com.akto.action.settings;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.trafficcleanup.TrafficRetentionPreview;
import com.akto.service.trafficcleanup.TrafficRetentionService;

import lombok.Getter;
import lombok.Setter;

/**
 * Delete APIs with no traffic since a cutoff. Preview and start are separate routes so that authz
 * and the audit log can tell a dry run from a delete (see CLAUDE.md in service/trafficcleanup).
 */
public class TrafficRetentionAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(TrafficRetentionAction.class, LogDb.DASHBOARD);

    @Setter
    private int days;

    @Getter
    @Setter
    private int cutoffEpoch;

    @Getter
    private TrafficRetentionPreview preview;

    @Getter
    private boolean started;

    public String previewTrafficRetentionCleanup() {
        try {
            this.preview = TrafficRetentionService.instance.preview(days, Context.now());
            return SUCCESS.toUpperCase();
        } catch (IllegalArgumentException e) {
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Traffic retention preview failed");
            addActionError("Could not evaluate which APIs are older than the cutoff");
            return ERROR.toUpperCase();
        }
    }

    public String startTrafficRetentionCleanup() {
        try {
            int accountId = Context.accountId.get();
            if (!TrafficRetentionService.instance.startDelete(accountId, cutoffEpoch, Context.now())) {
                addActionError("A traffic cleanup is already running for this account");
                return ERROR.toUpperCase();
            }
            this.started = true;
            return SUCCESS.toUpperCase();
        } catch (IllegalArgumentException e) {
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Traffic retention cleanup could not be started");
            addActionError("Could not start the traffic cleanup");
            return ERROR.toUpperCase();
        }
    }
}
