export const SMART_TESTING_TRACE_ACCOUNT_ID = 1669322524;

export function isRunAutomatedTestsEnabled(value) {
    return value === true || value === 'true' || value === 1;
}

export function shouldShowSmartTestingExecutionTrace(runAutomatedTests) {
    return isRunAutomatedTestsEnabled(runAutomatedTests);
}

export function transformAiSummaryToEvents(aiSummaryArray) {
    if (!Array.isArray(aiSummaryArray) || aiSummaryArray.length === 0) {
        return null;
    }

    return aiSummaryArray.map((item, index) => {
        const event = {
            id: `ai-${index}`,
            phase: item.phase.toUpperCase(),
            content: item.content || '',
        };

        if (item.attempt !== null && item.attempt !== undefined) {
            event.attempt = item.attempt;
        }

        if (item.statusCode !== null && item.statusCode !== undefined) {
            event.statusCode = item.statusCode;
        }

        if (item.requestSummary) {
            event.requestSummary = item.requestSummary;
        }

        if (item.vulnerable === true) {
            event.isVulnerable = true;
        }

        return event;
    });
}
