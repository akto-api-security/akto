package com.akto.dto.testing;

import com.akto.dao.testing.EndpointLogicalGroupDao;
import com.mongodb.client.model.Filters;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import static com.akto.util.Constants.ID;

public class TestRoles {
    private ObjectId id;
    public static final String NAME = "name";
    private String name;
    private ObjectId endpointLogicalGroupId;
    private AuthMechanism authMechanism;
    @BsonIgnore
    private EndpointLogicalGroup endpointLogicalGroup;
    private String createdBy;
    private int createdTs;
    public static final String LAST_UPDATED_TS = "lastUpdatedTs";
    private int lastUpdatedTs;
    public TestRoles(){}
    public TestRoles(ObjectId id, String name, ObjectId endpointLogicalGroupId, AuthMechanism authMechanism, String createdBy, int createdTs, int lastUpdatedTs) {
        this.id = id;
        this.name = name;
        this.endpointLogicalGroupId = endpointLogicalGroupId;
        this.authMechanism = authMechanism;
        this.createdBy = createdBy;
        this.createdTs = createdTs;
        this.lastUpdatedTs = lastUpdatedTs;
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

    public AuthMechanism getAuthMechanism() {
        return authMechanism;
    }

    public void setAuthMechanism(AuthMechanism authMechanism) {
        this.authMechanism = authMechanism;
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
}
