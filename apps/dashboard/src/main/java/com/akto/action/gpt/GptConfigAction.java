package com.akto.action.gpt;

import com.akto.action.UserAction;
import com.akto.dao.AktoGptConfigDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.gpt.AktoGptConfig;
import com.akto.dto.gpt.AktoGptConfigState;
import com.akto.listener.InitializerListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.UpdateOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GptConfigAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(GptConfigAction.class, LogDb.DASHBOARD);

    private int apiCollectionId;

    private List<BasicDBObject> currentState;

    public static final AktoGptConfigState DEFAULT_STATE = InitializerListener.isSaas ? AktoGptConfigState.ENABLED : AktoGptConfigState.DISABLED;

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
            int id = aktoGptConfig.getId() != 0 ? aktoGptConfig.getId() : apiCollection.getId();
            String state = aktoGptConfig.getState() != null ? aktoGptConfig.getState().toString() : DEFAULT_STATE.toString();
            String name = apiCollection != null && apiCollection.getName() != null ? apiCollection.getName(): String.valueOf(id);
            BasicDBObject obj = new BasicDBObject("id", id)
                    .append("state", state)
                    .append("collectionName", name);
            updatedAktoGptConfigList.add(obj);
        }
        for (Integer apiCollectionId : apiCollectionMap.keySet()) {
            //These collection ids don't exist in AktoGptconfigDao, store them in DB with DEFAULT value
            //also store these entries in DB
            AktoGptConfig aktoGptConfig = upsertAktoConfig(apiCollectionId, DEFAULT_STATE);
            BasicDBObject obj = new BasicDBObject("id", aktoGptConfig.getId())
                    .append("state", aktoGptConfig.getState().toString())
                    .append("collectionName", apiCollectionMap.get(apiCollectionId).getName());
            updatedAktoGptConfigList.add(obj);
        }
        return updatedAktoGptConfigList;
    }

    private AktoGptConfig upsertAktoConfig(int apiCollectionId, AktoGptConfigState state){
        AktoGptConfigDao.instance.getMCollection().updateOne(new BasicDBObject("_id", apiCollectionId),
                new BasicDBObject("$set", new BasicDBObject("state", state.toString())), new UpdateOptions().upsert(true));
        return new AktoGptConfig(apiCollectionId, state);
    }

    public String fetchAktoGptConfig(){
        if(apiCollectionId == -1){
            currentState = fetchUpdatedAktoGptConfigs();
            logger.debug("Fetching all AktoGptConfig: {}", currentState);
        } else {
            AktoGptConfig aktoGptConfig = AktoGptConfigDao.instance.findOne(new BasicDBObject("_id", apiCollectionId));
            if(aktoGptConfig == null) {
                aktoGptConfig = upsertAktoConfig(apiCollectionId, DEFAULT_STATE);
            }
            ApiCollection apiCollection = ApiCollectionsDao.instance.findOne(new BasicDBObject("_id", apiCollectionId));
            if (apiCollection != null) {
                String collectionName = apiCollection.getName();
                currentState = Collections.singletonList(new BasicDBObject("id", aktoGptConfig.getId())
                        .append("state", aktoGptConfig.getState().toString())
                        .append("collectionName",
                                collectionName != null ? collectionName : String.valueOf(apiCollectionId)));
                logger.debug("Fetching AktoGptConfig for collectionId: {}, {}", apiCollectionId, currentState);
            }
        }
        return SUCCESS.toUpperCase();
    }

    public String saveAktoGptConfig(){
        for(BasicDBObject aktoGptConfig : currentState){
            AktoGptConfigDao.instance.updateOne(new BasicDBObject("_id", aktoGptConfig.get("id")),
                    new BasicDBObject("$set", new BasicDBObject("state", aktoGptConfig.get("state"))));
        }
        currentState = fetchUpdatedAktoGptConfigs();
        logger.debug("Current state of AktoGptConfig is {}", currentState);
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
