import { parsePromptText, parseResponseText, parseTokens, parseModel } from "./constants";

// Enriches a raw ES doc (prompt / span / message summary) with parsed display fields.
export function enrichRow(row) {
    const tokens = parseTokens(row);
    return {
        ...row,
        _promptText: parsePromptText(row.queryPayload),
        _responseText: parseResponseText(row.responsePayload),
        _model: parseModel(row),
        _inputTokens: tokens.input,
        _outputTokens: tokens.output,
        _tokens: tokens.input + " / " + tokens.output,
    };
}
