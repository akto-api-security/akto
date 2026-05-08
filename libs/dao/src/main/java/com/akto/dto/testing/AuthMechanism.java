package com.akto.dto.testing;

import com.akto.dao.context.Context;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.RecordedLoginFlowInput;
import com.akto.dto.test_editor.ExecutorSingleOperationResp;

import com.akto.util.enums.LoginFlowEnums;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AuthMechanism {
    private ObjectId id;
    public static final String OBJECT_ID = "objectId";
    private List<AuthParam> authParams;

    @BsonIgnore
    private List<AuthParam> authParamsCached;

    @BsonIgnore
    private int cacheExpiryEpoch;
    @BsonIgnore
    private int errorCacheExpiryEpoch;

    private String type;

    private ArrayList<RequestData> requestData;

    private String uuid;
    private List<Integer> apiCollectionIds;

    @BsonIgnore
    private RecordedLoginFlowInput recordedLoginFlowInput;

    public AuthMechanism() {
    }

    public AuthMechanism(List<AuthParam> authParams, ArrayList<RequestData> requestData, String type, List<Integer> apiCollectionIds) {
        this.authParams = authParams;
        this.requestData = requestData;
        this.type = type;
        this.uuid = UUID.randomUUID().toString();
        this.apiCollectionIds = apiCollectionIds;
    }

    public ExecutorSingleOperationResp addAuthToRequest(OriginalHttpRequest request, boolean useCached) {

        boolean shouldUseCachedAuth = useCached && !isCacheExpired();
        List<AuthParam> authParamsToUse = shouldUseCachedAuth ? authParamsCached: authParams;
        
        String messageKeysPresent = "";
        String messageKeysAbsent = "";
        boolean modifiedAtLeastOne = false;

        for (AuthParam authParam1: authParamsToUse) {
            if(authParam1.authTokenPresent(request)){
                authParam1.addAuthTokens(request);
                messageKeysPresent += authParam1.getKey()+", ";
                modifiedAtLeastOne = true;
            } else {
                messageKeysAbsent += authParam1.getKey()+", ";
            }
        }

        return new ExecutorSingleOperationResp(modifiedAtLeastOne, "keys present=[" + messageKeysPresent +"], absent=["+ messageKeysAbsent + "]");

    }

    public boolean isCacheExpired() {
        // take max of cacheExpiryEpoch and errorCacheExpiryEpoch, to check what is the latest expiry time
        int maxExpiryEpoch = Math.max(cacheExpiryEpoch, errorCacheExpiryEpoch);
        return maxExpiryEpoch <= (Context.now() + 30);
    }

    public boolean removeAuthFromRequest(OriginalHttpRequest request) {
        for (AuthParam authParamPair : authParams) {
            boolean result = authParamPair.removeAuthTokens(request);
            if (!result) return false;
        }
        return true;
    }

    public boolean authTokenPresent(OriginalHttpRequest request) {
        boolean result = false;
        for (AuthParam authParamPair : authParams) {
            result = result || authParamPair.authTokenPresent(request);
        }
        return result;
    }

    public List<AuthParam> getAuthParamsFromAuthMechanism() {
        boolean eligibleForCachedToken = LoginFlowEnums.AuthMechanismTypes.LOGIN_REQUEST.toString().equalsIgnoreCase(getType()) || LoginFlowEnums.AuthMechanismTypes.SAMPLE_DATA.toString().equalsIgnoreCase(getType());
        boolean shouldUseCachedAuth = eligibleForCachedToken && !isCacheExpired();
        List<AuthParam> authParamsToUse = shouldUseCachedAuth ? authParamsCached : authParams;

        return authParamsToUse;
    }

    public ObjectId getId() {
        return id;
    }

    public String getUuid() {
        return uuid;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setRequestData(ArrayList<RequestData> requestData) {
        this.requestData = requestData;
    }

    public List<AuthParam> getAuthParams() {
        return authParams;
    }

    public void setAuthParams(List<AuthParam> authParams) {
        this.authParams = authParams;
    }

    public void updateAuthParamsCached(List<AuthParam> authParamsCached) {
        this.authParamsCached = authParamsCached;
    }

    public void updateCacheExpiryEpoch(int cacheExpiryEpoch) {
        this.cacheExpiryEpoch = cacheExpiryEpoch;
        this.errorCacheExpiryEpoch = cacheExpiryEpoch; // assuming error cache expiry is same as cache expiry
    }

    public void updateErrorCacheExpiryEpoch(int errorCacheExpiryEpoch) {
        this.errorCacheExpiryEpoch = errorCacheExpiryEpoch;
    }

    public ArrayList<RequestData> getRequestData() {
        return this.requestData;
    }

    public String getType() {
        return this.type;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public List<Integer> getApiCollectionIds() {
        return apiCollectionIds;
    }

    public void setApiCollectionIds(List<Integer> apiCollectionIds) {
        this.apiCollectionIds = apiCollectionIds;
    }

    public RecordedLoginFlowInput getRecordedLoginFlowInput() {
        return recordedLoginFlowInput;
    }

    public void setRecordedLoginFlowInput(RecordedLoginFlowInput recordedLoginFlowInput) {
        this.recordedLoginFlowInput = recordedLoginFlowInput;
    }
}
