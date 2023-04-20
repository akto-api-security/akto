package com.akto.action.gpt;

import com.akto.action.UserAction;
import com.akto.dao.AktoGptConfigDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dto.gpt.AktoGptConfig;
import com.akto.dto.ApiCollection;
import com.akto.dto.gpt.AktoGptConfigState;
import com.mongodb.BasicDBObject;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GptConfigAction extends UserAction {

    private int apiCollectionId;

    private List<BasicDBObject> currentState;

    public static final AktoGptConfigState DEFAULT_STATE = AktoGptConfigState.ENABLED;

    private static final Logger logger = LoggerFactory.getLogger(GptConfigAction.class);

    private List<BasicDBObject> fetchUpdatedAktoGptConfigs(){
        List<AktoGptConfig> aktoGptConfigList = AktoGptConfigDao.instance.findAll(new BasicDBObject());
        List<ApiCollection> apiCollectionList = ApiCollectionsDao.instance.findAll(new BasicDBObject(),
                new BasicDBObject("_id", true).append(ApiCollection.NAME, true));
        Map<Integer, ApiCollection> apiCollectionMap = new HashMap<>();
        for (ApiCollection apiCollection : apiCollectionList) {
            apiCollectionMap.put(apiCollection.getId(), apiCollection);
        }
        List<BasicDBObject> updatedAktoGptConfigList = new ArrayList<>();
        for (AktoGptConfig aktoGptConfig : aktoGptConfigList) {
            ApiCollection apiCollection = apiCollectionMap.remove(aktoGptConfig.getId());
            BasicDBObject obj = new BasicDBObject("id", aktoGptConfig.getId())
                    .append("state", aktoGptConfig.getState().toString())
                    .append("collectionName", apiCollection.getName());
            updatedAktoGptConfigList.add(obj);
        }
        for (Integer apiCollectionId : apiCollectionMap.keySet()) {
            //These collection ids dont exist in AktoGptconfigDao, store them in DB with DEFAULT value
            //also store these entries in DB
            AktoGptConfig aktoGptConfig = new AktoGptConfig(apiCollectionId, DEFAULT_STATE);
            AktoGptConfigDao.instance.insertOne(aktoGptConfig);
            BasicDBObject obj = new BasicDBObject("id", aktoGptConfig.getId())
                    .append("state", aktoGptConfig.getState().toString())
                    .append("collectionName", apiCollectionMap.get(apiCollectionId).getName());
            updatedAktoGptConfigList.add(obj);
        }
        return updatedAktoGptConfigList;
    }

    public String fetchAktoGptConfig(){
        if(apiCollectionId == -1){
            currentState = fetchUpdatedAktoGptConfigs();
        } else {
            AktoGptConfig aktoGptConfig = AktoGptConfigDao.instance.findOne(new BasicDBObject("_id", apiCollectionId));
            if(aktoGptConfig == null) {
                aktoGptConfig = new AktoGptConfig(apiCollectionId, DEFAULT_STATE);
                AktoGptConfigDao.instance.insertOne(aktoGptConfig);
            }
            String collectionName = ApiCollectionsDao.instance.findOne(new BasicDBObject("_id", apiCollectionId)).getName();
            currentState = Collections.singletonList(new BasicDBObject("id", aktoGptConfig.getId())
                    .append("state", aktoGptConfig.getState().toString())
                    .append("collectionName", collectionName));
        }
        logger.info("Current state of AktoGptConfig is {}", currentState);
        return SUCCESS.toUpperCase();
    }

    public String saveAktoGptConfig(){
        for(BasicDBObject aktoGptConfig : currentState){
            AktoGptConfigDao.instance.updateOne(new BasicDBObject("_id", aktoGptConfig.get("id")),
                    new BasicDBObject("$set", new BasicDBObject("state", aktoGptConfig.get("state"))));
        }
        currentState = fetchUpdatedAktoGptConfigs();
        logger.info("Current state of AktoGptConfig is {}", currentState);
        return SUCCESS.toUpperCase();
    }

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public List<BasicDBObject> getCurrentState() {
        return currentState;
    }

    public void setCurrentState(List<BasicDBObject> currentState) {
        this.currentState = currentState;
    }
}
