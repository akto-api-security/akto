package com.akto.listener;


import com.akto.DaoInit;
import com.akto.action.HarAction;
import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dao.demo.VulnerableRequestForTemplateDao;
import com.akto.dto.*;
import com.akto.dto.demo.VulnerableRequestForTemplate;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.AuthParam;
import com.akto.dto.testing.HardcodedAuthParam;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.Main;
import com.akto.runtime.policies.AktoPolicy;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import java.io.IOException;
import java.net.URL;
import java.util.*;

public class RuntimeListener extends AfterMongoConnectListener {

    public static HttpCallParser httpCallParser = null;
    public static AktoPolicy aktoPolicy = null;
    public static final String JUICE_SHOP_DEMO_COLLECTION_NAME = "juice_shop_demo";
    public static final String ANONYMOUS_EMAIL = "anonymoustesteditor@akto.io";

    public static void main(String[] args) {
        DaoInit.init(new ConnectionString("mongodb://localhost:27017/admini"));
        Context.accountId.set(1_000_000);

        String salt = "127oy";
        String passHash = Integer.toString((salt + "admin123").hashCode());
        SignupInfo signupInfo = new SignupInfo.PasswordHashInfo(salt, passHash);
        User user = UsersDao.instance.insertSignUp(ANONYMOUS_EMAIL, "Anonymous User", signupInfo, 1_000_000);
        long count = UsersDao.instance.getMCollection().countDocuments();
        // if first user then automatic admin
        // else check if rbac is 0 or not. If 0 then make the user that was created first as admin.
        // done for customers who were there before rbac feature
        if (count == 1) {
            RBACDao.instance.insertOne(new RBAC(user.getId(), RBAC.Role.ADMIN));
        } else {
            long rbacCount = RBACDao.instance.getMCollection().countDocuments();
            if (rbacCount == 0) {
                MongoCursor<User> cursor = UsersDao.instance.getMCollection().find().sort(Sorts.ascending("_id")).limit(1).cursor();
                if (cursor.hasNext()) {
                    User firstUser = cursor.next();
                    RBACDao.instance.insertOne(new RBAC(firstUser.getId(), RBAC.Role.ADMIN));
                }
            }
        }

    }

    private final LoggerMaker loggerMaker= new LoggerMaker(RuntimeListener.class);

    @Override
    public void runMainFunction() {
        Context.accountId.set(1_000_000);
        Main.initializeRuntime();
        httpCallParser = new HttpCallParser("userIdentifier", 1, 1, 1, false);
        aktoPolicy = new AktoPolicy(RuntimeListener.httpCallParser.apiCatalogSync, false);

        try {
            initialiseAnonymousUser();
            initialiseDemoCollections();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while initialising demo collections: " + e, LoggerMaker.LogDb.DASHBOARD);
        }
    }

    private void initialiseAnonymousUser() {

        User user = UsersDao.instance.findOne(Filters.eq(User.LOGIN, ANONYMOUS_EMAIL));
        if (user != null) {
            loggerMaker.infoAndAddToDb("Anonymous user already initialised", LoggerMaker.LogDb.DASHBOARD);
            return;
        }

        String salt = "127oy";
        String passHash = Integer.toString((salt + "admin123").hashCode());
        SignupInfo signupInfo = new SignupInfo.PasswordHashInfo(salt, passHash);
        user = UsersDao.instance.insertSignUp(ANONYMOUS_EMAIL, "Anonymous User", signupInfo, 1_000_000);
        if (user != null) {
            loggerMaker.infoAndAddToDb("Anonymous user initialised", LoggerMaker.LogDb.DASHBOARD);
        }
    }

    public void initialiseDemoCollections() {
        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(new BasicDBObject());
        if (accountSettings != null && accountSettings.getDemoCollectionCreateTime() > 0) {
            long count = VulnerableRequestForTemplateDao.instance.count(new BasicDBObject());
            if (count < 1) {
                //initialise vulnerable requests for templates in case its not present in db
                insertVulnerableRequestsForDemo();
                loggerMaker.infoAndAddToDb("map created in db for vulnerable requests and corresponding templates", LoggerMaker.LogDb.DASHBOARD);
            }
            loggerMaker.infoAndAddToDb("Demo collections already initialised", LoggerMaker.LogDb.DASHBOARD);
            return;
        }

        // get har file from github
        String url = "https://raw.githubusercontent.com/akto-api-security/tests-library/master/resources/juiceshop.har";
        String harString = "";
        try {
            harString = new Scanner(new URL(url).openStream(), "UTF-8").useDelimiter("\\A").next();
        } catch (IOException e) {
            loggerMaker.errorAndAddToDb("Error downlaoding from github: " + e, LoggerMaker.LogDb.DASHBOARD);
            return;
        }

        String tokensUrl = "https://raw.githubusercontent.com/akto-api-security/tests-library/master/resources/juiceshop_tokens.json";
        Map<String, String> tokens = new HashMap<>();
        try {
            String tokenJsonString = new Scanner(new URL(tokensUrl).openStream(), "UTF-8").useDelimiter("\\A").next();
            tokens = new Gson().fromJson(tokenJsonString, Map.class);
        } catch (IOException e) {
            loggerMaker.errorAndAddToDb("Error downloading from github: " + e, LoggerMaker.LogDb.DASHBOARD);
            return;
        }

        String victimToken = tokens.get("victimToken");
        harString = harString.replaceAll("\\{\\{AKTO\\.token\\}\\}", victimToken);

        // process har file
        HarAction harAction = new HarAction();
        harAction.setHarString(harString);
        harAction.setApiCollectionName(JUICE_SHOP_DEMO_COLLECTION_NAME);
        Map<String, Object> session = new HashMap<>();
        harAction.setSession(session);
        // todo: skipKafka = true for onPrem also
        try {
            harAction.execute();
        } catch (IOException e) {
            loggerMaker.errorAndAddToDb("Error: " + e, LoggerMaker.LogDb.DASHBOARD);
        }

        // auth mechanism
        String attackerKey = tokens.get("attackerKey");
        String attackerToken  = tokens.get("attackerToken");
        List<AuthParam> authParamList = new ArrayList<>();
        authParamList.add(new HardcodedAuthParam(AuthParam.Location.HEADER, attackerKey, attackerToken, true));
        AuthMechanism authMechanism = new AuthMechanism(
             authParamList, new ArrayList<>(), "HARDCODED"
        );
        AuthMechanismsDao.instance.insertOne(authMechanism);

        //inserting first time during initialisation of demo collections
        insertVulnerableRequestsForDemo();

        AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.DEMO_COLLECTION_CREATE_TIME, Context.now())
        );
    }

    private void insertVulnerableRequestsForDemo() {
        ApiCollection collection = ApiCollectionsDao.instance.findByName(JUICE_SHOP_DEMO_COLLECTION_NAME);
        if (collection == null) {
            loggerMaker.errorAndAddToDb("Error: collection not found", LoggerMaker.LogDb.DASHBOARD);
            return;
        }
        Map<String, List<String>> apiVsTemplateMap = VulnerableRequestForTemplateDao.getApiVsTemplateMap();
        List<VulnerableRequestForTemplate> vulnerableRequestForTemplates = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : apiVsTemplateMap.entrySet()) {
            String apiName = entry.getKey();
            List<String> templates = entry.getValue();
            String[] apiNameParts = apiName.split(" ");
            String method = apiNameParts[0];
            String path = apiNameParts[1];
            ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(collection.getId(), path, URLMethods.Method.fromString(method));
            vulnerableRequestForTemplates.add(new VulnerableRequestForTemplate(apiInfoKey, templates));
        }
        VulnerableRequestForTemplateDao.instance.insertMany(vulnerableRequestForTemplates);
    }

    //

    @Override
    public int retryAfter() {
        return 60;
    }

}
