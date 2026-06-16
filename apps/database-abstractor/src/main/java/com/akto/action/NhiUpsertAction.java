package com.akto.action;

import com.akto.dto.nhi_governance.NhiIdentity;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.nhi_governance.NhiIdentityUpsertService;
import com.akto.utils.nhi_governance.NhiIdentityUpsertService.BatchResult;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class NhiUpsertAction extends ActionSupport {

    private static final LoggerMaker logger = new LoggerMaker(NhiUpsertAction.class, LogDb.DASHBOARD);

    @Setter
    private NhiIdentity nhiIdentity;

    @Setter
    private List<NhiIdentity> nhiIdentities;

    @Getter
    private int upsertedCount;

    @Getter
    private int skippedCount;

    @Getter
    private boolean success = false;

    public String upsertNhiIdentity() {
        if (nhiIdentity == null) {
            addActionError("nhiIdentity is required");
            return Action.ERROR.toUpperCase();
        }
        try {
            NhiIdentityUpsertService.upsertOne(nhiIdentity);
            success = true;
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("upsertNhiIdentity failed: " + e.getMessage());
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String upsertNhiIdentities() {
        if (nhiIdentities == null || nhiIdentities.isEmpty()) {
            addActionError("nhiIdentities is required");
            return Action.ERROR.toUpperCase();
        }
        try {
            BatchResult r = NhiIdentityUpsertService.upsertMany(nhiIdentities);
            this.upsertedCount = r.upserted;
            this.skippedCount = r.skipped;
            success = true;
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("upsertNhiIdentities failed: " + e.getMessage());
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }
}
