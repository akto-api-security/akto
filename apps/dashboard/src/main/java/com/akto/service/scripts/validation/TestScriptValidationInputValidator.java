package com.akto.service.scripts.validation;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;

import com.akto.dto.type.URLMethods;
import com.akto.service.scripts.TestScriptException;
import com.akto.service.scripts.TestScriptValidator;

/** Input rules for a validation request. Pure: no database access. */
public class TestScriptValidationInputValidator {

    public static final int MAX_URL_LENGTH = 2000;
    public static final int MAX_MINI_TESTING_NAME_LENGTH = 256;
    private static final Pattern UUID_PATTERN =
        Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final TestScriptValidator scriptValidator = new TestScriptValidator();

    public void validate(ScriptValidationRequest request) throws TestScriptException {
        if (request == null) {
            throw new TestScriptException("Validation request is required");
        }
        if (request.getValidationId() == null || !UUID_PATTERN.matcher(request.getValidationId()).matches()) {
            throw new TestScriptException("validationId must be a UUID");
        }
        if (StringUtils.isBlank(request.getUrl()) || request.getUrl().length() > MAX_URL_LENGTH) {
            throw new TestScriptException("Endpoint url is required and must be at most " + MAX_URL_LENGTH + " characters");
        }
        parseMethod(request.getMethod());
        boolean hasPre = validatePhase("pre-request", request.getPreJavascript(), request.getPreScriptId());
        boolean hasPost = validatePhase("post-request", request.getPostJavascript(), request.getPostScriptId());
        if (!hasPre && !hasPost) {
            throw new TestScriptException("Choose a pre-request or a post-request script to validate");
        }
        if (StringUtils.isNotBlank(request.getTestRoleId()) && !ObjectId.isValid(request.getTestRoleId())) {
            throw new TestScriptException("Invalid test role");
        }
        if (request.getMiniTestingName() != null && request.getMiniTestingName().length() > MAX_MINI_TESTING_NAME_LENGTH) {
            throw new TestScriptException("Invalid testing module name");
        }
    }

    public static URLMethods.Method parseMethod(String method) throws TestScriptException {
        if (StringUtils.isBlank(method)) {
            throw new TestScriptException("Endpoint method is required");
        }
        try {
            return URLMethods.Method.valueOf(method.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new TestScriptException("Invalid endpoint method");
        }
    }

    /** True when the phase has a script. A blank javascript or scriptId counts as absent. */
    private boolean validatePhase(String label, String javascript, String scriptId) throws TestScriptException {
        boolean hasBody = StringUtils.isNotBlank(javascript);
        boolean hasId = StringUtils.isNotBlank(scriptId);
        if (hasBody && hasId) {
            throw new TestScriptException("Send either the " + label + " script body or a library script id, not both");
        }
        if (hasBody) {
            scriptValidator.validateJavascript(javascript);
        }
        return hasBody || hasId;
    }
}
