package com.akto.detection;

import com.akto.dto.CustomDataType;
import com.akto.dto.traffic.SensitiveDataCount;
import com.akto.dto.type.SingleTypeInfo;
import org.junit.After;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SensitiveDataEventsTest {

    @After
    public void tearDown() {
        SensitiveDataEvents.setTrackedTypes(null);
        SensitiveDataEvents.drain();
    }

    @Test
    public void recordsNothingWhenNoTypesAreTracked() {
        SensitiveDataEvents.record(SingleTypeInfo.EMAIL);
        assertTrue(SensitiveDataEvents.drain().isEmpty());
    }

    @Test
    public void recordsOnlyTrackedTypesOncePerMessage() {
        SensitiveDataEvents.setTrackedTypes(Collections.singleton("EMAIL"));

        SensitiveDataEvents.record(SingleTypeInfo.EMAIL);
        SensitiveDataEvents.record(SingleTypeInfo.EMAIL);
        SensitiveDataEvents.record(SingleTypeInfo.GENERIC);
        SensitiveDataEvents.record(null);

        assertEquals(Collections.singleton("EMAIL"), SensitiveDataEvents.drain());
        assertTrue("drain must forget what it returned", SensitiveDataEvents.drain().isEmpty());
    }

    @Test
    public void tracksOnlyActiveCustomTypesMarkedSensitive() {
        CustomDataType always = customType("VERIFIED_CUSTOMER_EMAIL", true, true, null);
        CustomDataType inResponse = customType("BOOKING_ID", true, false,
                Collections.singletonList(SingleTypeInfo.Position.RESPONSE_PAYLOAD));
        CustomDataType notSensitive = customType("INTERNAL_CODE", true, false, Collections.emptyList());
        CustomDataType inactive = customType("OLD_TYPE", false, true, null);

        Set<String> names = SensitiveDataEvents.customSensitiveTypeNames(
                Arrays.asList(always, inResponse, notSensitive, inactive, null));

        assertEquals(new HashSet<>(Arrays.asList("VERIFIED_CUSTOMER_EMAIL", "BOOKING_ID")), names);
        assertTrue(SensitiveDataEvents.customSensitiveTypeNames(null).isEmpty());
    }

    @Test
    public void hourOfTruncatesToTheStartOfTheHour() {
        int hourStart = 1791536400; // an exact hour boundary
        assertEquals(hourStart, SensitiveDataCount.hourOf(hourStart));
        assertEquals(hourStart, SensitiveDataCount.hourOf(hourStart + 1));
        assertEquals(hourStart, SensitiveDataCount.hourOf(hourStart + 3599));
        assertEquals(hourStart + 3600, SensitiveDataCount.hourOf(hourStart + 3600));
    }

    private static CustomDataType customType(String name, boolean active, boolean sensitiveAlways,
                                             java.util.List<SingleTypeInfo.Position> positions) {
        CustomDataType type = new CustomDataType();
        type.setName(name);
        type.setActive(active);
        type.setSensitiveAlways(sensitiveAlways);
        type.setSensitivePosition(positions);
        return type;
    }
}
