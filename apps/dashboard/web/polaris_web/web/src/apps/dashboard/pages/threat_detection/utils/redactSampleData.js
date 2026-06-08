/** Keys whose values are replaced with "****" (case-insensitive JSON keys; protobuf header blocks). */
export const REDACTED_SAMPLE_KEYWORDS = [
    "password",
    "passwd",
    "secret",
    "token",
    "access_token",
    "accessToken",
    "refresh_token",
    "refreshToken",
    "id_token",
    "idToken",
    "authorization",
    "cookie",
    "apiKey",
    "api_key",
    "apikey",
    "ssn",
    "phone",
    "phoneNumber",
    "creditCard",
    "credit_card",
    "cvv",
    "pin",
];

export const REDACT_PLACEHOLDER = "****";

/**
 * Redacts values for sensitive JSON keys and protobuf-style `key: "name" ... values: "..."` blocks
 * inside a single text blob (e.g. mirror/protobuf payloads that are not valid top-level JSON).
 */
export function redactEmbeddedSensitiveStrings(
    text,
    redactedKeywords = REDACTED_SAMPLE_KEYWORDS,
    placeholder = REDACT_PLACEHOLDER
) {
    if (typeof text !== "string" || text.length === 0) {
        return text;
    }
    let result = text;
    const keywords = [...redactedKeywords].sort((a, b) => String(b).length - String(a).length);
    try {
        for (const kw of keywords) {
            const esc = String(kw).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
            const reJson = new RegExp(
                `("${esc}"\\s*:\\s*")((?:[^"\\\\]|\\\\.)*)(")`,
                "gi"
            );
            result = result.replace(reJson, (_, a, __, c) => a + placeholder + c);
        }
        for (const kw of keywords) {
            const esc = String(kw).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
            const reProto = new RegExp(
                `(key:\\s*"${esc}"[\\s\\S]*?values:\\s*")((?:[^"\\\\]|\\\\.)*)(")`,
                "gi"
            );
            result = result.replace(reProto, (_, a, __, c) => a + placeholder + c);
        }
    } catch {
        return text;
    }
    return result;
}

export function redactSampleDataByKeywords(data, redactedKeywords = REDACTED_SAMPLE_KEYWORDS) {
    const placeholder = REDACT_PLACEHOLDER;
    try {
        const keySet = new Set(redactedKeywords.map((k) => String(k).toLowerCase()));
        const shouldRedactKey = (key) => keySet.has(String(key).toLowerCase());

        function redact(value) {
            try {
                if (value === null || value === undefined) {
                    return value;
                }
                const t = typeof value;
                if (t === "string") {
                    let processed = value;
                    const trimmed = value.trim();
                    if (
                        (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                        (trimmed.startsWith("[") && trimmed.endsWith("]"))
                    ) {
                        try {
                            const parsed = JSON.parse(value);
                            processed = JSON.stringify(redact(parsed));
                        } catch {
                            processed = value;
                        }
                    }
                    return redactEmbeddedSensitiveStrings(processed, redactedKeywords, placeholder);
                }
                if (Array.isArray(value)) {
                    return value.map((item) => {
                        try {
                            return redact(item);
                        } catch {
                            return item;
                        }
                    });
                }
                if (t === "object") {
                    const out = {};
                    try {
                        Object.keys(value).forEach((k) => {
                            try {
                                const v = value[k];
                                if (shouldRedactKey(k)) {
                                    out[k] = placeholder;
                                } else {
                                    out[k] = redact(v);
                                }
                            } catch {
                                try {
                                    out[k] = value[k];
                                } catch {
                                    out[k] = placeholder;
                                }
                            }
                        });
                    } catch {
                        return value;
                    }
                    return out;
                }
                return value;
            } catch {
                return value;
            }
        }

        return redact(data);
    } catch {
        return data;
    }
}
