import { parsePromptText, parseResponseText, parseTokens, parseModel } from "./constants";

// Enriches a raw ES doc (prompt / span / message / session summary) with parsed
// display fields. Token totals prefer the backend-aggregated inputTokens/outputTokens
// (present on session/trace buckets) and fall back to per-doc parsing for raw spans.
export function enrichRow(row) {
    const parsed = parseTokens(row);
    const input = Number(row.inputTokens) || parsed.input;
    const output = Number(row.outputTokens) || parsed.output;
    const model = parseModel(row);
    return {
        ...row,
        _promptText: parsePromptText(row.queryPayload),
        _responseText: parseResponseText(row.responsePayload),
        _model: model,
        _models: model ? [model] : [],
        _inputTokens: input,
        _outputTokens: output,
        _tokens: input + " / " + output,
    };
}
