package com.akto.linear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class LinearStatusMappingTest {

    @Test
    public void mapsLinearStateTypesToAktoStatuses() {
        assertEquals(TestRunIssueStatus.OPEN, LinearStatusMapping.toAktoStatus("backlog"));
        assertEquals(TestRunIssueStatus.OPEN, LinearStatusMapping.toAktoStatus("started"));
        assertEquals(TestRunIssueStatus.FIXED, LinearStatusMapping.toAktoStatus("completed"));
        assertEquals(TestRunIssueStatus.IGNORED, LinearStatusMapping.toAktoStatus("canceled"));
        assertEquals(TestRunIssueStatus.IGNORED, LinearStatusMapping.toAktoStatus("duplicate"));
        assertNull(LinearStatusMapping.toAktoStatus("unknown"));
        assertNull(LinearStatusMapping.toAktoStatus(null));
    }

    @Test
    public void picksLinearStateTypesForAktoStatuses() {
        assertEquals(Arrays.asList("unstarted", "backlog"), LinearStatusMapping.targetStateTypes(TestRunIssueStatus.OPEN));
        assertEquals(Arrays.asList("completed"), LinearStatusMapping.targetStateTypes(TestRunIssueStatus.FIXED));
        assertEquals(Arrays.asList("canceled"), LinearStatusMapping.targetStateTypes(TestRunIssueStatus.IGNORED));
        assertNull(LinearStatusMapping.targetStateTypes(null));
    }
}
