package com.akto.testing;

import com.akto.dto.ApiInfo;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.type.URLMethods;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestExecutorTest {

    private TestResult generateTestResult(boolean bigPayload) {
        String message = bigPayload ? StringUtils.repeat("A", 3_000_000) : "something small";
        String originalMessage = bigPayload ? StringUtils.repeat("B", 1_000_000) : "something small";
        return new TestResult(
                message, originalMessage, new ArrayList<>(), 100, false, TestResult.Confidence.LOW, null
        );
    }

    @Test
    public void testTrim() {
        List<TestResult> testResultList = new ArrayList<>();
        testResultList.add(generateTestResult(false));
        testResultList.add(generateTestResult(false));
        testResultList.add(generateTestResult(false));
        testResultList.add(generateTestResult(true));
        testResultList.add(generateTestResult(false));
        testResultList.add(generateTestResult(true));
        testResultList.add(generateTestResult(false));
        TestingRunResult testingRunResult = new TestingRunResult(
                new ObjectId(), new ApiInfo.ApiInfoKey(0, "url", URLMethods.Method.GET), "BOLA",
                "REPLACE_AUTH_TOKEN", testResultList ,true, new ArrayList<>(), 90, 0, 100, new ObjectId()
        );
        TestExecutor.trim(testingRunResult);
        assertEquals(5, testingRunResult.getTestResults().size());
    }
}
