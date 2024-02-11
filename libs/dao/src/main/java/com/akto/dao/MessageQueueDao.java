package com.akto.dao;

import com.akto.dto.MessageQueueEntry;
import com.mongodb.BasicDBObject;

import java.util.List;

public class MessageQueueDao extends CommonContextDao<MessageQueueEntry> {

    public static final MessageQueueDao instance = new MessageQueueDao();

    @Override
    public String getCollName() {
        return "message_queue";
    }

    @Override
    public Class<MessageQueueEntry> getClassT() {
        return MessageQueueEntry.class;
    }

    public List<MessageQueueEntry> getAllEntries() {
        return findAll(new BasicDBObject());
    }
}
