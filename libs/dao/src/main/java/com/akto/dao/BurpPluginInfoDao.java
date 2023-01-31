package com.akto.dao;

import org.bson.conversions.Bson;

import com.akto.dao.context.Context;
import com.akto.dto.BurpPluginInfo;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class BurpPluginInfoDao extends AccountsContextDao<BurpPluginInfo> {

    public static final BurpPluginInfoDao instance = new BurpPluginInfoDao();


    public static  Bson filterByUsername(String username) {
        return Filters.eq(BurpPluginInfo.USERNAME, username);
    }

    public void updateLastDownloadedTimestamp(String username) {
        instance.updateOne(filterByUsername(username), Updates.set(BurpPluginInfo.LAST_DOWNLOAD_TIMESTAMP, Context.now()));
    }

    public void updateLastDataSentTimestamp(String username) {
        instance.updateOne(filterByUsername(username), Updates.set(BurpPluginInfo.LAST_DATA_SENT_TIMESTAMP, Context.now()));
    }


    public BurpPluginInfo findByUsername(String username) {
        return instance.findOne(filterByUsername(username));
    }

    @Override
    public String getCollName() {
        return "burp_plugin_info";
    }

    @Override
    public Class<BurpPluginInfo> getClassT() {
        return BurpPluginInfo.class;
    }

    
}
