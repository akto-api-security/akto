package com.akto.dao.agents;

import java.util.ArrayList;
import java.util.List;

import org.bson.conversions.Bson;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.agents.Model;
import com.akto.dto.agents.ModelType;
import com.mongodb.client.model.Filters;

public class AgentModelDao extends AccountsContextDao<Model> {

    public static final AgentModelDao instance = new AgentModelDao();

    public Bson filterByName(String name) {
        return Filters.eq(Model._NAME, name);
    }

    public Bson filterByType(ModelType type) {
        return Filters.eq(Model._TYPE, type.name());
    }

    public Model findByName(String name) {
        return findOne(filterByName(name));
    }

    public List<Model> findAllByType(ModelType type) {
        return findAll(filterByType(type));
    }

    public List<Model> findAllForTypes(ModelType... types) {
        List<Model> results = new ArrayList<>();
        for (ModelType type : types) {
            results.addAll(findAllByType(type));
        }
        return results;
    }

    public List<Model> findAllForAllTypes() {
        return findAllForTypes(ModelType.values());
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
