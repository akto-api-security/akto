package com.akto.dto;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonIgnore;

import com.akto.dao.context.Context;
import com.akto.dto.CollectionConditions.CollectionCondition;

public class ApiCollection {

    @BsonId
    int id;
    public static final String NAME = "name";
    String name;
    int startTs;
    Set<String> urls;
    public static final String URLS_STRING = "urls";
    String hostName;
    public static final String HOST_NAME = "hostName";
    int vxlanId;

    @BsonIgnore
    int urlsCount;
    public static final String VXLAN_ID = "vxlanId";

    @BsonIgnore
    Type type;

    public enum Type {
        TRAFFIC, CUSTOM, API_GROUP
    }

    public Type getType() {
        if (this.hostName != null || this.vxlanId != 0) {
            return Type.TRAFFIC;
        } else if (this.conditions != null) {
            return Type.API_GROUP;
        }
        return Type.CUSTOM;
    }

    List<CollectionCondition> conditions;
    public static final String CONDITIONS_STRING = "conditions";

    public ApiCollection() {
    }

    public ApiCollection(int id, String name, int startTs, Set<String> urls, String hostName, int vxlanId) {
        this.id = id;
        this.name = name;
        this.startTs = startTs;
        this.urls = urls;
        this.hostName = hostName;
        this.vxlanId = vxlanId;
    }

    public ApiCollection(int id, String name, List<CollectionCondition> conditions) {
        this.id = id;
        this.name = name;
        this.conditions = conditions;
    }

    public static boolean useHost = Objects.equals(System.getenv("USE_HOSTNAME"), "true");

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getStartTs() {
        return this.startTs;
    }

    public void setStartTs(int startTs) {
        this.startTs = startTs;
    }

    public Set<String> getUrls() {
        return this.urls;
    }

    public void setUrls(Set<String> urls) {
        this.urls = urls;
    }

    @Override
    public String toString() {
        return "{" +
            " id='" + getId() + "'" +
            ", name='" + getName() + "'" +
            ", startTs='" + getStartTs() + "'" +
            ", urls='" + getUrls() + "'" +
            "}";
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public int getVxlanId() {
        return vxlanId;
    }

    public void setVxlanId(int vxlanId) {
        this.vxlanId = vxlanId;
    }

    public int getUrlsCount() {
        return urlsCount;
    }

    public void setUrlsCount(int urlsCount) {
        this.urlsCount = urlsCount;
    }

    // to be used in front end
    public String getDisplayName() {
        String result;
        if (this.hostName != null) {
            result = this.hostName + " - " + this.name;
        } else {
            result = this.name + "";
        }

        if (this.hostName == null || this.name == null) {
            result = result.replace(" - ", "");
        }

        result = result.replace("null", "");
        return result;
    }

    // To be called if you are creating a collection that is not from mirroring
    public static ApiCollection createManualCollection(int id, String name){
        return new ApiCollection(id, name, Context.now() , new HashSet<>(),  null, 0);
    }

    public List<CollectionCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<CollectionCondition> conditions) {
        this.conditions = conditions;
    }

}
