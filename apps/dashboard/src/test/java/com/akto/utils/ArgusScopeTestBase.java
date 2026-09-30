package com.akto.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.CustomRoleDao;
import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.RBACDao;
import com.akto.dao.SSOConfigsDao;
import com.akto.dao.SetupDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.CustomRole;
import com.akto.dto.RBAC;
import com.akto.dto.Setup;
import com.akto.dto.User;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.traffic.CollectionTags;
import com.akto.util.Constants;
import com.akto.util.DashboardMode;
import com.akto.util.Pair;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

/*
 * Shared setup for Argus collection-scoping tests: a SaaS (metered) dashboard with three Argus agent
 * collections - two owned by team A (1, 2) and one by another team (3) - custom roles limited to team A's
 * collections, and users with different Argus roles.
 */
public abstract class ArgusScopeTestBase extends MongoBasedTest {

    protected static final int TEAM_A = 101, ADMIN = 102, MEMBER = 103, THREAT_ENGINEER_ALL = 104, TEAM_A_MEMBER = 105;
    protected static final String OWN_HOST = "team-a-chatbot.example.com";
    protected static final String OWN_CLAUDE_HOST = "laptop1.claude";
    protected static final String OTHER_HOST = "team-b-bot.example.com";

    // Roles are only enforced on metered (SaaS / on-prem) dashboards; DashboardMode caches this, so reset it
    protected static void setDashboardMode(String mode) throws Exception {
        SetupDao.instance.getMCollection().drop();
        if (mode != null) SetupDao.instance.insertOne(new Setup(mode));
        java.lang.reflect.Field lastFetched = DashboardMode.class.getDeclaredField("lastSaasFetched");
        lastFetched.setAccessible(true);
        lastFetched.setInt(null, 0);
    }

    @Before
    public void setup() throws Exception {
        setDashboardMode("SAAS");
        Context.accountId.set(ACCOUNT_ID);
        Context.contextSource.set(CONTEXT_SOURCE.AGENTIC);
        ApiCollectionsDao.instance.getMCollection().drop();
        RBACDao.instance.getMCollection().drop();
        CustomRoleDao.instance.getMCollection().drop();
        McpAuditInfoDao.instance.getMCollection().drop();
        UsersDao.instance.getMCollection().drop();
        SSOConfigsDao.instance.getMCollection().drop();

        insertAgentCollection(1, OWN_HOST);
        insertAgentCollection(2, OWN_CLAUDE_HOST);
        insertAgentCollection(3, OTHER_HOST);

        insertCustomRole("TEAM_A_ADMIN", "THREAT_ENGINEER");
        insertCustomRole("TEAM_A_MEMBER", "MEMBER");

        insertUser(TEAM_A, "TEAM_A_ADMIN");
        insertUser(ADMIN, "ADMIN");
        insertUser(MEMBER, "MEMBER");
        insertUser(THREAT_ENGINEER_ALL, "THREAT_ENGINEER");
        insertUser(TEAM_A_MEMBER, "TEAM_A_MEMBER");
    }

    protected void insertAgentCollection(int id, String host) {
        ApiCollection collection = ApiCollection.createManualCollection(id, host);
        collection.setHostName(host);
        CollectionTags tag = new CollectionTags();
        tag.setKeyName(Constants.AKTO_GEN_AI_TAG);
        tag.setValue("Gen AI");
        collection.setTagsList(Collections.singletonList(tag));
        ApiCollectionsDao.instance.insertOne(collection);
    }

    protected void insertCustomRole(String name, String baseRole) {
        CustomRole role = new CustomRole();
        role.setName(name);
        role.setBaseRole(baseRole);
        role.setApiCollectionsId(Arrays.asList(1, 2));
        CustomRoleDao.instance.insertOne(role);
        CustomRoleDao.clearRoleCache();
    }

    protected void insertUser(int userId, String agenticRole) {
        Map<String, String> scopeRoleMapping = new HashMap<>();
        scopeRoleMapping.put(CONTEXT_SOURCE.AGENTIC.name(), agenticRole);
        RBACDao.instance.insertOne(new RBAC(userId, null, ACCOUNT_ID, scopeRoleMapping));
        User user = user(userId);
        UsersDao.instance.insertOne(user);
        RBACDao.instance.deleteUserEntryFromCache(new Pair<>(userId, ACCOUNT_ID));
        UsersCollectionsList.deleteCollectionIdsFromCache(userId, ACCOUNT_ID);
    }

    protected static User user(int userId) {
        User user = new User();
        user.setId(userId);
        user.setLogin("user" + userId + "@example.com");
        return user;
    }

    protected static Map<String, Object> session(int userId) {
        Map<String, Object> session = new HashMap<>();
        session.put("user", user(userId));
        return session;
    }

    protected static void as(int userId, CONTEXT_SOURCE contextSource) {
        Context.userId.set(userId);
        Context.contextSource.set(contextSource);
    }
}
