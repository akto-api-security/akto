package com.akto.otel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.listener.RuntimeListener;
import com.akto.log.LoggerMaker;
import com.akto.parsers.HttpCallParser;
import com.akto.utils.AccountHTTPCallParserAktoPolicyInfo;

public class TraceProcessingServiceImpl implements TraceProcessingService {

    private static final LoggerMaker logger = new LoggerMaker(TraceProcessingServiceImpl.class, LoggerMaker.LogDb.DASHBOARD);

    @Override
    public void processTraces(List<HttpResponseParams> traces, int accountId) throws Exception {
        try {
            Context.accountId.set(accountId);

            Map<String, List<HttpResponseParams>> responseParamsToAccountIdMap = new HashMap<>();
            responseParamsToAccountIdMap.put(String.valueOf(accountId), traces);

            SingleTypeInfo.fetchCustomDataTypes(accountId);
            AccountHTTPCallParserAktoPolicyInfo info = RuntimeListener.accountHTTPParserMap.get(accountId);
            if (info == null) { // account created after docker run
                info = new AccountHTTPCallParserAktoPolicyInfo();
                HttpCallParser callParser = new HttpCallParser("userIdentifier", 1, 1, 1, false, true);
                info.setHttpCallParser(callParser);
                RuntimeListener.accountHTTPParserMap.put(accountId, info);
            }

            AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());

            info.getHttpCallParser().syncFunction(traces, true, false, accountSettings, true);
            info.getHttpCallParser().apiCatalogSync.buildFromDB(false, false);

            logger.infoAndAddToDb("Successfully processed " + traces.size() + " converted traces through API pipeline");
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Failed to process converted traces: " + e.getMessage());
            throw e;
        }
    }
}
