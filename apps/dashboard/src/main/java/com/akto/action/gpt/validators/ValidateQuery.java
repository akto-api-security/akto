package com.akto.action.gpt.validators;

import com.mongodb.BasicDBObject;

public interface ValidateQuery {
    boolean validate(BasicDBObject meta);

    String getErrorMessage();
}
