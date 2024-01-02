package com.akto.action.settings;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.settings.DefaultPayloadsDao;
import com.akto.dto.settings.DefaultPayload;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

import java.util.List;

public class DefaultPayloadAction extends UserAction {

    private String domain;
    private String pattern;
    private DefaultPayload defaultPayload;

    public String fetchDefaultPayload() {
        this.defaultPayload = DefaultPayloadsDao.instance.findOne(DefaultPayload.ID, domain);
        return SUCCESS.toUpperCase();
    }

    private List<DefaultPayload> domains;
    public String fetchAllDefaultPayloads() {
        this.domains = DefaultPayloadsDao.instance.findAll(Filters.empty(), Projections.exclude(DefaultPayload.PATTERN));
        return SUCCESS.toUpperCase();
    }

    public String saveDefaultPayload() {
        fetchDefaultPayload();
        if (defaultPayload == null) {
            defaultPayload = new DefaultPayload(domain, pattern, getSUser().getLogin(), Context.now(), Context.now());
            DefaultPayloadsDao.instance.insertOne(defaultPayload);
        } else {
            Bson updates = Updates.combine(
                    Updates.set(DefaultPayload.PATTERN, pattern),
                    Updates.set(DefaultPayload.UPDATED_TS, Context.now())
            );
            DefaultPayloadsDao.instance.updateOne(DefaultPayload.ID, domain, updates);
            fetchDefaultPayload();
        }
        return SUCCESS.toUpperCase();
    }


    @Override
    public String execute() {
        throw new IllegalStateException("Default action called but not defined!");
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public DefaultPayload getDefaultPayload() {
        return defaultPayload;
    }

    public List<DefaultPayload> getDomains() {
        return domains;
    }
}
