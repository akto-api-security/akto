const SMART_TESTING_TRACE_ACCOUNT_ID = 1669322524;

export function isRunAutomatedTestsEnabled(value) {
    return value === true || value === 'true' || value === 1;
}

export function shouldShowSmartTestingExecutionTrace(runAutomatedTests) {
    return (
        isRunAutomatedTestsEnabled(runAutomatedTests) &&
        Number(window.ACTIVE_ACCOUNT) === SMART_TESTING_TRACE_ACCOUNT_ID
    );
}
