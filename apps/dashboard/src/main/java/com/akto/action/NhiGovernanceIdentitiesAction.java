package com.akto.action;

import com.akto.dao.nhi_governance.NhiIdentityDao;
import com.akto.dto.nhi_governance.NhiIdentity;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import lombok.Getter;
import lombok.Setter;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.List;

public class NhiGovernanceIdentitiesAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(NhiGovernanceIdentitiesAction.class, LogDb.DASHBOARD);

    @Getter
    private List<NhiIdentity> identities;

    @Getter
    private boolean success = false;

    @Setter
    private String identityId;

    public String fetchNhiIdentities() {
        try {
            identities = NhiIdentityDao.instance.findAll(Filters.empty());
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching NHI identities: " + e.getMessage());
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
}
