import { parsePromptText, parseResponseText, parseTokens, parseModel } from "./constants";

// Bucket items into numBuckets time slots and return a count array suitable for sparklines.
// getTime(item) → epoch seconds (0 is ignored).
export function buildSparkline(items, getTime, numBuckets = 12) {
    if (!items.length) return [0];
    const times = items.map(getTime).filter(t => t > 0);
    if (!times.length) return [0];
    const minT = Math.min(...times);
    const maxT = Math.max(...times);
    const range = maxT - minT || 1;
    const buckets = Array(numBuckets).fill(0);
    times.forEach(t => {
        const idx = Math.min(numBuckets - 1, Math.floor(((t - minT) / range) * numBuckets));
        buckets[idx]++;
    });
    return buckets;
}

// Same as buildSparkline but sums getValue(item) per bucket instead of counting.
export function buildWeightedSparkline(items, getTime, getValue, numBuckets = 12) {
    if (!items.length) return [0];
    const pairs = items.map(item => ({ t: getTime(item), v: getValue(item) })).filter(p => p.t > 0);
    if (!pairs.length) return [0];
    const minT = Math.min(...pairs.map(p => p.t));
    const maxT = Math.max(...pairs.map(p => p.t));
    const range = maxT - minT || 1;
    const buckets = Array(numBuckets).fill(0);
    pairs.forEach(({ t, v }) => {
        const idx = Math.min(numBuckets - 1, Math.floor(((t - minT) / range) * numBuckets));
        buckets[idx] += v;
    });
    return buckets;
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

// Build human-readable date labels for the 12-bucket sparkline, one per bucket.
// Short range (≤ 30 days) → "Jun 17"; longer → "Jun '25".
export function buildSparklineLabels(since, until, numBuckets = 12) {
    if (!since || !until || until <= since) return [];
    const rangeMs  = (until - since) * 1000;
    const bucketMs = rangeMs / numBuckets;
    const longRange = rangeMs > 30 * 86400 * 1000;
    const labels = [];
    for (let i = 0; i < numBuckets; i++) {
        const d = new Date(since * 1000 + (i + 0.5) * bucketMs);
        labels.push(longRange
            ? `${MONTHS[d.getMonth()]} '${String(d.getFullYear()).slice(2)}`
            : `${MONTHS[d.getMonth()]} ${d.getDate()}`
        );
    }
    return labels;
}
