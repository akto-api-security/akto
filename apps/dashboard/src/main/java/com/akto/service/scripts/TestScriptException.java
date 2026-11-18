package com.akto.service.scripts;

/** A rejected script-library request. The message is user-facing and is returned as the action error. */
public class TestScriptException extends Exception {

    public TestScriptException(String message) {
        super(message);
    }
}
