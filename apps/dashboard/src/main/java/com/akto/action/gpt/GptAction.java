package com.akto.action.gpt;

import com.akto.action.UserAction;
import com.akto.action.gpt.handlers.GptQuery;
import com.akto.action.gpt.handlers.QueryHandler;
import com.akto.action.gpt.handlers.QueryHandlerFactory;
import com.akto.dto.User;
import com.mongodb.BasicDBObject;
import org.slf4j.Logger;

public class GptAction extends UserAction {

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(GptAction.class);
    public static final String USER_EMAIL = "user_email";
    private String type;
    private BasicDBObject meta;
    private BasicDBObject response;

    public String askAi(){
        try {
            User sUser = getSUser();
            meta.put(USER_EMAIL, sUser.getLogin());
            GptQuery query = GptQuery.getQuery(type);
            QueryHandler queryHandler = QueryHandlerFactory.getQueryHandler(query);
            response = queryHandler.handleQuery(meta);
            if(response == null){
                throw new Exception("Received empty response from AI");
            }
            return SUCCESS.toUpperCase();
        }catch (Exception e){
            logger.error("Error while asking AI", e);
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }
    }


    public BasicDBObject getResponse() {
        return response;
    }

    public void setResponse(BasicDBObject response) {
        this.response = response;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BasicDBObject getMeta() {
        return meta;
    }

    public void setMeta(BasicDBObject meta) {
        this.meta = meta;
    }

}
