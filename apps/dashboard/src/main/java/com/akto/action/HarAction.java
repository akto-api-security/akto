package com.akto.action;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.BurpPluginInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.file.FilesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiToken.Utility;
import com.akto.dto.HttpResponseParams;
import com.akto.har.HAR;
import com.akto.listener.KafkaListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.DashboardMode;
import com.akto.utils.GzipUtils;
import com.akto.utils.Utils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.io.FileUtils;

public class HarAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(HarAction.class, LogDb.DASHBOARD);;

    private String harString;
    private List<String> harErrors;
    private BasicDBObject content;
    private int apiCollectionId;
    private String apiCollectionName;

    private boolean skipKafka = DashboardMode.isLocalDeployment();
    private byte[] tcpContent;

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

        loggerMaker.debugAndAddToDb("HarAction.execute() started", LoggerMaker.LogDb.DASHBOARD);
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

        HttpResponseParams.Source source = HttpResponseParams.Source.HAR;
        if (getSession().getOrDefault("utility","").equals(Utility.BURP.toString())) {
            BurpPluginInfoDao.instance.updateLastDataSentTimestamp(getSUser().getLogin());
            source = HttpResponseParams.Source.BURP;
        }

        try {
            HAR har = new HAR();
            loggerMaker.debugAndAddToDb("Har file upload processing for collectionId:" + apiCollectionId, LoggerMaker.LogDb.DASHBOARD);
            String zippedString = GzipUtils.zipString(harString);
            com.akto.dto.files.File file = new com.akto.dto.files.File(HttpResponseParams.Source.HAR.toString(),zippedString);
            FilesDao.instance.insertOne(file);
            List<String> messages = har.getMessages(harString, apiCollectionId, Context.accountId.get(), source);
            harErrors = har.getErrors();
            Utils.pushDataToKafka(apiCollectionId, topic, messages, harErrors, skipKafka, true, true);
            loggerMaker.debugAndAddToDb("Har file upload processing for collectionId:" + apiCollectionId + " finished", LoggerMaker.LogDb.DASHBOARD);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,"Exception while parsing harString", LoggerMaker.LogDb.DASHBOARD);
            e.printStackTrace();
            return SUCCESS.toUpperCase();
        }
        return SUCCESS.toUpperCase();
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