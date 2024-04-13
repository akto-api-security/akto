package com.akto.dto;

import com.akto.dao.context.Context;
import org.bson.codecs.pojo.annotations.BsonId;

public class Team {

    public enum UserType {
        MEMBER, VISITOR, GUEST
    }

    @BsonId
    int id;
    String teamName;
    int creationTs;
    int ownerId;

    public Team() {}

    public Team(int id, String teamName, int ownerId) {
        this.id = id;
        this.teamName = teamName;
        this.creationTs = Context.now();
        this.ownerId = ownerId;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public int getCreationTs() {
        return creationTs;
    }

    public void setCreationTs(int creationTs) {
        this.creationTs = creationTs;
    }

    public int getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(int ownerId) {
        this.ownerId = ownerId;
    }
}
