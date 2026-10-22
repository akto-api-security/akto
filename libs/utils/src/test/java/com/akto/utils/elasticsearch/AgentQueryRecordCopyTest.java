package com.akto.utils.elasticsearch;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * withPayloads copies the record field by field. This fails when a field is added to
 * AgentQueryRecord and not carried over, which would silently drop it from every redacted record.
 */
public class AgentQueryRecordCopyTest {

    private static final Set<String> CHANGED_BY_COPY = new HashSet<>(
            Arrays.asList("queryPayload", "responsePayload", "payloadRedaction"));

    @Test
    public void everyFieldExceptThePayloadsAndMarkerIsCarriedOver() throws Exception {
        AgentQueryRecord original = new AgentQueryRecord("doc", 1000, "svc", "dev", "user", "sess", "q", "r",
                1700000000000L, 11, 22, "trace", "span", true);
        Method setVerdict = AgentQueryRecord.class.getDeclaredMethod("setGuardrailVerdict", String.class);
        setVerdict.setAccessible(true);
        setVerdict.invoke(original, "{\"guardrailViolated\":true,\"guardrailAction\":\"block\","
                + "\"guardrailPolicy\":\"p\",\"guardrailRule\":\"r\",\"guardrailReason\":\"why\","
                + "\"guardrailSeverity\":\"HIGH\"}");

        AgentQueryRecord copy = original.withPayloads("masked q", "masked r", "BASIC");

        for (Field field : AgentQueryRecord.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || CHANGED_BY_COPY.contains(field.getName())) {
                continue;
            }
            field.setAccessible(true);
            assertEquals(field.get(original), field.get(copy),
                    "withPayloads did not carry over field '" + field.getName() + "'");
        }
        assertEquals("masked q", copy.getQueryPayload());
        assertEquals("masked r", copy.getResponsePayload());
        assertEquals("BASIC", copy.getPayloadRedaction());
    }
}
