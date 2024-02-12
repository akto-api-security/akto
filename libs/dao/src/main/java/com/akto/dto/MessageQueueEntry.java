package com.akto.dto;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.messaging.*;
import com.akto.dto.notifications.content.StringContent;
import com.mongodb.BasicDBList;
import com.mongodb.client.result.InsertOneResult;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.mongodb.client.model.Filters.eq;

@BsonDiscriminator
public abstract class MessageQueueEntry {
    int accountId;
    InboxParams params;
    int sentTs;
    int creationTs = (int) (System.currentTimeMillis()/1000l);
    boolean selfInform = false;

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public InboxParams getParams() {
        return params;
    }

    public void setParams(InboxParams params) {
        this.params = params;
    }

    public int getCreationTs() {
        return creationTs;
    }

    public void setCreationTs(int creationTs) {
        this.creationTs = creationTs;
    }

    public int getSentTs() {
        return sentTs;
    }

    public boolean isSelfInform() {
        return selfInform;
    }

    public void setSelfInform(boolean selfInform) {
        this.selfInform = selfInform;
    }

    public void setSentTs(int sentTs) {
        this.sentTs = sentTs;
    }

    public void schedule() {
        InsertOneResult insertOneResult = MessageQueueDao.instance.insertOne(this);
    }

    public abstract Message toMessage();
}
