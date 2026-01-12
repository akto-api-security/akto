package com.akto.action.tpi;

import com.akto.action.UserAction;
import com.akto.dao.ConfigsDao;
import com.akto.dto.Config;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;

public class GoogleAuthAction extends UserAction {

    private BasicDBObject googleConfigResult;
    public String retrieveGoogleConfig() {
        Config.GoogleConfig googleConfig =(Config.GoogleConfig) ConfigsDao.instance.findOne(
                Filters.eq("configType", Config.ConfigType.GOOGLE.name())
        );
        if (googleConfig== null) return Action.ERROR.toUpperCase();

        googleConfigResult = new BasicDBObject();
        googleConfigResult.put("client_id",googleConfig.getClientId());
        return Action.SUCCESS.toUpperCase();
    }

    private String code;
    private String accessToken;
    private int thirdPartyId;

    public String sendGoogleAuthCodeToServer() {
        // Implementation deleted.
        // Not present in the new workflows.
        return Action.SUCCESS.toUpperCase();
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public int getThirdPartyId() {
        return thirdPartyId;
    }

    public void setThirdPartyId(int thirdPartyId) {
        this.thirdPartyId = thirdPartyId;
    }

    public BasicDBObject getGoogleConfigResult() {
        return googleConfigResult;
    }
}
