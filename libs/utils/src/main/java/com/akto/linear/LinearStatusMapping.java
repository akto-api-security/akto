package com.akto.linear;

import com.akto.linear.LinearApiClient.LinearWorkflowState;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LinearStatusMapping {

    private static final Map<String, TestRunIssueStatus> STATE_TYPE_TO_AKTO = new HashMap<>();
    private static final Map<TestRunIssueStatus, List<String>> AKTO_TO_STATE_TYPES = new HashMap<>();

    static {
        STATE_TYPE_TO_AKTO.put("triage", TestRunIssueStatus.OPEN);
        STATE_TYPE_TO_AKTO.put("backlog", TestRunIssueStatus.OPEN);
        STATE_TYPE_TO_AKTO.put("unstarted", TestRunIssueStatus.OPEN);
        STATE_TYPE_TO_AKTO.put("started", TestRunIssueStatus.OPEN);
        STATE_TYPE_TO_AKTO.put("completed", TestRunIssueStatus.FIXED);
        STATE_TYPE_TO_AKTO.put("canceled", TestRunIssueStatus.IGNORED);
        STATE_TYPE_TO_AKTO.put("duplicate", TestRunIssueStatus.IGNORED);

        AKTO_TO_STATE_TYPES.put(TestRunIssueStatus.OPEN, Arrays.asList("unstarted", "backlog"));
        AKTO_TO_STATE_TYPES.put(TestRunIssueStatus.FIXED, Collections.singletonList("completed"));
        AKTO_TO_STATE_TYPES.put(TestRunIssueStatus.IGNORED, Collections.singletonList("canceled"));
    }

    public static TestRunIssueStatus toAktoStatus(String stateType) {
        return stateType == null ? null : STATE_TYPE_TO_AKTO.get(stateType);
    }

    public static List<String> targetStateTypes(TestRunIssueStatus status) {
        return status == null ? null : AKTO_TO_STATE_TYPES.get(status);
    }

    public static Optional<LinearWorkflowState> targetState(List<LinearWorkflowState> states,
        TestRunIssueStatus status) {
        List<String> types = targetStateTypes(status);
        if (types == null || states == null) {
            return Optional.empty();
        }
        for (String type : types) {
            Optional<LinearWorkflowState> state = states.stream()
                .filter(candidate -> type.equals(candidate.getType()))
                .min(Comparator.comparingDouble(LinearWorkflowState::getPosition));
            if (state.isPresent()) {
                return state;
            }
        }
        return Optional.empty();
    }
}
