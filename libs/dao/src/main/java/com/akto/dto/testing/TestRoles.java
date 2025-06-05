package com.akto.dto.testing;

import com.akto.dao.testing.EndpointLogicalGroupDao;
import com.akto.dto.RawApi;
import com.akto.dto.testing.sources.AuthWithCond;
import com.mongodb.client.model.Filters;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

import static com.akto.util.Constants.ID;

public class TestRoles {
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

    public static final String CREATED_BY = "createdBy";
    private String createdBy;
    private int createdTs;
    public static final String LAST_UPDATED_TS = "lastUpdatedTs";
    private int lastUpdatedTs;
    private List<Integer> apiCollectionIds;

    public static final String SCOPE_ROLES = "scopeRoles";
    private List<String> scopeRoles;

    public static final String LAST_UPDATED_BY = "lastUpdatedBy";
    private String lastUpdatedBy;
    
    public TestRoles(){}
    
    public TestRoles(ObjectId id, String name, ObjectId endpointLogicalGroupId, List<AuthWithCond> authWithCondList, String createdBy, int createdTs, int lastUpdatedTs, List<Integer> apiCollectionIds, String lastUpdatedBy) {
        this.id = id;
        this.name = name;
        this.endpointLogicalGroupId = endpointLogicalGroupId;
        this.authWithCondList = authWithCondList;
        this.createdBy = createdBy;
        this.createdTs = createdTs;
        this.lastUpdatedTs = lastUpdatedTs;
        this.apiCollectionIds = apiCollectionIds;
        this.lastUpdatedBy = lastUpdatedBy;
	}
    
    public EndpointLogicalGroup fetchEndpointLogicalGroup() {
        if (this.endpointLogicalGroup == null) {
            this.endpointLogicalGroup = EndpointLogicalGroupDao.instance.findOne(Filters.eq(ID, this.endpointLogicalGroupId));
        }
        return this.endpointLogicalGroup;
    }

    public AuthMechanism findDefaultAuthMechanism() {
        try {
            for(AuthWithCond authWithCond: this.getAuthWithCondList()) {
                if (authWithCond.getHeaderKVPairs().isEmpty()) {
                    AuthMechanism ret = authWithCond.getAuthMechanism();
                    if(authWithCond.getRecordedLoginFlowInput()!=null){
                        ret.setRecordedLoginFlowInput(authWithCond.getRecordedLoginFlowInput());
                    }

                    return ret;
                }
            }
        } catch (Exception e) {
            return null;
        }

        return null;
    }

    public AuthMechanism findMatchingAuthMechanism(RawApi rawApi) {
        if (rawApi == null) {
            return findDefaultAuthMechanism();
        }

        for(AuthWithCond authWithCond: this.getAuthWithCondList()) {

            try {
                boolean allSatisfied = true;

                if (authWithCond.getHeaderKVPairs().isEmpty()) {
                    continue;
                }

                for(String headerKey: authWithCond.getHeaderKVPairs().keySet()) {
                    String headerVal = authWithCond.getHeaderKVPairs().get(headerKey);
                    List<String> rawHeaderValue = rawApi.getRequest().getHeaders().getOrDefault(headerKey.toLowerCase(), new ArrayList<>());
                    if (!rawHeaderValue.contains(headerVal)) {
                        allSatisfied = false;
                        break;
                    }
                }

                if (allSatisfied) {
                    AuthMechanism ret = authWithCond.getAuthMechanism();
                    if(authWithCond.getRecordedLoginFlowInput()!=null){
                        ret.setRecordedLoginFlowInput(authWithCond.getRecordedLoginFlowInput());
                    }
                    return ret;
                }
            } catch (Exception e) {
                // Handle exception if needed
            }
        }
        
        return findDefaultAuthMechanism();
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

    public List<String> getScopeRoles() {
        return scopeRoles;
    }   

    public void setScopeRoles(List<String> scopeRoles) {
        this.scopeRoles = scopeRoles;
    }

    public String getLastUpdatedBy() {
        return lastUpdatedBy;
    }

    public void setLastUpdatedBy(String lastUpdatedBy) {
        this.lastUpdatedBy = lastUpdatedBy;
    }
}
