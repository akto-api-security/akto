package com.akto.dto.testing;

import com.akto.dao.testing.EndpointLogicalGroupDao;
// import com.akto.data_actor.DataActor;
// import com.akto.data_actor.DataActorFactory;
import com.akto.dto.RawApi;
import com.akto.dto.testing.sources.AuthWithCond;
import com.mongodb.client.model.Filters;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.common.AuthPolicy;
import com.akto.dto.*;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import static com.akto.util.Constants.default_token;

import static com.akto.util.Constants.ID;

public class TestRoles {
    private static final Logger log = LoggerFactory.getLogger(TestRoles.class);
    private ObjectId id;
    @BsonIgnore
    private String hexId;
    public static final String NAME = "name";
    private String name;
    private ObjectId endpointLogicalGroupId;
    public static final String AUTH_WITH_COND_LIST = "authWithCondList";
    private List<AuthWithCond> authWithCondList;
    @BsonIgnore
    private EndpointLogicalGroup endpointLogicalGroup;
    
    @BsonIgnore
    private String endpointLogicalGroupIdHexId;

    private String createdBy;
    private int createdTs;
    public static final String LAST_UPDATED_TS = "lastUpdatedTs";
    private int lastUpdatedTs;
    private List<Integer> apiCollectionIds;
    public TestRoles(){}
    public TestRoles(ObjectId id, String name, ObjectId endpointLogicalGroupId, List<AuthWithCond> authWithCondList, String createdBy, int createdTs, int lastUpdatedTs, List<Integer> apiCollectionIds) {
        this.id = id;
        this.name = name;
        this.endpointLogicalGroupId = endpointLogicalGroupId;
        this.authWithCondList = authWithCondList;
        this.createdBy = createdBy;
        this.createdTs = createdTs;
        this.lastUpdatedTs = lastUpdatedTs;
        this.apiCollectionIds = apiCollectionIds;
	}
    
    public EndpointLogicalGroup fetchEndpointLogicalGroup() {
        if (this.endpointLogicalGroup == null) {
            this.endpointLogicalGroup = EndpointLogicalGroupDao.instance.findOne(Filters.eq(ID, this.endpointLogicalGroupId));
        }
        return this.endpointLogicalGroup;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ObjectId getEndpointLogicalGroupId() {
        return endpointLogicalGroupId;
    }

    public void setEndpointLogicalGroupId(ObjectId endpointLogicalGroupId) {
        this.endpointLogicalGroupId = endpointLogicalGroupId;
    }

    public EndpointLogicalGroup getEndpointLogicalGroup() {
        return endpointLogicalGroup;
    }

    public void setEndpointLogicalGroup(EndpointLogicalGroup endpointLogicalGroup) {
        this.endpointLogicalGroup = endpointLogicalGroup;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public int getCreatedTs() {
        return createdTs;
    }

    public void setCreatedTs(int createdTs) {
        this.createdTs = createdTs;
    }

    public int getLastUpdatedTs() {
        return lastUpdatedTs;
    }

    public void setLastUpdatedTs(int lastUpdatedTs) {
        this.lastUpdatedTs = lastUpdatedTs;
    }

    public List<AuthWithCond> getAuthWithCondList() {
        return authWithCondList;
    }

    public void setAuthWithCondList(List<AuthWithCond> authWithCondList) {
        this.authWithCondList = authWithCondList;
    }

    public String getHexId() {
        if (hexId == null) return this.id.toHexString();
        return this.hexId;
    }

    public void setHexId(String hexId) {
        this.hexId = hexId;
    }
    public List<Integer> getApiCollectionIds() {
        return apiCollectionIds;
    }

    public void setApiCollectionIds(List<Integer> apiCollectionIds) {
        this.apiCollectionIds = apiCollectionIds;
    }

    public String getEndpointLogicalGroupIdHexId() {
        if (endpointLogicalGroupIdHexId == null) return this.endpointLogicalGroupId.toHexString();
        return this.endpointLogicalGroupIdHexId;
    }

    public void setEndpointLogicalGroupIdHexId(String endpointLogicalGroupIdHexId) {
        this.endpointLogicalGroupIdHexId = endpointLogicalGroupIdHexId;
    }

    public static HttpResponseParams createResponseParamsFromRawApi(RawApi rawApi) {
        if (rawApi == null || rawApi.getResponse() == null) {
            return null;
        }
        HttpResponseParams responseParams = new HttpResponseParams();
        responseParams.setStatusCode(rawApi.getResponse().getStatusCode());
        responseParams.setHeaders(rawApi.getResponse().getHeaders());
        responseParams.setPayload(rawApi.getResponse().getBody());
        HttpRequestParams requestParams = new HttpRequestParams();
        requestParams.setHeaders(rawApi.fetchReqHeaders());
        responseParams.setRequestParams(requestParams);
        return responseParams;

    }

    public static ApiInfo createApiInfoFromRawApi(HttpResponseParams httpResponseParams) {
        if (httpResponseParams == null || httpResponseParams.requestParams == null) {
            return null;
        }
        return new ApiInfo(httpResponseParams);
    }
}
