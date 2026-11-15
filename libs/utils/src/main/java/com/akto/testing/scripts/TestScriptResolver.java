package com.akto.testing.scripts;

import java.util.Optional;

import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.config.ScriptSelection;
import com.akto.dto.testing.config.TestScript;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

/**
 * Decides which script a request runs, from the run's TestingRunConfig. The meaning of the stored id
 * comes from ScriptSelection; this class only dispatches on it.
 * A provider failure propagates to the caller; a selected script that is missing or of the wrong
 * type is logged and treated as "no script" for that request.
 */
public class TestScriptResolver {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestScriptResolver.class, LogDb.TESTING);

    private final TestScriptProvider provider;

    public TestScriptResolver(TestScriptProvider provider) {
        this.provider = provider;
    }

    /** No config means the request is not part of a testing run, so no script runs (unchanged behaviour). */
    public Optional<TestScript> resolve(TestingRunConfig config, TestScript.Type type) {
        if (config == null) {
            return Optional.empty();
        }
        String rawId = type == TestScript.Type.PRE_REQUEST ? config.getPreRequestScriptId() : config.getPostRequestScriptId();
        ScriptSelection selection = ScriptSelection.from(rawId, type);

        switch (selection.getKind()) {
            case NONE:
                return Optional.empty();
            case DEFAULT:
                return usable(provider.findDefault(type));
            case BY_ID:
                TestScript script = provider.findById(selection.getScriptId());
                if (script == null) {
                    loggerMaker.errorAndAddToDb("Selected " + type + " script " + selection.getScriptId()
                        + " not found for testing run config " + config.getId() + "; running without it");
                    return Optional.empty();
                }
                if (script.getType() != type) {
                    loggerMaker.errorAndAddToDb("Selected " + type + " script " + selection.getScriptId()
                        + " has type " + script.getType() + " in testing run config " + config.getId() + "; running without it");
                    return Optional.empty();
                }
                return usable(script);
            default:
                throw new IllegalStateException("Unhandled selection " + selection);
        }
    }

    private static Optional<TestScript> usable(TestScript script) {
        if (script == null || script.getJavascript() == null || script.getJavascript().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(script);
    }
}
