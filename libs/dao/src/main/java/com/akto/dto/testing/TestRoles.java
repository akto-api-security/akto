package com.akto.dto.testing;

import com.akto.dao.testing.EndpointLogicalGroupDao;
import com.akto.dto.RawApi;
import com.akto.dto.testing.sources.AuthWithCond;
import com.akto.util.Pair;
import com.mongodb.client.model.Filters;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.Map;

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

    public AuthMechanism getDefaultAuthMechanism(){
        if(this.getAuthWithCondList() != null && !this.getAuthWithCondList().isEmpty()){
            for (AuthWithCond authWithCond : this.getAuthWithCondList()) {
                if(authWithCond.getHeaderKVPairs() != null && authWithCond.getHeaderKVPairs().isEmpty()){
                    return authWithCond.getAuthMechanism();
                }
            }
        }
        return null;
    }

    public Pair<AuthMechanism, Integer> findMatchinAuthMechanism(RawApi rawApi) {
        if (this.getAuthWithCondList().size() > 0) {
            int index = 0;
            List<AuthWithCond> authWithCondList = this.getAuthWithCondList();
            AuthWithCond firstAuth = authWithCondList.get(0);
            AuthMechanism defaultAuthMechanism = firstAuth.getAuthMechanism();
            if(firstAuth.getRecordedLoginFlowInput()!=null){
                defaultAuthMechanism.setRecordedLoginFlowInput(firstAuth.getRecordedLoginFlowInput());
            }
            if (rawApi == null) {
                return new Pair<>(defaultAuthMechanism, 0);
            } else {
                try {
                    Map<String, List<String>> reqHeaders = rawApi.getRequest().getHeaders();
                    for (AuthWithCond authWithCond: authWithCondList)  {
                        Map<String, String> headerKVPairs = authWithCond.getHeaderKVPairs();
                        if (headerKVPairs == null) continue;

                        boolean allHeadersMatched = true;
                        for(String hKey: headerKVPairs.keySet()) {
                            String hVal = authWithCond.getHeaderKVPairs().get(hKey);
                            if (reqHeaders.containsKey(hKey.toLowerCase())) {
                                if (!reqHeaders.get(hKey.toLowerCase()).contains(hVal)) {
                                    allHeadersMatched = false;
                                    break;
                                }
                            }
                        }

                        if (allHeadersMatched) {
                            defaultAuthMechanism = authWithCond.getAuthMechanism();
                            if(authWithCond.getRecordedLoginFlowInput()!=null){
                                defaultAuthMechanism.setRecordedLoginFlowInput(authWithCond.getRecordedLoginFlowInput());
                            }
                            return new Pair<>(defaultAuthMechanism, index);
                        }
                        index++;
                    }
                } catch (Exception e) {
                    return new Pair<>(defaultAuthMechanism, 0);
                }
            }

            return new Pair<>(defaultAuthMechanism, 0);
        }

        return null;        
    }
}
