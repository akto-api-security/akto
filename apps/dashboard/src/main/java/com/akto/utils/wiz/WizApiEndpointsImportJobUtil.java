package com.akto.utils.wiz;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.listener.RuntimeListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.open_api.parser.ParserResult;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.Main;
import com.akto.utils.AccountHTTPCallParserAktoPolicyInfo;
import com.akto.wiz.WizImportJobPageContext;

public class WizApiEndpointsImportJobUtil {

    private static final LoggerMaker logger = new LoggerMaker(WizApiEndpointsImportJobUtil.class, LogDb.DASHBOARD);

    public static void setupBeforeImport(int accountId) {
        SingleTypeInfo.fetchCustomDataTypes(accountId);
        AccountHTTPCallParserAktoPolicyInfo aktoPolicyInfo = RuntimeListener.accountHTTPParserMap.get(accountId);
        if (aktoPolicyInfo == null) {
            aktoPolicyInfo = new AccountHTTPCallParserAktoPolicyInfo();
            HttpCallParser httpCallParser = new HttpCallParser("userIdentifier", 10, 5, 30, false, true);
            httpCallParser.setSkipDependencyAnalysis(true);
            aktoPolicyInfo.setHttpCallParser(httpCallParser);
            RuntimeListener.accountHTTPParserMap.put(accountId, aktoPolicyInfo);
        }
    }
    
    public static void wizApiEndpointsProcessor(ParserResult parsedSwagger, WizImportJobPageContext pageContext) {
        try {
            int accountId = Context.accountId.get();

            boolean isFirstPage = pageContext != null && pageContext.isFirstPage();
            if (isFirstPage) {
                setupBeforeImport(accountId);
            }

            boolean isLastPage = pageContext != null && pageContext.isLastPage();
            boolean syncImmediately = isLastPage;
            
            List<String> messages = parsedSwagger.getUploadLogs().stream()
                .map(com.akto.dto.upload.SwaggerUploadLog::getAktoFormat)
                .collect(Collectors.toList());

            List<HttpResponseParams> responses = new ArrayList<>();

            for (String message: messages) {
                try {
                    HttpResponseParams responseParams = HttpCallParser.parseKafkaMessage(message);
                    responses.add(responseParams);
                } catch (Exception e) {
                    logger.error("Error parsing Kafka message: " + e.getMessage(), e);
                }
            }

            AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
            responses = Main.filterBasedOnHeaders(responses, accountSettings);

            AccountHTTPCallParserAktoPolicyInfo aktoPolicyInfo = RuntimeListener.accountHTTPParserMap.get(accountId);
            HttpCallParser httpCallParser = aktoPolicyInfo.getHttpCallParser();
            httpCallParser.syncFunction(responses, syncImmediately, false, accountSettings, false);
        } catch (Exception e) {
            logger.error("Error pushing Wiz import data to Kafka: " + e.getMessage(), e);
        }
    }

}
