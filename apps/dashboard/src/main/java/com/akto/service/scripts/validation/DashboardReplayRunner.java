package com.akto.service.scripts.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dao.CustomAuthTypeDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.CustomAuthType;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.rules.RequiredConfigs;
import com.akto.store.SampleMessageStore;
import com.akto.test_editor.TestingUtilsSingleton;
import com.akto.test_editor.execution.Executor;
import com.akto.testing.TestExecutor;
import com.akto.testing.scripts.ScriptDiagnosticsCollector;

/**
 * Runs the replay template the way the test editor runs a template inline
 * (SaveTestEditorAction.runTestForGivenTemplate), with the diagnostics collector active on this thread.
 * Uses the runTestNew overload that takes the RawApi, so the replay always sends this sample and never
 * one cached by TestingConfigurations for the same collection, url and method.
 */
public class DashboardReplayRunner implements ReplayRunner {

    @Override
    public ReplayOutcome replay(ApiInfo.ApiInfoKey apiInfoKey, String sample, TestingRunConfig testingRunConfig) throws Exception {
        // Role cache is static and shared across accounts; cleared on entry like the test editor does.
        Executor.clearRoleCache();
        TestConfig testConfig = ScriptValidationReplayTemplate.parse();
        TestingUtilsSingleton.init();
        RequiredConfigs.initiate();

        Map<ApiInfo.ApiInfoKey, List<String>> sampleDataMap = new HashMap<>();
        sampleDataMap.put(apiInfoKey, Collections.singletonList(sample));
        SampleMessageStore messageStore = SampleMessageStore.create(sampleDataMap);
        List<CustomAuthType> customAuthTypes = CustomAuthTypeDao.instance.findAll(CustomAuthType.ACTIVE, true);
        RawApi rawApi = RawApi.buildFromMessage(sample, true);
        // The engine refuses to run without the account's attacker role, as in TestExecutor.runTestNew(TestingUtil...).
        TestRoles attackerRole = Executor.fetchOrFindAttackerRole();
        AuthMechanism attackerAuthMechanism = attackerRole == null ? null : attackerRole.findMatchingAuthMechanism(rawApi);
        List<TestingRunResult.TestLog> testLogs = new ArrayList<>();

        ScriptDiagnosticsCollector collector = ScriptDiagnosticsCollector.begin();
        try {
            TestingRunResult result = new TestExecutor().runTestNew(apiInfoKey, null, messageStore, attackerAuthMechanism, customAuthTypes,
                null, testConfig, testingRunConfig, true, testLogs, rawApi);
            return ReplayOutcome.from(result, collector.toDiagnostics(), null);
        } finally {
            ScriptDiagnosticsCollector.end();
        }
    }
}
