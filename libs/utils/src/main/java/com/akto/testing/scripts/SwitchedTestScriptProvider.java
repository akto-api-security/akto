package com.akto.testing.scripts;

import java.util.function.BooleanSupplier;

import com.akto.dto.testing.config.TestScript;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

/**
 * Serves the delegate only while the switch is on. When it is off, every lookup reports "not found" without
 * touching the delegate, so the chain carries on as if the source were not there.
 *
 * The database-abstractor puts Ask Akto validation drafts behind one of these so that serving them to
 * mini-testing can be stopped without a deploy (see TestScriptProviders.served).
 */
public class SwitchedTestScriptProvider implements TestScriptProvider {

    private static final LoggerMaker loggerMaker = new LoggerMaker(SwitchedTestScriptProvider.class, LogDb.DB_ABS);

    private final String name;
    private final TestScriptProvider delegate;
    private final BooleanSupplier enabled;

    public SwitchedTestScriptProvider(String name, TestScriptProvider delegate, BooleanSupplier enabled) {
        this.name = name;
        this.delegate = delegate;
        this.enabled = enabled;
    }

    @Override
    public TestScript findById(String id) {
        if (!enabled.getAsBoolean()) {
            loggerMaker.warn("script source {} is switched off, treating {} as not found", name, id);
            return null;
        }
        TestScript script = delegate.findById(id);
        if (script != null) {
            loggerMaker.info("served script {} from {}", id, name);
        }
        return script;
    }

    @Override
    public TestScript findDefault(TestScript.Type type) {
        return enabled.getAsBoolean() ? delegate.findDefault(type) : null;
    }
}
