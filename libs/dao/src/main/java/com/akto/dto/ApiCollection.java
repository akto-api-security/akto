package com.akto.dto;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonIgnore;

public class ApiCollection {

    @BsonId
    int id;
    public static final String NAME = "name";
    String name;
    int startTs;
    Set<String> urls;
    String hostName;
    public static final String HOST_NAME = "hostName";
    int vxlanId;
    public static final String VXLAN_ID = "vxlanId";

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

    // to be used in front end
    public String getDisplayName() {
        String result;
        if (this.hostName != null) {
            result = this.hostName + " - " + this.name;
        } else {
            result = this.name + "";
        }

        if (this.hostName == null || this.name == null) {
            result = result.replace(" - ", " ");
        }

        result = result.replace("null", "");
        return result;
    }


}
