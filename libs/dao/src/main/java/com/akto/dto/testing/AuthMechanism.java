package com.akto.dto.testing;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.RecordedLoginFlowInput;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AuthMechanism {
    private ObjectId id;
    public static final String OBJECT_ID = "objectId";
    private List<AuthParam> authParams;

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

    public AuthMechanism copy() {
        List<AuthParam> newAuthParams = new ArrayList<>();
        for (AuthParam authParam : authParams) {
            newAuthParams.add(authParam.copy());
        }
        return new AuthMechanism(newAuthParams, new ArrayList<>(requestData), type, apiCollectionIds == null? null :new ArrayList<>(apiCollectionIds));
    }

    public boolean addAuthToRequest(OriginalHttpRequest request) {
        for (AuthParam authParamPair : authParams) {
            boolean result = authParamPair.addAuthTokens(request);
            if (!result) return false;
        }
        return true;
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

    public AuthParam fetchFirstAuthParam() {
        return authParams.get(0);
    }

    //public AuthParam set

    public void setAuthParams(List<AuthParam> authParams) {
        this.authParams = authParams;
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
