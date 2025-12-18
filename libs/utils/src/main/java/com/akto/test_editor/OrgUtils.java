package com.akto.test_editor;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.billing.Organization;
import com.akto.dto.billing.Tokens;
import com.mongodb.BasicDBObject;

public class OrgUtils {

    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    public static BasicDBObject getBillingTokenForAuth() {
        BasicDBObject bDObject;
        int accountId = Context.getActualAccountId();
        Organization organization = com.akto.usage.OrgUtils.getOrganizationCached(accountId);
        if (organization == null) {
            return new BasicDBObject("error", "organization not found");
        }
        String errMessage = "";
        Tokens tokens = dataActor.fetchToken(organization.getId(), accountId);
        if (tokens == null) {
            errMessage = "error extracting ${akto_header}, token is missing";
            return new BasicDBObject("error", errMessage);
        }
        if (tokens.isOldToken()) {
            errMessage = "error extracting ${akto_header}, token is old";
        }
        if (errMessage.length() > 0) {
            bDObject = new BasicDBObject("error", errMessage);
        } else {
            bDObject = new BasicDBObject("token", tokens.getToken());
        }
        return bDObject;
    }

}
