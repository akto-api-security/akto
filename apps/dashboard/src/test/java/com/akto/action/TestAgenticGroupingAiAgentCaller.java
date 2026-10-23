package com.akto.action;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.CollectionTags;
import com.akto.util.AgenticObserveUtil;
import com.akto.util.Constants;

/**
 * findTypeTag is only the first gate. Drives the real classifyAllGroups so the rest of the path is
 * covered too - the blank-hostName skip, the service-name grouping and the group's resulting type -
 * rather than asserting the classifier in isolation and assuming the collection reaches Argus.
 */
public class TestAgenticGroupingAiAgentCaller {

    private ApiCollection collection(int id, String hostName, String... tagKeys) {
        ApiCollection c = new ApiCollection(id, hostName, (int) (System.currentTimeMillis() / 1000),
                new HashSet<>(), hostName, 0, false, true);
        List<CollectionTags> tags = new ArrayList<>();
        for (String key : tagKeys) {
            tags.add(new CollectionTags(0, key, "v", CollectionTags.TagSource.KUBERNETES));
        }
        c.setTagsList(tags);
        return c;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> classify(List<ApiCollection> collections) throws Exception {
        Method m = AgenticObserveAction.class.getDeclaredMethod("classifyAllGroups",
                List.class, Map.class, Map.class, Map.class);
        m.setAccessible(true);
        return (Map<String, Object>) m.invoke(new AgenticObserveAction(), collections,
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
    }

    private String fieldOf(Object group, String name) throws Exception {
        Field f = group.getClass().getDeclaredField(name);
        f.setAccessible(true);
        Object v = f.get(group);
        return v == null ? null : v.toString();
    }

    @Test
    public void aiAgentCallerCollectionReachesArgusAsAService() throws Exception {
        Map<String, Object> groups = classify(Arrays.asList(
                collection(1, "payments-worker", Constants.AKTO_AI_AGENT_CALLER_TAG)));

        Object group = groups.get("service|payments-worker");
        assertNotNull("the caller must produce a group or it never shows in Argus: " + groups.keySet(),
                group);
        assertEquals("payments-worker", fieldOf(group, "name"));
        assertEquals("service", fieldOf(group, "rowType"));
        assertEquals(AgenticObserveUtil.CLIENT_TYPE_AI_AGENT, fieldOf(group, "clientType"));
    }

    @Test
    public void bareServiceNameIsNotLostByServiceNameGrouping() throws Exception {
        // extractServiceNameForGrouping strips leading labels on dotted hosts; a stub is named after
        // its service and has no dots at all, which must still group rather than return blank.
        Map<String, Object> groups = classify(Arrays.asList(
                collection(2, "a.b.internal.svc", Constants.AKTO_AI_AGENT_CALLER_TAG),
                collection(3, "flat-name", Constants.AKTO_AI_AGENT_CALLER_TAG)));

        assertNotNull("dotted host should group: " + groups.keySet(), groups.get("service|internal.svc"));
        assertNotNull("undotted host should group: " + groups.keySet(), groups.get("service|flat-name"));
    }

    @Test
    public void collectionWithoutTheTagStillProducesNoGroup() throws Exception {
        Map<String, Object> groups = classify(Arrays.asList(
                collection(4, "ordinary-api", "privatecloud.agoda.com/service")));
        assertNull("an ordinary collection must not appear in Argus", groups.get("service|ordinary-api"));
    }

    @Test
    public void blankHostNameIsStillSkipped() throws Exception {
        ApiCollection c = new ApiCollection(5, "no-host", (int) (System.currentTimeMillis() / 1000),
                new HashSet<>(), null, 0, false, true);
        c.setTagsList(new ArrayList<>(Collections.singletonList(
                new CollectionTags(0, Constants.AKTO_AI_AGENT_CALLER_TAG, "v", CollectionTags.TagSource.KUBERNETES))));

        assertEquals("pre-1.75.6 stubs have no hostName and are skipped before classification",
                0, classify(Collections.singletonList(c)).size());
    }

    @Test
    public void genAiCollectionIsUnaffected() throws Exception {
        Map<String, Object> groups = classify(Arrays.asList(
                collection(6, "llm-gateway.internal", Constants.AKTO_GEN_AI_TAG)));
        Object group = groups.get("service|llm-gateway.internal");
        assertNotNull("existing gen-ai grouping must not regress: " + groups.keySet(), group);
        assertEquals(AgenticObserveUtil.CLIENT_TYPE_AI_AGENT, fieldOf(group, "clientType"));
    }
}
