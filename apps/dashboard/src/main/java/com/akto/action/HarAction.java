package com.akto.action;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.BurpPluginInfoDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.User;
import com.akto.dto.UserAccountEntry;
import com.akto.har.HAR;
import com.akto.log.LoggerMaker;
import com.akto.dto.ApiToken.Utility;
import com.akto.utils.DashboardMode;
import com.akto.utils.Utils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;
import org.apache.commons.io.FileUtils;
import com.sun.jna.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public class HarAction extends UserAction {
    private static final int CONNECTION_TIMEOUT = 10 * 1000;
    private String harString;
    private List<String> harErrors;
    private BasicDBObject content;
    private int apiCollectionId;
    private String apiCollectionName;

    private boolean skipKafka = DashboardMode.isLocalDeployment();
    private byte[] tcpContent;
    private static final LoggerMaker loggerMaker = new LoggerMaker(HarAction.class);

    @Override
    public String execute() throws IOException {
        ApiCollection apiCollection = null;
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
            List<String> messages = har.getMessages(harString, apiCollectionId, Context.accountId.get());
            harErrors = har.getErrors();
            Utils.pushDataToKafka(apiCollectionId, topic, messages, harErrors, skipKafka);
            loggerMaker.infoAndAddToDb("Har file upload processing for collectionId:" + apiCollectionId + " finished", LoggerMaker.LogDb.DASHBOARD);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Exception while parsing harString", LoggerMaker.LogDb.DASHBOARD);
            e.printStackTrace();
            return SUCCESS.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    private String filename;
    private String username;
    public String createCollectionWithUrl() {
        Pattern pattern = Pattern.compile("[a-zA-Z0-9\\-_]{1,100}");
        if (filename.length() > 100) {
            addActionError("filename should be limited to 100 chars");
            return ERROR.toUpperCase();
        }

        if (!pattern.matcher(filename).matches()) {
            addActionError("filename should contain only alphanumeric, hyphen or underscores");
            return ERROR.toUpperCase();
        }

        String fileUrl = "https://akto-setup.s3.amazonaws.com/templates/sample_data/" + filename + ".har";
        String tempFileUrl = "temp_" + UUID.randomUUID() + "_" + filename;
        try {
            FileUtils.copyURLToFile(new URL(fileUrl), new File(tempFileUrl), CONNECTION_TIMEOUT, CONNECTION_TIMEOUT);

            User user = UsersDao.instance.findOne(User.LOGIN, username);
            if (user == null) return SUCCESS.toUpperCase();

            for(UserAccountEntry entry: user.getAccounts().values()) {
                int accountId = entry.getAccountId();

                loggerMaker.infoAndAddToDb("Checking for account " + accountId + " " + filename, LoggerMaker.LogDb.DASHBOARD);

                Context.accountId.set(accountId);
                ApiCollection apiCollection = ApiCollectionsDao.instance.findByName(filename);
                if (apiCollection != null) return SUCCESS.toUpperCase();

                loggerMaker.infoAndAddToDb("Adding to account " + accountId + " " + filename, LoggerMaker.LogDb.DASHBOARD);
                this.apiCollectionName = filename;
                this.harString = FileUtils.readFileToString(new File(tempFileUrl), StandardCharsets.UTF_8);
                this.execute();
                loggerMaker.infoAndAddToDb("Completed adding to account " + accountId + " " + filename, LoggerMaker.LogDb.DASHBOARD);

                break;
            }

        } catch (IOException e) {
            addActionError("An error occurred: " + e.getMessage());
            return ERROR.toUpperCase();
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
            ;
            return Action.ERROR.toUpperCase();        
        }

    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setFilename(String filename) {
        this.filename = filename;
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