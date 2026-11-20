package com.akto.hybrid_runtime;

import com.akto.dto.settings.DataControlSettings;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** pushSensitiveSampleOnce must be off unless explicitly turned on, so the old behaviour is the default. */
public class DataControlFetcherFlagTest {

    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static void setSettings(DataControlSettings settings) throws Exception {
        Field f = DataControlFetcher.class.getDeclaredField("dataControlSettings");
        f.setAccessible(true);
        f.set(null, settings);
    }

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeTrue(System.getenv("PUSH_SENSITIVE_SAMPLE_ONCE") == null,
                "env override is set, server flag cannot be tested");
        setSettings(null);
    }

    @AfterEach
    void tearDown() throws Exception {
        setSettings(null);
    }

    @Test
    void offWhenNothingFetched() {
        assertFalse(DataControlFetcher.pushSensitiveSampleOnce());
    }

    @Test
    void offWhenServerSettingsHaveNoFlag() throws Exception {
        setSettings(new DataControlSettings());
        assertFalse(DataControlFetcher.pushSensitiveSampleOnce());
    }

    @Test
    void offWhenServerResponseOmitsTheField() throws Exception {
        // old cyborg / old doc: the field is simply not in the json
        DataControlSettings parsed = mapper.readValue("{\"id\":1,\"discardOldApi\":true}", DataControlSettings.class);
        setSettings(parsed);
        assertFalse(DataControlFetcher.pushSensitiveSampleOnce());
    }

    @Test
    void onWhenServerSaysTrue() throws Exception {
        DataControlSettings parsed = mapper.readValue("{\"id\":1,\"pushSensitiveSampleOnce\":true}", DataControlSettings.class);
        setSettings(parsed);
        assertTrue(DataControlFetcher.pushSensitiveSampleOnce());
    }

    @Test
    void offAgainWhenFetchFailsAfterBeingOn() throws Exception {
        DataControlSettings on = new DataControlSettings();
        on.setPushSensitiveSampleOnce(true);
        setSettings(on);
        assertTrue(DataControlFetcher.pushSensitiveSampleOnce());
        setSettings(null); // a failed fetch assigns null
        assertFalse(DataControlFetcher.pushSensitiveSampleOnce());
    }

    @Test
    void otherServerTogglesAreUnaffected() throws Exception {
        DataControlSettings parsed = mapper.readValue(
                "{\"id\":1,\"discardOldApi\":true,\"discardNewApi\":true,\"pushSensitiveSampleOnce\":true}", DataControlSettings.class);
        setSettings(parsed);
        assertTrue(DataControlFetcher.discardOldApi());
        assertTrue(DataControlFetcher.discardNewApi());
        assertFalse(DataControlFetcher.stopIngestionFromKafka());
    }

    @Test
    void resettingExamplesDoesNotChangeIdentity() {
        // examples is not part of composeKey/equals/hashCode, so clearing it cannot break map or set lookups
        com.akto.dto.type.SingleTypeInfo.ParamId paramId = new com.akto.dto.type.SingleTypeInfo.ParamId(
                "/a", "GET", 200, false, "email", com.akto.dto.type.SingleTypeInfo.EMAIL, 1, false);
        Set<Object> examples = new HashSet<>();
        examples.add("raw");
        com.akto.dto.type.SingleTypeInfo info = new com.akto.dto.type.SingleTypeInfo(
                paramId, examples, new HashSet<>(), 0, 0, 0, new com.akto.types.CappedSet<>(),
                com.akto.dto.type.SingleTypeInfo.Domain.ENUM,
                com.akto.dto.type.SingleTypeInfo.ACCEPTED_MAX_VALUE, com.akto.dto.type.SingleTypeInfo.ACCEPTED_MIN_VALUE);

        String key = info.composeKey();
        int hash = info.hashCode();
        info.setExamples(new HashSet<>());

        assertEquals(key, info.composeKey());
        assertEquals(hash, info.hashCode());
        assertTrue(info.getExamples().isEmpty());
    }
}
