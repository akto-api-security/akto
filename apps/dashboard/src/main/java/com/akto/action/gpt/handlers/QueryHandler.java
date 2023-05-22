package com.akto.action.gpt.handlers;

import com.mongodb.BasicDBObject;


public interface QueryHandler {

    public BasicDBObject handleQuery(BasicDBObject meta) throws Exception;
}

