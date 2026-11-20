package com.akto.service.scripts.validation;

import com.akto.dto.ApiInfo;
import com.akto.dto.testing.TestingRunConfig;

/** Replays one captured request in the dashboard JVM with the scripts the config selects. */
public interface ReplayRunner {
    ReplayOutcome replay(ApiInfo.ApiInfoKey apiInfoKey, String sample, TestingRunConfig testingRunConfig) throws Exception;
}
