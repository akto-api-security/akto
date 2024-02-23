package com.akto.action;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.BurpPluginInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.file.FilesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.HttpResponseParams;
import com.akto.har.HAR;
import com.akto.log.LoggerMaker;
import com.akto.dto.ApiToken.Utility;
import com.akto.util.DashboardMode;
import com.akto.utils.GzipUtils;
import com.akto.utils.Utils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;
import org.apache.commons.io.FileUtils;
import com.sun.jna.*;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
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
            com.akto.dto.files.File file = new com.akto.dto.files.File(HttpResponseParams.Source.HAR,zippedString);
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

    private static final int CHUNK_SIZE = 1 * 1024 * 1024 ;
    private static final String REMOTE_IMAGE_URL = "https://temp-aryan.s3.ap-south-1.amazonaws.com/big_image.jpeg";
    private static final MediaType MEDIA_TYPE_JPEG = MediaType.parse("image/jpeg");

    public static class CustomInputStream extends InputStream {
        private final InputStream originalStream;
    
        public CustomInputStream(InputStream originalStream) {
            this.originalStream = originalStream;
        }
    
        @Override
        public int read() throws IOException {
            System.out.println("hi");
            return originalStream.read();
        }
    
        @Override
        public int read(byte[] b) throws IOException {
            System.out.println("hi");
            return originalStream.read(b);
        }
    
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            System.out.println("hi");
            return originalStream.read(b, off, len);
        }
    
        // Implement other methods if needed, delegating to originalStream.
    }
  
    static int counter = 0;
    public static void main(String[] args) throws Exception {
        URL url = new URL(REMOTE_IMAGE_URL);
        InputStream urlInputStream = url.openStream() ;

        try {
            OkHttpClient client = new OkHttpClient();
            String uploadUrl = "https://juiceshop.akto.io/profile/image/file";

            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", "filename", new RequestBody() {
                        @Override
                        public MediaType contentType() {
                            return MEDIA_TYPE_JPEG;
                        }

                        @Override
                        public void writeTo(BufferedSink sink) throws IOException {
                            byte[] chunk = new byte[CHUNK_SIZE];
                            int bytesRead;
                            while ((bytesRead = urlInputStream.read(chunk)) != -1) {
                                counter += bytesRead;
                                System.out.println("Writing" +  bytesRead);
                                sink.write(chunk, 0, bytesRead);
                            }
                        }
                    })
                    .build();
            Request request = new Request.Builder()
                    .url(uploadUrl)
                    .addHeader("cookie","_BEAMER_USER_ID_TEEsyHNL42222=350d4563-63a9-4732-99e8-bf8ddcbdb9b2; _BEAMER_FIRST_VISIT_TEEsyHNL42222=2023-12-02T08:28:32.664Z; intercom-id-xjvl0z2h=8efe8e84-4f98-499d-b1ec-96cbeb9a6243; intercom-device-id-xjvl0z2h=0a609431-b163-4271-a731-80d13863c955; _ga=GA1.1.995063340.1702709571; _gcl_au=1.1.1377381098.1702709571; _hjSessionUser_3538216=eyJpZCI6IjI1YzYxOTUyLTNiZTctNWE1OS1iZTc2LTFjOWUxMGRkYTY3OSIsImNyZWF0ZWQiOjE3MDI3MDk1NzEwMTMsImV4aXN0aW5nIjp0cnVlfQ==; cb_user_id=null; cb_group_id=null; cb_anonymous_id=%228f0411b9-0788-491f-9135-19f676d96e70%22; _clck=4g8d4j%7C2%7Cfif%7C0%7C1475; _rdt_uuid=1707125191687.c46d45f4-8372-4a49-9f54-2cedeb6e0960; _hjSessionUser_3823272=eyJpZCI6ImZlNTQwM2JiLWEzMmItNWVmNS04NDAwLWU3NTUyZDg1ZThkZSIsImNyZWF0ZWQiOjE3MDczODAxOTEwMDMsImV4aXN0aW5nIjp0cnVlfQ==; _ga_DEM5S3JBNR=GS1.1.1707971743.18.0.1707971743.0.0.0; _ga_GGW4LCJNV5=GS1.1.1707971743.20.0.1707971743.0.0.0; language=en; __cuid=f4c092a9faf6477f884bcee458fed3a3; amp_fef1e8=c645b56c-4f16-4ec6-ac94-610109baabd1R...1hn5hm0uo.1hn5hm50n.2i.l.37; welcomebanner_status=dismiss; cookieconsent_status=dismiss; token=eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJzdGF0dXMiOiJzdWNjZXNzIiwiZGF0YSI6eyJpZCI6MjIsInVzZXJuYW1lIjoiYXR0YWNrZXIiLCJlbWFpbCI6ImF0dGFja2VyQGdtYWlsLmNvbSIsInBhc3N3b3JkIjoiMTY2YzU1YjUxZmQ1ZmJkYTg3NWFiNmMzMTg0NDIyNTUiLCJyb2xlIjoiY3VzdG9tZXIiLCJkZWx1eGVUb2tlbiI6IiIsImxhc3RMb2dpbklwIjoidW5kZWZpbmVkIiwicHJvZmlsZUltYWdlIjoiYXNzZXRzL3B1YmxpYy9pbWFnZXMvdXBsb2Fkcy8yMi5qcGciLCJ0b3RwU2VjcmV0IjoiIiwiaXNBY3RpdmUiOnRydWUsImNyZWF0ZWRBdCI6IjIwMjQtMDItMTcgMDI6MDg6NTEuNjU3ICswMDowMCIsInVwZGF0ZWRBdCI6IjIwMjQtMDItMjIgMDQ6Mzk6MDMuNTY5ICswMDowMCIsImRlbGV0ZWRBdCI6bnVsbH0sImlhdCI6MTcwODU3Njc0OCwiZXhwIjoyMDIzOTM2NzQ4fQ.Cljc6di8Ny4yYOlgKvz9u8nqyjxlrWqzvx35YfWvjYbE5NgAvSNokSJYF_W3mrN-beLyaj-segLzTl8f8Zoqfv0tVr6OP5MpfwPzXnDP-Drm8GrycwTQolh0v0PjxgXq-EWKAtZeU_Aisi42OlHNW30i_fajEhQwiJTfw5el1Ls; intercom-session-xjvl0z2h=SS9hQ256Z2dFZldwenh6MkVhMG5uS0ZoZCsyNVFlc2tTUjhndVRscmU3dGkxWUdpdDQ3TXpLSWNodDArK3dNSi0tbm5wUVpkTTlJaHFOYzF4aFcyM29Bdz09--7aeb04ec5e15cc2a72e659f4f672c0b614793b0b; mp_c403d0b00353cc31d7e33d68dc778806_mixpanel=%7B%22distinct_id%22%3A%20%22aryan%40akto.io_ON_PREM%22%2C%22%24device_id%22%3A%20%2218c29a4bb9f15d4-03fd022ba99e1e-16525634-13c680-18c29a4bb9f15d4%22%2C%22%24initial_referrer%22%3A%20%22%24direct%22%2C%22%24initial_referring_domain%22%3A%20%22%24direct%22%2C%22__mps%22%3A%20%7B%7D%2C%22__mpso%22%3A%20%7B%7D%2C%22__mpus%22%3A%20%7B%7D%2C%22__mpa%22%3A%20%7B%7D%2C%22__mpu%22%3A%20%7B%7D%2C%22__mpr%22%3A%20%5B%5D%2C%22__mpap%22%3A%20%5B%5D%2C%22%24user_id%22%3A%20%22aryan%40akto.io_ON_PREM%22%2C%22email%22%3A%20%22aryan%40akto.io%22%2C%22dashboard_mode%22%3A%20%22ON_PREM%22%2C%22account_name%22%3A%20%22akto%22%7D")
                    .post(requestBody)
                    .build();

            System.out.println(requestBody.contentLength());

            try (Response response = client.newCall(request).execute()) {
                System.out.println("reqlen" + requestBody.contentLength());
                System.out.println("Final " + counter);
                //System.out.println(response.body().string());
            }
        } catch (Exception e) {
            e.printStackTrace();
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