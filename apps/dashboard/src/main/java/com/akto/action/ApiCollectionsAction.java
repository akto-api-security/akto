package com.akto.action;

import java.util.ArrayList;
import java.util.List;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dto.ApiCollection;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;

public class ApiCollectionsAction extends UserAction {
    
    List<ApiCollection> apiCollections = new ArrayList<>();

    public String getAllCollections() {

        this.apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        return Action.SUCCESS.toUpperCase();

    }

    public List<ApiCollection> getApiCollections() {
        return this.apiCollections;
    }

    public void setApiCollections(List<ApiCollection> apiCollections) {
        this.apiCollections = apiCollections;
    }
}
