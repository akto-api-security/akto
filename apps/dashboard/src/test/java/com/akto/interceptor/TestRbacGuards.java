package com.akto.interceptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.akto.MongoBasedTest;
import com.akto.dao.CustomRoleDao;
import com.akto.dao.RBACDao;
import com.akto.dao.context.Context;
import com.akto.dto.CustomRole;
import com.akto.dto.RBAC;
import com.akto.dto.RBAC.Role;
import com.akto.dto.rbac.RbacEnums;
import com.akto.util.Pair;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

public class TestRbacGuards extends MongoBasedTest {

    /*
     * Actions that run without the role check: login / signup / SSO callbacks, health and metrics,
     * public tools, and a few reads not reviewed yet. Adding a new action here needs a reason.
     */
    private static final Set<String> ACTIONS_WITHOUT_ROLE_CHECK = new HashSet<>(Arrays.asList(
            "home", "verify-email", "metrics", "detailedMetrics", "mongo-error", "api/inventory/*/openapi", "api/health",
            "auth/login", "dashboard/accessToken", "api/me", "api/saveSubscription", "validate", "api/createNewTeam",
            "signup-email", "signup-google", "signup-github", "authorization-code/callback", "okta-initiate-login",
            "signup-azure-saml", "trigger-saml-sso", "callback-google-saml", "callback-jumpcloud-saml", "middleware/config",
            "api/deleteApisBasedOnHeader", "api/logout", "auth0-logout", "api/fetchAccountConfig", "tools/publicApi",
            "tools/fetchAllSubCategories", "api/fetchVulnerableRequests", "tools/fetchVulnerableRequests", "tools/fetchSampleData",
            "api/fetchParamsStatus", "api/fetchRemediationInfo", "api/fetchActiveTestRunsStatus", "copilot/oauth/callback",
            "api/fetchRuntimeInstances", "api/fetchRuntimeMetrics", "tools/runTestForGivenTemplate",
            "api/fetchTestingRunPlaygroundStatus", "tools/createSampleDataJson", "callback", "tools/addLLmData",
            "tools/convertSamleDataToBurpRequest", "tools/convertSampleDataToCurl", "api/downloadReportPDF",
            "auth/sendPasswordResetLink", "auth/resetPassword", "api/findSvcToSvcGraphEdges", "api/findSvcToSvcGraphNodes",
            "api/downloadSamplePdf", "api/wrapped"
    ));

    private static Map<String, String> roleCheckParams(Element action) {
        NodeList refs = action.getElementsByTagName("interceptor-ref");
        for (int i = 0; i < refs.getLength(); i++) {
            Element ref = (Element) refs.item(i);
            if (!"roleAccessInterceptor".equals(ref.getAttribute("name"))) continue;
            Map<String, String> params = new HashMap<>();
            NodeList paramNodes = ref.getElementsByTagName("param");
            for (int j = 0; j < paramNodes.getLength(); j++) {
                Element param = (Element) paramNodes.item(j);
                params.put(param.getAttribute("name"), param.getTextContent().trim());
            }
            return params;
        }
        return null;
    }

    @Test
    public void testEveryActionHasAValidRoleCheck() throws Exception {
        Document struts = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new File("src/main/resources/struts.xml"));
        NodeList actions = struts.getElementsByTagName("action");
        Set<String> features = new HashSet<>();
        for (RbacEnums.Feature feature : RbacEnums.Feature.values()) features.add(feature.name());
        List<String> problems = new ArrayList<>();

        for (int i = 0; i < actions.getLength(); i++) {
            Element action = (Element) actions.item(i);
            String name = action.getAttribute("name");
            Map<String, String> params = roleCheckParams(action);
            if (params == null) {
                if (!ACTIONS_WITHOUT_ROLE_CHECK.contains(name)) problems.add(name + ": no role check");
                continue;
            }
            String label = params.get("featureLabel");
            if (!features.contains(label)) problems.add(name + ": unknown featureLabel " + label);
            String accessType = params.get("accessType");
            if (!"READ".equals(accessType) && !"READ_WRITE".equals(accessType)) problems.add(name + ": bad accessType " + accessType);
            String collectionScope = params.get("collectionScope");
            if (collectionScope != null) {
                try {
                    RbacEnums.CollectionScope.valueOf(collectionScope);
                } catch (IllegalArgumentException e) {
                    problems.add(name + ": bad collectionScope " + collectionScope);
                }
            }
        }
        assertTrue(String.join("\n", problems), problems.isEmpty());
    }

    @Test
    public void testRoleFromName() {
        assertEquals(Role.MEMBER, Role.fromName("MEMBER"));
        assertEquals(Role.MEMBER, Role.fromName("SECURITY ENGINEER"));
        assertEquals(Role.THREAT_ENGINEER, Role.fromName("THREAT ENGINEER"));
        assertEquals(Role.NO_ACCESS, Role.fromName("NO_ACCESS"));
        assertNull(Role.fromName("SOME_DELETED_ROLE"));
        assertNull(Role.fromName(null));
    }

    private static Role storedRole(int userId, String role) {
        Map<String, String> scopeRoleMapping = new HashMap<>();
        scopeRoleMapping.put(CONTEXT_SOURCE.API.name(), role);
        RBACDao.instance.insertOne(new RBAC(userId, null, ACCOUNT_ID, scopeRoleMapping));
        RBACDao.instance.deleteUserEntryFromCache(new Pair<>(userId, ACCOUNT_ID));
        return RBACDao.getCurrentRoleForUser(userId, ACCOUNT_ID);
    }

    @Test
    public void testStoredRoleResolution() {
        Context.accountId.set(ACCOUNT_ID);
        Context.contextSource.set(CONTEXT_SOURCE.API);
        RBACDao.instance.getMCollection().drop();
        CustomRoleDao.instance.getMCollection().drop();
        CustomRoleDao.instance.insertOne(new CustomRole("TEAM_ROLE", Role.THREAT_ENGINEER.name(), new ArrayList<>(), false, false, new ArrayList<>()));

        assertEquals(Role.ADMIN, storedRole(201, "ADMIN"));
        assertEquals(Role.MEMBER, storedRole(202, "SECURITY ENGINEER")); // display name stored by older signups
        assertEquals(Role.THREAT_ENGINEER, storedRole(203, "TEAM_ROLE"));
        assertEquals(Role.GUEST, storedRole(204, "SOME_DELETED_ROLE")); // least privilege, not an exception
    }
}
