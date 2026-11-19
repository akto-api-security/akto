package com.akto.utils;

import com.akto.dto.AgenticUsers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LitellmUserRegistryTest {

    private Supplier<List<AgenticUsers>> originalFetch;
    private Consumer<List<AgenticUsers>> originalUpsert;
    private final List<AgenticUsers> upserted = new ArrayList<>();
    private int fetchCalls;

    private static AgenticUsers existing(String userName, String email) {
        AgenticUsers u = new AgenticUsers();
        u.setUserName(userName);
        u.setUserEmail(email);
        return u;
    }

    @Before
    public void setUp() {
        originalFetch = LitellmUserRegistry.fetchAllAgentUsers;
        originalUpsert = LitellmUserRegistry.upsertAgentUsers;
        LitellmUserRegistry.reset();
        LitellmUserRegistry.fetchAllAgentUsers = () -> {
            fetchCalls++;
            return Arrays.asList(existing("abhijeets-macbook-1a2b3c4d", "Native.User@example.com"), existing("picked@example.com", null));
        };
        LitellmUserRegistry.upsertAgentUsers = upserted::addAll;
    }

    @After
    public void tearDown() {
        LitellmUserRegistry.fetchAllAgentUsers = originalFetch;
        LitellmUserRegistry.upsertAgentUsers = originalUpsert;
        LitellmUserRegistry.reset();
    }

    @Test
    public void newEmailIsUpsertedOnceWithItsIdentity() {
        LitellmUserRegistry.register("Test.User@example.com");
        LitellmUserRegistry.register("test.user@example.com");
        assertEquals(1, upserted.size());
        AgenticUsers u = upserted.get(0);
        assertEquals("Test.User@example.com", u.getUserId());
        assertEquals("Test.User@example.com", u.getUserEmail());
        // The email's local part as written, like deriveUsernameFromEmail: not the host slug (test-user).
        assertEquals("Test.User", u.getUserName());
        assertEquals("litellm", u.getLastUpdatedBy());
    }

    @Test
    public void emailsAlreadyInAgentUsersAreNotUpserted() {
        LitellmUserRegistry.register("native.user@example.com");
        LitellmUserRegistry.register("PICKED@example.com");
        assertTrue(upserted.isEmpty());
    }

    @Test
    public void ownRowsAreUpsertedAgainToCorrectTheirUserName() {
        AgenticUsers stale = existing("test-user", "test.user@example.com");
        stale.setUserId("test.user@example.com");
        LitellmUserRegistry.fetchAllAgentUsers = () -> Collections.singletonList(stale);
        LitellmUserRegistry.register("test.user@example.com");
        assertEquals(1, upserted.size());
        assertEquals("test.user", upserted.get(0).getUserName());
    }

    @Test
    public void agentUsersIsReadOncePerProcess() {
        LitellmUserRegistry.register("a@example.com");
        LitellmUserRegistry.register("b@example.com");
        assertEquals(1, fetchCalls);
        assertEquals(2, upserted.size());
    }

    @Test
    public void failuresAreSwallowed() {
        LitellmUserRegistry.fetchAllAgentUsers = () -> { throw new RuntimeException("abstractor down"); };
        LitellmUserRegistry.upsertAgentUsers = users -> { throw new RuntimeException("abstractor down"); };
        LitellmUserRegistry.register("a@example.com");
    }

    @Test
    public void emailsWithoutALocalPartAreSkipped() {
        LitellmUserRegistry.register("@example.com");
        LitellmUserRegistry.register("not-an-email");
        assertTrue(upserted.isEmpty());
    }

    @Test
    public void blankEmailsAreSkipped() {
        LitellmUserRegistry.register(null);
        LitellmUserRegistry.register("  ");
        assertEquals(Collections.emptyList(), upserted);
        assertEquals(0, fetchCalls);
    }
}
