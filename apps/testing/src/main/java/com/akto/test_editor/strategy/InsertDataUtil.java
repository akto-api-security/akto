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
import com.akto.utils.DataInsertionUtil;

public class InsertDataUtil {
    
    public static Map<Integer, HttpCallParser> accountHTTPParserMap = new ConcurrentHashMap<>();


    public static void insertDataInApiCollection(int apiCollectionId, String topic, List<String> messages, List<String> errors) throws Exception {
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
        HttpCallParser httpCallParser = accountHTTPParserMap.get(accountId);
        if (httpCallParser == null) { // account created after docker run
            httpCallParser = new HttpCallParser("userIdentifier", 1, 1, 1, false);
            accountHTTPParserMap.put(accountId, httpCallParser);
        }

        DataInsertionUtil.processTraffic(httpCallParser, responses, apiCollectionId);
    }

}
