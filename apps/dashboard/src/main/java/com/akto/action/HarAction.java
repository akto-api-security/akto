package com.akto.action;

import com.akto.DaoInit;
import com.akto.analyser.ResourceAnalyser;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.BurpPluginInfoDao;
import com.akto.dao.RuntimeFilterDao;
import com.akto.dao.context.Context;
import com.akto.dao.file.FilesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.HttpResponseParams;
import com.akto.har.HAR;
import com.akto.listener.InitializerListener;
import com.akto.listener.KafkaListener;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.APICatalogSync;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.ApiToken.Utility;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.har.HAR;
import com.akto.log.LoggerMaker;
import com.akto.usage.UsageMetricCalculator;
import com.akto.dto.ApiToken.Utility;
import com.akto.util.DashboardMode;
import com.akto.utils.GzipUtils;
import com.akto.utils.Utils;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sun.jna.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class HarAction extends UserAction {
    private String harString;
    private List<String> harErrors;
    private BasicDBObject content;
    private int apiCollectionId;
    private String apiCollectionName;

    private boolean skipKafka = DashboardMode.isLocalDeployment();
    private byte[] tcpContent;
    private static final LoggerMaker loggerMaker = new LoggerMaker(HarAction.class);

    public String executeWithSkipKafka(boolean skipKafka) throws IOException {
        this.skipKafka = skipKafka;
        execute();
        return SUCCESS.toUpperCase();
    }

    @Override
    public String execute() throws IOException {
        if (DashboardMode.isKubernetes()) {
            skipKafka = true;
        }

        ApiCollection apiCollection = null;
        
        /*
         * We need to allow the first time creation for demo collections 
         * thus calculating them before creation.
         */
        Set<Integer> demoCollections = UsageMetricCalculator.getDemos();
        Set<Integer> deactivatedCollections = UsageMetricCalculator.getDeactivated();

        loggerMaker.infoAndAddToDb("HarAction.execute() started", LoggerMaker.LogDb.DASHBOARD);
        if (apiCollectionName != null) {
            apiCollection =  ApiCollectionsDao.instance.findByName(apiCollectionName);
            if (apiCollection == null) {
                ApiCollectionsAction apiCollectionsAction = new ApiCollectionsAction();
                apiCollectionsAction.setSession(this.getSession());
                apiCollectionsAction.setCollectionName(apiCollectionName);
                String result = apiCollectionsAction.createCollection();
                if (result.equalsIgnoreCase(Action.SUCCESS)) {
                    List<ApiCollection> apiCollections = apiCollectionsAction.getApiCollections();
                    if (apiCollections != null && apiCollections.size() > 0) {
                        apiCollection = apiCollections.get(0);
                    } else {
                        addActionError("Couldn't create api collection " +  apiCollectionName);
                        return ERROR.toUpperCase();
                    }
                } else {
                    Collection<String> actionErrors = apiCollectionsAction.getActionErrors();
                    if (actionErrors != null && actionErrors.size() > 0) {
                        for (String actionError: actionErrors) {
                            addActionError(actionError);
                        }
                    }
                    return ERROR.toUpperCase();
                }
            }

            apiCollectionId = apiCollection.getId();
        } else {
            apiCollection =  ApiCollectionsDao.instance.findOne(Filters.eq("_id", apiCollectionId));
        }

        if (apiCollection == null) {
            addActionError("Invalid collection name");
            return ERROR.toUpperCase();
        }

        if (apiCollection.getHostName() != null)  {
            addActionError("Traffic mirroring collection can't be used");
            return ERROR.toUpperCase();
        }

        String commonErrorMessage = "collection can't be used, please create a new collection.";

        if(demoCollections.contains(apiCollectionId)) {
            addActionError("Demo " + commonErrorMessage);
            return ERROR.toUpperCase();
        }

        if(deactivatedCollections.contains(apiCollectionId)) {
            addActionError("Deactivated " + commonErrorMessage);
            return ERROR.toUpperCase();
        }
        if (!skipKafka && KafkaListener.kafka == null) {
            addActionError("Dashboard kafka not running");
            return ERROR.toUpperCase();
        }

        if (ApiCollection.Type.API_GROUP.equals(apiCollection.getType()))  {
            addActionError("API groups can't be used");
            return ERROR.toUpperCase();
        }

        if (harString == null) {
            harString = this.content.toString();
        }
        String topic = System.getenv("AKTO_KAFKA_TOPIC_NAME");
        if (topic == null) topic = "akto.api.logs";
        if (harString == null) {
            addActionError("Empty content");
            return ERROR.toUpperCase();
        }

        if (getSession().getOrDefault("utility","").equals(Utility.BURP.toString())) {
            BurpPluginInfoDao.instance.updateLastDataSentTimestamp(getSUser().getLogin());
        }

        try {
            HAR har = new HAR();
            loggerMaker.infoAndAddToDb("Har file upload processing for collectionId:" + apiCollectionId, LoggerMaker.LogDb.DASHBOARD);
            String zippedString = GzipUtils.zipString(harString);
            com.akto.dto.files.File file = new com.akto.dto.files.File(HttpResponseParams.Source.HAR.toString(),zippedString);
            FilesDao.instance.insertOne(file);
            List<String> messages = har.getMessages(harString, apiCollectionId, Context.accountId.get());
            harErrors = har.getErrors();
            Utils.pushDataToKafka(apiCollectionId, topic, messages, harErrors, skipKafka);
            loggerMaker.infoAndAddToDb("Har file upload processing for collectionId:" + apiCollectionId + " finished", LoggerMaker.LogDb.DASHBOARD);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,"Exception while parsing harString", LoggerMaker.LogDb.DASHBOARD);
            e.printStackTrace();
            return SUCCESS.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public static void main(String[] args) {
        String unzipString = GzipUtils.unzipString("H4sIAAAAAAAAAJ1X2XLbNhT9FZR5yUJwEbVZeugosWt3YiUei2nSqfIAkRCJEUkwIGjJ8Wim/9A/7Jf0gosFKkrc9sEe4eLci7segA9GwiNj8mDcUVEwnhkTw7V6hmkEghLJhdrKSEpBPttIDhsHoGe5Z4Ddm0ZOIloYkz8+mwbNpGDV4sEoJBGShudEUp9VNnpOr4+dMXZc3x1PnP6k71hu333lDDwHbMsK5Y77piHol5IWUp2fUhnzELQvL3wAlSKB37GUeTGxbZIzTHJByB2x8uguSKyAp5N+37PzsohxRrc44FlGAwlO45BIAiaU8m+PcVz5/o3tWi5scMEipmQqA5xvWBtWTEkIClVYTT6uOLgH+SBJqZYnHYHkPOIXNMBvYvyBaEpL4x2X9uzFa0GycGlM75al43jwa7w0TLQ03sSCp6xM9S23N1waJw3jOV+xhGr2f3ZOA28SItdcpB1XPrIs5Nuia3wWBDSX+JpkUQlV1hRohi9f69APeSQgS/jXrKBBKSi+rWtYaEpuR6GgAs8iaBkNMedfWZIQe2A56HnjEnrnI9exnCkCwbA/Rbth/wWa5XlCP9LVWybtgTeyvCF6/vbKn1+bKGEbii5psOEvUJVDakPaLMca9kdjy+2N0IKsiWCN3rcBaw5JupN2LNPEJHAgC4jqJHunJK92x9I0mX6pC+VYZyZLIWU2uWPr5ueWrvJWmmeR+dJ+ecCPO6YKFmU0xHQXxJB72tZ/5R0URsfF/YXKIMYLJvU6ZTyjp4FzHnaA4GgEs3oarIqld5Z7GnZOO1MR8qBMVYG/bamLLOAhyyINHX1luYlCuob2pCZaCV3tRjAYT3mv4csmEyZiOvLN48Br2A2lOSYJu4P4YKChM8X9ArgKHNAnfMG+Ano4GpnGiof39dLZK0Iqcg6drRgJiE2WQAZ9Z2A2Cx+6BE4ByY/45SlO+YQ/LRb4RnD5jf/uFKVQr6YLEh5s9JA/YQhaQqKxf59T/D5X2kWnC4qMrdfHdSiKSlHwBM+ShG/x+5oBD4ovu8f8IuDXCfvnF+9+79SABDFtbWu4vFxBi5soJTsMmUuagDx3MBie9TqTeLHLGWRdU8buk/7Pq8tCV7p5v/BNBHeHic4vri/8ix/YmINXsw7LnXLs5MFXTTUPqnUqTYT00piobn8Q73BzycGYb5mMu4m+hRG4ZikD7lX/NbsD53vIW5oSlnVnyv0BuqAdu50YVVBHWTyasceYTpHlFGhLgP26vKVc4/Ep9ZP3ykkgzSJI0QE26p/pOPXI0Hb9uDSR46JZGSH16EBub+Kp5wa6nHfieKtoYVbRghYFvEN42fg+dP4rtwgaQuMG8sPttZq8MlED0OUX19H5BWJR3FBFWvHLQZqCK1WOFY/Mr9UjqWaayrngJyBYCdtI5bwS1QMVqDVKILvts4FmS6MDAH/0tWSyGUaqCAstKlpDQGjo7z//QvVcIXiuoKo1aK0c2JpeLSnkfUKRcqo9umqKoChaB1Tg6GEN8eI1SVlyP/FJzFNizgQjiVmQrMBw27D1dI9i10RxD/48uA/QQ8ATLibbGK646YoEm0jwMgtxLX426A3OR8NKq7GvMjnp9fKdEvZ0oTushV5H2K+EeUdWa5P28FUC54LASlhG0UNMWRTLiQug7zq04gJqP1E38XTf5K1Kk543+7giKk2dirn/ozytUmNCoCAhUImmMiqEpYFsHZN3fKgXflvNwG4krQO3NOdCNnv5U3bmwJsw7Uemmnciqh/6SEl7w2fe2RRI+7BAGQRXlLk67zG8J0+E90ggWHVdHZ3qx7Q9EYaVwvCGiGVIgrjhZVxVmBVok/Fthlb31Wb9jYCgQeFTCK1K2XWshcG3T0QlmCp4KQJqnXL435Qj9urVLFc3KvJ5Cg9E+wzesmOvrbHX6aPjvrEP3GDs4SUTKENAM/vqgwtui6LiHJrBNxbQ0pbAbdN+hFV5UQ+g/ef9/h+L4QHiLQ4AAA==");;
        System.out.println(unzipString);
    }

    public void setContent(BasicDBObject content) {
        this.content = content;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public void setHarString(String harString) {
        this.harString = harString;
    }

    public void setApiCollectionName(String apiCollectionName) {
        this.apiCollectionName = apiCollectionName;
    }

    public List<String> getHarErrors() {
        return harErrors;
    }

    public boolean getSkipKafka() {
        return this.skipKafka;
    }

    public void setTcpContent(byte[] tcpContent) {
        this.tcpContent = tcpContent;
    }

    Awesome awesome = null;

    public String uploadTcp() {

        File tmpDir = FileUtils.getTempDirectory();
        String filename = UUID.randomUUID().toString() + ".pcap";
        File tcpDump = new File(tmpDir, filename);
        try {
            FileUtils.writeByteArrayToFile(tcpDump, tcpContent);
            Awesome awesome =  (Awesome) Native.load("awesome", Awesome.class);
            Awesome.GoString.ByValue str = new Awesome.GoString.ByValue();
            str.p = tcpDump.getAbsolutePath();
            str.n = str.p.length();

            Awesome.GoString.ByValue str2 = new Awesome.GoString.ByValue();
            str2.p = System.getenv("AKTO_KAFKA_BROKER_URL");
            str2.n = str2.p.length();

            awesome.readTcpDumpFile(str, str2 , apiCollectionId);

            return Action.SUCCESS.toUpperCase();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return Action.ERROR.toUpperCase();
        }

    }

    interface Awesome extends Library {
        public static class GoString extends Structure {
            /** C type : const char* */
            public String p;
            public long n;
            public GoString() {
                super();
            }
            protected List<String> getFieldOrder() {
                return Arrays.asList("p", "n");
            }
            /** @param p C type : const char* */
            public GoString(String p, long n) {
                super();
                this.p = p;
                this.n = n;
            }
            public static class ByReference extends GoString implements Structure.ByReference {}
            public static class ByValue extends GoString implements Structure.ByValue {}
        }

        public void readTcpDumpFile(GoString.ByValue filepath, GoString.ByValue kafkaURL, long apiCollectionId);

    }
}