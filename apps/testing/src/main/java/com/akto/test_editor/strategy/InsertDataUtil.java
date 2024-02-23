package com.akto.test_editor.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;

import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.parsers.HttpCallParser;
import com.akto.utils.AccountHTTPCallParserAktoPolicyInfo;
import com.akto.utils.DataInsertionUtil;

public class InsertDataUtil {
    
    public static Map<Integer, AccountHTTPCallParserAktoPolicyInfo> accountHTTPParserMap = new ConcurrentHashMap<>();


    public static void insertDataInApiCollection(int apiCollectionId, String topic, List<String> messages, List<String> errors, boolean skipKafka) throws Exception {
        List<HttpResponseParams> responses = new ArrayList<>();
        for (String message: messages){
            HttpResponseParams responseParams =  HttpCallParser.parseKafkaMessage(message);
            responseParams.getRequestParams().setApiCollectionId(apiCollectionId);
            responses.add(responseParams);
        }

        String accountIdStr = responses.get(0).accountId;
        if (!StringUtils.isNumeric(accountIdStr)) {
            return;
        }

        int accountId = Integer.parseInt(accountIdStr);
        Context.accountId.set(accountId);

        SingleTypeInfo.fetchCustomDataTypes(accountId);
        AccountHTTPCallParserAktoPolicyInfo info = accountHTTPParserMap.get(accountId);
        if (info == null) { // account created after docker run
            info = new AccountHTTPCallParserAktoPolicyInfo();
            HttpCallParser callParser = new HttpCallParser("userIdentifier", 1, 1, 1, false);
            info.setHttpCallParser(callParser);
            accountHTTPParserMap.put(accountId, info);
        }

        DataInsertionUtil.processTraffic(info.getHttpCallParser(), responses, apiCollectionId);
    }

}
