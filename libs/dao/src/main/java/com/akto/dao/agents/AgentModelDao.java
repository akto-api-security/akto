package com.akto.dao.agents;

import org.bson.conversions.Bson;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.agents.Model;
import com.mongodb.client.model.Filters;

public class AgentModelDao extends AccountsContextDao<Model> {

    public static final AgentModelDao instance = new AgentModelDao();

    public Bson filterByName(String name) {
        return Filters.eq(Model._NAME, name);
    }

    public Model findByName(String name) {
        return findOne(filterByName(name));
    }

    public void deleteByName(String name) {
        getMCollection().deleteOne(filterByName(name));
    }

    @Override
    public String getCollName() {
        return "agent_models";
    }

    @Override
    public Class<Model> getClassT() {
        return Model.class;
    }

}
