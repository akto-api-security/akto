package com.akto.dao;

import com.akto.dto.messaging.Message;

public class MessagesDao extends AccountsContextDao<Message> {

    public static final MessagesDao instance = new MessagesDao();

    @Override
    public String getCollName() {
        return "messages";
    }

    @Override
    public Class<Message> getClassT() {
        return Message.class;
    }
}
