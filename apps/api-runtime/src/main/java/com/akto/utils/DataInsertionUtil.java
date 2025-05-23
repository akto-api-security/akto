package com.akto.utils;

import java.util.List;

import com.akto.dao.AccountSettingsDao;
import com.akto.dto.AccountSettings;
import com.akto.dto.HttpResponseParams;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.APICatalogSync;

public class DataInsertionUtil {

    public static void processTraffic(HttpCallParser httpCallParser, List<HttpResponseParams> responses, int apiCollectionId){
        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        httpCallParser.syncFunction(responses, true, false, accountSettings);
        APICatalogSync.mergeUrlsAndSave(apiCollectionId, true, true, httpCallParser.apiCatalogSync.existingAPIsInDb, false, false);
        httpCallParser.apiCatalogSync.buildFromDB(false, false);
        APICatalogSync.updateApiCollectionCount(httpCallParser.apiCatalogSync.getDbState(apiCollectionId), apiCollectionId);
    }

}
