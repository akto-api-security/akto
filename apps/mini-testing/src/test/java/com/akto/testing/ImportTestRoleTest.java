package com.akto.testing;

import com.akto.dao.context.Context;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.AuthParam;
import com.akto.dto.testing.HardcodedAuthParam;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.sources.AuthWithCond;
import com.akto.test_editor.execution.Executor;
import com.akto.util.Constants;
import com.akto.util.enums.LoginFlowEnums;
import org.bson.types.ObjectId;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ImportTestRoleTest {

    // a role mini-testing already fetched, so the test needs no database
    @SuppressWarnings("unchecked")
    private static TestRoles cachedRole(List<AuthWithCond> auth) throws Exception {
        TestRoles role = new TestRoles(new ObjectId(), "pdh-user", null, auth, "test", 0, 0, null);
        role.setLastFetched(Context.now());
        Field cache = Executor.class.getDeclaredField("roleCache");
        cache.setAccessible(true);
        ((Map<String, TestRoles>) cache.get(null)).put(role.getHexId(), role);
        return role;
    }

    private static TestRoles cookieRole() throws Exception {
        AuthMechanism auth = new AuthMechanism(
                Collections.singletonList(new HardcodedAuthParam(AuthParam.Location.HEADER, "Cookie", "session=secret", true)),
                null, LoginFlowEnums.AuthMechanismTypes.HARDCODED.toString(), null);
        return cachedRole(Collections.singletonList(new AuthWithCond(auth, new HashMap<>(), null)));
    }

    private static OriginalHttpRequest importRequest(String roleId) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("origin", new ArrayList<>(Arrays.asList("https://pdh.example.com")));
        if (roleId != null) {
            headers.put(Constants.AKTO_TEST_ROLE_ID_HEADER, new ArrayList<>(Arrays.asList(roleId)));
        }
        return new OriginalHttpRequest("https://pdh.example.com/ws", "", "GET", "", headers, "WEBSOCKET");
    }

    @Test
    public void testNoRoleLeavesTheRequestAlone() {
        OriginalHttpRequest request = importRequest(null);
        Map<String, String> applied = new HashMap<>();
        assertNull(Main.applyImportTestRole(request, applied));
        assertEquals(Collections.singleton("origin"), request.getHeaders().keySet());
        assertTrue(applied.isEmpty());
    }

    @Test
    public void testRoleAuthIsAddedAndItsIdRemoved() throws Exception {
        TestRoles role = cookieRole();
        OriginalHttpRequest request = importRequest(role.getHexId());
        // the dashboard sends the role's header name empty; the role fills it in
        request.getHeaders().put("cookie", new ArrayList<>(Arrays.asList("")));
        Map<String, String> applied = new HashMap<>();

        assertNull(Main.applyImportTestRole(request, applied));
        assertEquals(Collections.singletonList("session=secret"), request.getHeaders().get("cookie"));
        assertFalse(request.getHeaders().containsKey(Constants.AKTO_TEST_ROLE_ID_HEADER));
        // the value it got goes back to the dashboard for the saved sample
        assertEquals(Collections.singletonMap("cookie", "session=secret"), applied);
    }

    @Test
    public void testRoleAuthHeaderIsAddedWhenMissing() throws Exception {
        TestRoles role = cookieRole();
        OriginalHttpRequest request = importRequest(role.getHexId());

        assertNull(Main.applyImportTestRole(request, new HashMap<>()));
        assertEquals(Collections.singletonList("session=secret"), request.getHeaders().get("cookie"));
    }

    @Test
    public void testRoleWithoutAuthIsAnError() throws Exception {
        TestRoles role = cachedRole(new ArrayList<>());
        OriginalHttpRequest request = importRequest(role.getHexId());
        Map<String, String> applied = new HashMap<>();

        assertEquals("No auth mechanism configured for test role pdh-user", Main.applyImportTestRole(request, applied));
        assertFalse(request.getHeaders().containsKey(Constants.AKTO_TEST_ROLE_ID_HEADER));
        assertTrue(applied.isEmpty());
    }

    @Test
    public void testAppliedAuthGoesBackOnTheResponse() {
        Map<String, List<String>> handshakeHeaders = new HashMap<>();
        handshakeHeaders.put("upgrade", Collections.singletonList("websocket"));
        OriginalHttpResponse response = new OriginalHttpResponse("", Collections.unmodifiableMap(handshakeHeaders), 101);

        Main.attachAppliedAuth(response, Collections.singletonMap("cookie", "session=secret"));
        assertEquals(Collections.singletonList("{\"cookie\": \"session=secret\"}"),
                response.getHeaders().get(Constants.AKTO_APPLIED_AUTH_HEADER));
        assertEquals(Collections.singletonList("websocket"), response.getHeaders().get("upgrade"));

        // nothing applied: the response is left as it was
        OriginalHttpResponse untouched = new OriginalHttpResponse("", new HashMap<>(), 101);
        Main.attachAppliedAuth(untouched, new HashMap<>());
        assertTrue(untouched.getHeaders().isEmpty());
    }
}
