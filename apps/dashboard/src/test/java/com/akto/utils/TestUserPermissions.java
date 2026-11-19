package com.akto.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.akto.MongoBasedTest;
import com.akto.action.UserPermissionsAction;
import com.akto.dto.User;

/* The UI hides exactly what the server refuses: the permissions come from the same check, over the role checks in struts.xml. */
public class TestUserPermissions extends MongoBasedTest {

    static Map<String, String[]> checks;

    static Map<String, String[]> roleChecksFromStruts() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        Document struts = factory.newDocumentBuilder().parse(new File("src/main/resources/struts.xml"));
        NodeList actions = struts.getElementsByTagName("action");
        Map<String, String[]> result = new HashMap<>();
        for (int i = 0; i < actions.getLength(); i++) {
            Element action = (Element) actions.item(i);
            NodeList refs = action.getElementsByTagName("interceptor-ref");
            for (int j = 0; j < refs.getLength(); j++) {
                Element ref = (Element) refs.item(j);
                if (!"roleAccessInterceptor".equals(ref.getAttribute("name"))) continue;
                Map<String, String> params = new HashMap<>();
                NodeList paramNodes = ref.getElementsByTagName("param");
                for (int k = 0; k < paramNodes.getLength(); k++) {
                    Element param = (Element) paramNodes.item(k);
                    params.put(param.getAttribute("name"), param.getTextContent().trim());
                }
                result.put(action.getAttribute("name"), new String[]{params.get("featureLabel"), params.get("accessType"), params.get("collectionScope")});
            }
        }
        return result;
    }

    @Before
    public void setup() throws Exception {
        if (checks == null) checks = roleChecksFromStruts();
        ArgusScopeTestBase.setDashboardMode("SAAS");
        new TestAccessChanges().setup();
    }

    static List<String> denied(int userId) {
        return UserPermissionsAction.deniedActions(checks, TestRoleAssignment.user(userId), ACCOUNT_ID);
    }

    @Test
    public void testDeniedActionsFollowTheRole() {
        List<String> admin = denied(TestAccessChanges.ADMIN);
        assertTrue(admin.toString(), admin.isEmpty());

        List<String> member = denied(TestAccessChanges.MEMBER);
        assertTrue(member.contains("api/createCustomRole"));
        assertTrue(member.contains("api/addSplunkIntegration"));
        assertFalse(member.contains("api/inviteUsers"));
        assertFalse(member.contains("api/getAllCollectionsBasic"));

        List<String> guest = denied(TestAccessChanges.GUEST);
        assertTrue(guest.contains("api/inviteUsers"));
        assertTrue(guest.containsAll(member));
    }

    @Test
    public void testAdminLimitedToCollectionsCantManageAccess() {
        List<String> limitedAdmin = denied(TestAccessChanges.LIMITED_ADMIN);
        assertTrue(limitedAdmin.contains("api/createCustomRole"));
        assertTrue(limitedAdmin.contains("api/saveSamlGroupRoleMapping"));
        assertFalse(limitedAdmin.contains("api/modifyAccountSettings")); // other admin actions are unchanged
    }

    @Test
    public void testFeatureAccess() {
        User member = TestRoleAssignment.user(TestAccessChanges.MEMBER);
        Map<String, String> access = UserPermissionsAction.featureAccess(member, ACCOUNT_ID);
        assertEquals("NO_ACCESS", access.get("ADMIN_ACTIONS"));
        assertEquals("READ", access.get("INTEGRATIONS"));
        assertEquals("NO_ACCESS", access.get("THREAT_PROTECTION"));
        assertEquals("READ_WRITE", access.get("API_COLLECTIONS"));
    }
}
