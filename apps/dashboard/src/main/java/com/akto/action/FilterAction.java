package com.akto.action;

import com.akto.dao.RuntimeFilterDao;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.mongodb.BasicDBObject;

import java.util.*;

public class FilterAction extends UserAction{

    private List<RuntimeFilter> runtimeFilters;
    @Override
    public String execute(){
        runtimeFilters = RuntimeFilterDao.instance.findAll(new BasicDBObject());
        return SUCCESS.toUpperCase();
    }

    public List<RuntimeFilter> getRuntimeFilters() {
        return runtimeFilters;
    }
}
