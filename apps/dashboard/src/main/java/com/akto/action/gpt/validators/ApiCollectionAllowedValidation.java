package com.akto.action.gpt.validators;

import com.akto.action.gpt.GptConfigAction;
import com.akto.dao.AktoGptConfigDao;
import com.akto.dto.gpt.AktoGptConfig;
import com.akto.dto.gpt.AktoGptConfigState;
import com.mongodb.BasicDBObject;

public class ApiCollectionAllowedValidation implements ValidateQuery {
    @Override
    public boolean validate(BasicDBObject meta) {
        int apiCollectionId = meta.getInt("apiCollectionId", -1);
        if(apiCollectionId == -1) {
            return false;
        }
        AktoGptConfig aktoGptConfig = AktoGptConfigDao.instance.findOne(new BasicDBObject("_id", apiCollectionId));
        if(aktoGptConfig == null) {
            aktoGptConfig = new AktoGptConfig(apiCollectionId, GptConfigAction.DEFAULT_STATE);
        }
        return aktoGptConfig.getState() == AktoGptConfigState.ENABLED;
    }

    @Override
    public String getErrorMessage() {
        return "Api collection is not allowed, please enable Api collection by going to Settings -> Akto GPT -> Api Collection";
    }
}
