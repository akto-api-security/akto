// Turns a test run's smart-testing steps (every API's agent) into one conversation. Pure: no React, no requests.

export const LIVE_POLL_MS = 1000; // while an agent is working
export const IDLE_POLL_MS = 4000; // when none is: new APIs of the run may still start

const STATUS = {
    RUNNING: { tone: 'info', label: 'Running' },
    COMPLETED: { tone: 'success', label: 'Done' },
    STOPPED: { tone: 'warning', label: 'Stopped' },
    SKIPPED: { tone: undefined, label: 'Skipped' },
    FAILED: { tone: 'critical', label: 'Failed' },
};

export function statusBadge(status) {
    return STATUS[status] || { tone: undefined, label: status || 'Unknown' };
}

// An API whose agent sent nothing for this long is not shown as running (its "finished" call may have been lost).
const STALE_RUN_SECS = 30 * 60;

export function isRunning(run, nowSecs = Math.floor(Date.now() / 1000)) {
    if (run?.status !== 'RUNNING') return false;
    const lastSeen = run.updatedTs || run.startedTs || 0;
    return nowSecs - lastSeen < STALE_RUN_SECS;
}

export function testName(testSubType, subCategoryMap) {
    return subCategoryMap?.[testSubType]?.testName || testSubType || '';
}

const eventKey = (event) => `${event.runKey}:${event.seq}`;

/** Known events plus fresh ones, without duplicates (polls overlap by a second on purpose). */
export function mergeEvents(known, fresh) {
    if (!fresh?.length) return known;
    const seen = new Set(known.map(eventKey));
    const added = fresh.filter((event) => !seen.has(eventKey(event)));
    return added.length ? [...known, ...added] : known;
}

/** Where the next poll starts: the newest event's second (the backend returns events at or after it). */
export function nextCursor(events, previous) {
    return events.reduce((latest, event) => Math.max(latest, event.timestamp || 0), previous || 0);
}

const verdictOf = (text) => {
    if (/^Vulnerable:/.test(text)) return 'vulnerable';
    if (/^Not vulnerable:/.test(text)) return 'safe';
    return null;
};

/**
 * One API's steps as blocks: notes (what the agent is doing), a repair (getting the captured request to work) and
 * attempts (plan → request → response → checks → verdict), in the order they happened.
 */
export function blocksOf(events) {
    const blocks = [];
    const attempts = new Map();
    let repair = null;
    for (const event of events) {
        const phase = (event.phase || '').toLowerCase();
        const text = event.content || '';
        const key = eventKey(event);
        if (phase === 'repair') {
            if (!repair) {
                repair = { type: 'repair', key, steps: [] };
                blocks.push(repair);
            }
            repair.steps.push({ key, text });
        } else if (['plan', 'action', 'observation', 'validation'].includes(phase) && event.attempt > 0) {
            let attempt = attempts.get(event.attempt);
            if (!attempt) {
                attempt = { type: 'attempt', key, number: event.attempt, plan: '', request: '', response: '', checks: [], verdict: null, verdictText: '' };
                attempts.set(event.attempt, attempt);
                blocks.push(attempt);
            }
            if (phase === 'plan') attempt.plan = text;
            else if (phase === 'action') attempt.request = text;
            else if (phase === 'observation') attempt.response = text;
            else if (verdictOf(text)) {
                attempt.verdict = verdictOf(text);
                attempt.verdictText = text.replace(/^(Not vulnerable|Vulnerable):\s*/, '');
            } else attempt.checks.push(text);
        } else if (phase === 'user') {
            blocks.push({ type: 'read', key, text });
        } else {
            blocks.push({ type: 'note', key, text, final: phase === 'status' && /^(Finished|Skipped):/.test(text) });
        }
    }
    return blocks;
}

/** Whether an attempt was stopped by a safety rule before it was sent. */
export function wasRefused(attempt) {
    return /refused:/.test(attempt.response);
}

/**
 * The whole run as a conversation: one section per API (in the order they started) and the watcher's messages,
 * each placed at the moment it was sent.
 */
export function conversation(runs, events, messages) {
    const byRun = new Map();
    for (const event of events) {
        if (!byRun.has(event.runKey)) byRun.set(event.runKey, []);
        byRun.get(event.runKey).push(event);
    }
    const items = (runs || []).map((run) => {
        const blocks = blocksOf((byRun.get(run.runKey) || []).slice().sort((a, b) => a.seq - b.seq));
        const attempts = blocks.filter((block) => block.type === 'attempt');
        return {
            type: 'api',
            key: run.runKey,
            ts: run.startedTs || 0,
            run,
            blocks,
            attempts: attempts.length,
            vulnerable: attempts.filter((attempt) => attempt.verdict === 'vulnerable').length,
        };
    });
    for (const message of messages || []) {
        items.push({ type: 'user', key: `message:${message.timestamp}:${message.text}`, ts: message.timestamp || 0, text: message.text });
    }
    return items.sort((a, b) => a.ts - b.ts || (a.type === 'user' ? 1 : -1));
}

/** Totals for the header. */
export function totals(items) {
    const apis = items.filter((item) => item.type === 'api');
    return {
        apis: apis.length,
        running: apis.filter((item) => isRunning(item.run)).length,
        vulnerable: apis.reduce((sum, item) => sum + item.vulnerable, 0),
    };
}

/** The request without its host and query string: "PUT /api/…/alertConfigs/6af5… | webhookUrl: …". */
export function shortRequest(text) {
    return (text || '').replace(/https?:\/\/[^/\s]+/, '').replace(/\?[^\s|]*/, '');
}

/** "HTTP 400" and the server's own message, from an observation like 'HTTP 400. Body: {"detail": "…"} — …'. */
export function responseSummary(text) {
    const value = text || '';
    const status = (value.match(/HTTP (\d{3})/) || [])[1] || null;
    const message = (value.match(/"(?:detail|message|error_description|errorMessage)"\s*:\s*"([^"]{1,300})"/) || [])[1]
        || (value.match(/refused:\s*([^\n]+)/) || [])[1]
        || null;
    return { status, message };
}

/** One short line for an attempt's result. */
export function attemptResult(attempt) {
    if (attempt.verdict === 'vulnerable') return { tone: 'critical', label: 'Vulnerable' };
    if (attempt.verdict === 'safe') return { tone: 'success', label: 'Not vulnerable' };
    if (wasRefused(attempt)) return { tone: 'subdued', label: 'Not sent · safety rule' };
    const { status } = responseSummary(attempt.response);
    if (status) return { tone: 'subdued', label: `HTTP ${status}` };
    return { tone: 'info', label: attempt.request ? 'Sending' : 'Planning' };
}

/** Whether a step only repeats what the API's header already shows. */
export function repeatsHeader(block) {
    return block.type === 'note' && /^Testing [A-Z]+ /.test(block.text);
}
