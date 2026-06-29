import { parsePromptText, parseResponseText, parseTokens, parseModel } from "./constants";

// Bucket items into numBuckets time slots.
// Returns { counts, timestamps } where timestamps are the bucket midpoints in epoch seconds.
// getTime(item) → epoch seconds (0 is ignored).
export function buildSparkline(items, getTime, numBuckets = 12) {
    if (!items.length) return { counts: [0], timestamps: [0] };
    const times = items.map(getTime).filter(t => t > 0);
    if (!times.length) return { counts: [0], timestamps: [0] };
    const minT = Math.min(...times);
    const maxT = Math.max(...times);
    const range = maxT - minT || 1;
    const bucketSize = range / numBuckets;
    const counts = Array(numBuckets).fill(0);
    times.forEach(t => {
        const idx = Math.min(numBuckets - 1, Math.floor(((t - minT) / range) * numBuckets));
        counts[idx]++;
    });
    const timestamps = Array.from({ length: numBuckets }, (_, i) =>
        Math.round(minT + (i + 0.5) * bucketSize)
    );
    return { counts, timestamps };
}

// Same as buildSparkline but sums getValue(item) per bucket instead of counting.
export function buildWeightedSparkline(items, getTime, getValue, numBuckets = 12) {
    if (!items.length) return { counts: [0], timestamps: [0] };
    const pairs = items.map(item => ({ t: getTime(item), v: getValue(item) })).filter(p => p.t > 0);
    if (!pairs.length) return { counts: [0], timestamps: [0] };
    const minT = Math.min(...pairs.map(p => p.t));
    const maxT = Math.max(...pairs.map(p => p.t));
    const range = maxT - minT || 1;
    const bucketSize = range / numBuckets;
    const counts = Array(numBuckets).fill(0);
    pairs.forEach(({ t, v }) => {
        const idx = Math.min(numBuckets - 1, Math.floor(((t - minT) / range) * numBuckets));
        counts[idx] += v;
    });
    const timestamps = Array.from({ length: numBuckets }, (_, i) =>
        Math.round(minT + (i + 0.5) * bucketSize)
    );
    return { counts, timestamps };
}

// Enriches a raw ES doc (prompt / span / message / session summary) with parsed
// display fields. Already-set underscore fields are preserved when the parser
// produces an empty result (important for dummy/synthetic row objects).
export function enrichRow(row) {
    const parsed = parseTokens(row);
    // Prefer explicit camelCase API fields → then already-set underscore fields → then parsed payload
    const input  = Number(row.inputTokens)  || Number(row._inputTokens)  || parsed.input;
    const output = Number(row.outputTokens) || Number(row._outputTokens) || parsed.output;

    const model = parseModel(row);
    const backendModels = Array.isArray(row.models) ? row.models.filter(Boolean) : [];
    const modelsArr = backendModels.length
        ? backendModels
        : model
            ? [model]
            : (Array.isArray(row._models) && row._models.length ? row._models : []);

    const resolvedModel  = modelsArr[0] || model || row._model || "";
    const resolvedModels = modelsArr.length ? modelsArr : (resolvedModel ? [resolvedModel] : []);

    // Only overwrite _promptText / _responseText when the parser finds something;
    // otherwise keep what was already set on the row (e.g. from dummy data).
    const promptText   = parsePromptText(row.queryPayload)   || row._promptText   || "";
    const responseText = parseResponseText(row.responsePayload) || row._responseText || "";

    // Normalize duration — backend may return duration / latency / duration_ms
    const durationMs = Number(row.durationMs || row.duration || row.latency || row.duration_ms || 0);

    // Normalize latestTimestamp — some sources (e.g. flat prompt rows) only have timestamp
    const latestTimestamp = row.latestTimestamp || row.timestamp || 0;

    return {
        ...row,
        latestTimestamp,
        _promptText:   promptText,
        _responseText: responseText,
        _model:        resolvedModel,
        _models:       resolvedModels,
        _inputTokens:  input,
        _outputTokens: output,
        _tokens:       input + " / " + output,
        durationMs,
    };
}

const MONTHS = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];

// Format actual epoch-second bucket timestamps as sparkline tooltip labels.
// Detects the right granularity from the span of the timestamps themselves.
// > 2 years → "2024"; > 30 days → "Jun '25"; ≤ 30 days → "Jun 17".
export function formatSparklineLabels(timestamps) {
    if (!timestamps?.length) return [];
    const validTs = timestamps.filter(t => t > 0);
    if (!validTs.length) return timestamps.map(() => "");
    const rangeMs   = (Math.max(...validTs) - Math.min(...validTs)) * 1000;
    const multiYear = rangeMs > 2 * 365 * 86400 * 1000;
    const longRange = rangeMs > 30 * 86400 * 1000;
    return timestamps.map(t => {
        if (!t) return "";
        const d = new Date(t * 1000);
        if (multiYear) return String(d.getFullYear());
        if (longRange) return `${MONTHS[d.getMonth()]} '${String(d.getFullYear()).slice(2)}`;
        return `${MONTHS[d.getMonth()]} ${d.getDate()}`;
    });
}
