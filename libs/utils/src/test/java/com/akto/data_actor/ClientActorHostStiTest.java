package com.akto.data_actor;

import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.JSONUtils;
import com.mongodb.BasicDBObject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ClientActorHostStiTest {

    private static final String STI_ID = "65a1b2c3d4e5f60718293a4b";

    private final ClientActor clientActor = new ClientActor();

    // shaped like the JSON of fetchStiBasedOnHostHeaders: the ObjectId is serialized as an object, strId is its hex
    private static BasicDBObject abstractorStiDoc() {
        return new BasicDBObject("strId", STI_ID)
                .append("id", new BasicDBObject("timestamp", 1))
                .append("url", "/api/users")
                .append("method", "GET")
                .append("responseCode", -1)
                .append("isHeader", true)
                .append("param", "host")
                .append("subType", new BasicDBObject("name", "GENERIC"))
                .append("apiCollectionId", 7)
                .append("count", 5)
                .append("timestamp", 10)
                .append("lastSeen", 1_700_000_000L);
    }

    private static void assertSameSti(SingleTypeInfo expected, SingleTypeInfo actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getUrl(), actual.getUrl());
        assertEquals(expected.getMethod(), actual.getMethod());
        assertEquals(expected.getResponseCode(), actual.getResponseCode());
        assertEquals(expected.getIsHeader(), actual.getIsHeader());
        assertEquals(expected.getParam(), actual.getParam());
        assertEquals(expected.getSubType(), actual.getSubType());
        assertEquals(expected.getApiCollectionId(), actual.getApiCollectionId());
        assertEquals(expected.getCount(), actual.getCount());
        assertEquals(expected.getTimestamp(), actual.getTimestamp());
        assertEquals(expected.getLastSeen(), actual.getLastSeen());
    }

    @Test
    public void rawStisParseToTheSameObjectsAsTheOnesJustFetched() throws Exception {
        SingleTypeInfo fetchedSti = JSONUtils.parseSingleTypeInfo(abstractorStiDoc(), clientActor.objectMapper);
        String rawSti = abstractorStiDoc().toJson();

        List<SingleTypeInfo> parsedStis = clientActor.parseRawStis(Arrays.asList(rawSti, rawSti));

        assertEquals(2, parsedStis.size());
        assertEquals(STI_ID, parsedStis.get(0).getId().toHexString());
        assertEquals(SingleTypeInfo.GENERIC, parsedStis.get(0).getSubType());
        assertSameSti(fetchedSti, parsedStis.get(0));
        assertSameSti(fetchedSti, parsedStis.get(1));
    }

    @Test
    public void everyParseGivesAnIndependentObject() {
        // the runtime changes the STIs of its catalog in place, so a refresh must never hand out a shared one
        String rawSti = abstractorStiDoc().toJson();

        List<SingleTypeInfo> firstParse = clientActor.parseRawStis(Collections.singletonList(rawSti));
        List<SingleTypeInfo> secondParse = clientActor.parseRawStis(Collections.singletonList(rawSti));
        firstParse.get(0).setCount(999);

        assertNotSame(firstParse.get(0), secondParse.get(0));
        assertEquals(5, secondParse.get(0).getCount());
    }

    @Test
    public void aRawStiThatCannotBeParsedGivesNull() {
        assertNull(clientActor.parseRawStis(Collections.singletonList("not json")));
    }
}
