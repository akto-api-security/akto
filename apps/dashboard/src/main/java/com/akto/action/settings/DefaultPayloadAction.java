package com.akto.action.settings;

import com.akto.action.UserAction;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dto.settings.DefaultPayload;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DefaultPayloadAction extends UserAction {

    private static final ExecutorService es = Executors.newFixedThreadPool(1);
    private String domain;
    private String pattern;
    private DefaultPayload defaultPayload;

    private Map<String, DefaultPayload> createDefaultPayloadsMap() {
        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        Map<String, DefaultPayload> defaultPayloadMap = accountSettings.getDefaultPayloads();

        if (defaultPayloadMap == null) {
            defaultPayloadMap = new HashMap<>();
        }

        return defaultPayloadMap;
    }

    public String fetchDefaultPayload() {
        this.defaultPayload = createDefaultPayloadsMap().get(Base64.getEncoder().encodeToString(domain.getBytes()));
        return SUCCESS.toUpperCase();
    }

    private List<DefaultPayload> domains;
    public String fetchAllDefaultPayloads() {
        this.domains = new ArrayList<>();
        this.domains.addAll(createDefaultPayloadsMap().values());
        return SUCCESS.toUpperCase();
    }

    public String saveDefaultPayload() {
        fetchDefaultPayload();
        int now = Context.now();
        if (defaultPayload == null) {
            defaultPayload = new DefaultPayload(domain, pattern, getSUser().getLogin(), now, now, false);
        }

        String domainBase64 = Base64.getEncoder().encodeToString(domain.getBytes());
        defaultPayload.setUpdatedTs(now);
        defaultPayload.setPattern(pattern);
        Bson updates = Updates.set(AccountSettings.DEFAULT_PAYLOADS + "." + domainBase64, defaultPayload);
        AccountSettingsDao.instance.updateOne(AccountSettingsDao.generateFilter(), updates);

        fetchDefaultPayload();

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
