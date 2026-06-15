import { useState } from "react";
import {
    VerticalStack,
    HorizontalStack,
    Box,
    Text,
    Button,
    Badge,
    Autocomplete,
    Checkbox,
} from "@shopify/polaris";
import { DeleteMajor } from "@shopify/polaris-icons";

// Entry kept as an object so it can be extended later without breaking stored data.
// `pattern` is a glob matched against "host+path" (e.g. "chatgpt.com/*", "*/v1/chat/completions").
const createEntry = (pattern) => ({ pattern });

// Lowercase/trim and strip any scheme prefix; keep "*" and "/".
const normalizePattern = (raw) =>
    (raw || "").trim().toLowerCase().replace(/^https?:\/\//, "");

// Host/path glob characters: letters, digits and . - _ / : ~ % plus the * wildcard.
const PATTERN_ALLOWED = /^[a-z0-9.\-_/:~%*]+$/;

// The host part (before the first "/") must be either exactly "*" (any host) or a domain whose
// labels may contain "*" but whose final label (TLD) is concrete - so "chatgpt.com",
// "*.openai.com", "localhost" are valid, while "*.*" (no real TLD) is not.
const HOST_PART = /^(\*|(?:[a-z0-9*]([a-z0-9*-]*[a-z0-9*])?\.)*[a-z0-9]([a-z0-9-]*[a-z0-9])?)$/;

// Validate a normalized pattern. Returns an error string, or "" when valid.
const validatePattern = (p) => {
    if (!p) {
        return "Enter a host or path pattern";
    }
    if (/\s/.test(p)) {
        return "Pattern cannot contain spaces";
    }
    if (!PATTERN_ALLOWED.test(p)) {
        return "Use only letters, numbers and . - _ / : and * (wildcard)";
    }
    // A pattern made only of wildcards / slashes (e.g. "*", "/*", "*/*") would block everything.
    if (/^[*/]+$/.test(p)) {
        return "Pattern is too broad - include a host or path (e.g. chatgpt.com/*)";
    }
    // The host part (before the first "/") must be a real domain or "*" for any host.
    const slash = p.indexOf("/");
    const hostPart = slash === -1 ? p : p.slice(0, slash);
    if (!HOST_PART.test(hostPart)) {
        return "Host must be a domain (e.g. chatgpt.com, *.openai.com) or * for any host - not " + hostPart;
    }
    return "";
};

export const BlockedHostsConfig = {
    number: 11,
    title: "Access restrictions",

    validate: () => ({ isValid: true, errorMessage: null }),

    getSummary: ({ blockedHosts, blockPersonalAccounts }) => {
        const rows = (blockedHosts || []).filter((r) => (r.pattern || "").trim());
        const parts = [];
        if (rows.length > 0) {
            const names = rows.map((r) => r.pattern.trim()).slice(0, 2).join(", ");
            const more = rows.length > 2 ? ` +${rows.length - 2}` : "";
            parts.push(`${rows.length} pattern${rows.length === 1 ? "" : "s"}: ${names}${more}`);
        }
        if (blockPersonalAccounts) {
            parts.push("personal accounts blocked");
        }
        return parts.length > 0 ? parts.join(" · ") : "";
    }
};

const SectionCard = ({ title, description, children }) => (
    <Box
        padding="5"
        borderColor="border"
        borderWidth="1"
        borderRadius="3"
        background="bg-surface"
    >
        <VerticalStack gap="4">
            <VerticalStack gap="1">
                <Text variant="headingSm" as="h3">{title}</Text>
                <Text variant="bodySm" tone="subdued">{description}</Text>
            </VerticalStack>
            {children}
        </VerticalStack>
    </Box>
);

const BlockedHostsStep = ({ blockedHosts, setBlockedHosts, blockPersonalAccounts, setBlockPersonalAccounts, hostSuggestions = [] }) => {
    const entries = blockedHosts || [];
    const [inputValue, setInputValue] = useState("");
    const [error, setError] = useState("");

    const existingPatterns = entries.map((e) => (e.pattern || "").trim().toLowerCase());

    const addPattern = (value) => {
        const p = normalizePattern(value);
        if (!p) {
            setInputValue("");
            setError("");
            return;
        }
        const validationError = validatePattern(p);
        if (validationError) {
            setError(validationError);
            return;
        }
        if (existingPatterns.includes(p)) {
            setError("This pattern is already in the block list");
            return;
        }
        setBlockedHosts([...entries, createEntry(p)]);
        setInputValue("");
        setError("");
    };

    const removePattern = (index) => {
        setBlockedHosts(entries.filter((_, i) => i !== index));
    };

    const handleInputChange = (value) => {
        setInputValue(value);
        if (error) {
            setError("");
        }
    };

    const options = hostSuggestions
        .filter((h) => !existingPatterns.includes(h.toLowerCase()))
        .filter((h) => h.toLowerCase().includes(inputValue.toLowerCase()))
        .slice(0, 8)
        .map((h) => ({ value: h, label: h }));

    return (
        <VerticalStack gap="5">
            <SectionCard
                title="Block host / path"
                description={
                    <>
                        Block traffic by host or path pattern. Use <Text as="span" fontWeight="semibold">*</Text> as
                        a wildcard. Examples: <Text as="span" fontWeight="semibold">chatgpt.com/*</Text>,{" "}
                        <Text as="span" fontWeight="semibold">*/v1/chat/completions</Text>,{" "}
                        <Text as="span" fontWeight="semibold">deepseek.com/api/v1/*</Text>
                    </>
                }
            >
                <Autocomplete
                    options={options}
                    selected={[]}
                    onSelect={(selected) => addPattern(selected[0])}
                    textField={
                        <Autocomplete.TextField
                            label="Host or path pattern"
                            value={inputValue}
                            onChange={handleInputChange}
                            placeholder="e.g. chatgpt.com/*  or  */v1/chat/completions"
                            autoComplete="off"
                            error={error || undefined}
                            connectedRight={
                                <Button onClick={() => addPattern(inputValue)} disabled={!inputValue.trim()}>
                                    Add
                                </Button>
                            }
                            onKeyPress={(e) => {
                                if (e.key === "Enter") {
                                    e.preventDefault();
                                    addPattern(inputValue);
                                }
                            }}
                        />
                    }
                />

                <VerticalStack gap="3">
                    <HorizontalStack gap="2" blockAlign="center">
                        <Text variant="headingSm" as="h3">Blocked patterns</Text>
                        {entries.length > 0 && <Badge status="critical">{`${entries.length}`}</Badge>}
                    </HorizontalStack>

                    {entries.length === 0 ? (
                        <Box padding="4" borderColor="border" borderWidth="1" borderRadius="3" background="bg-subdued">
                            <Text variant="bodySm" tone="subdued" alignment="center">
                                No patterns blocked yet. Add a host or path pattern above to start blocking traffic.
                            </Text>
                        </Box>
                    ) : (
                        <VerticalStack gap="2">
                            {entries.map((entry, index) => (
                                <Box
                                    key={index}
                                    paddingBlockStart="3"
                                    paddingBlockEnd="3"
                                    paddingInlineStart="4"
                                    paddingInlineEnd="3"
                                    borderColor="border"
                                    borderWidth="1"
                                    borderRadius="3"
                                    background="bg-surface"
                                >
                                    <HorizontalStack align="space-between" blockAlign="center">
                                        <Text variant="bodyMd" fontWeight="semibold" alignment="start">{entry.pattern}</Text>
                                        <Button
                                            plain
                                            icon={DeleteMajor}
                                            onClick={() => removePattern(index)}
                                            accessibilityLabel={`Delete ${entry.pattern}`}
                                        />
                                    </HorizontalStack>
                                </Box>
                            ))}
                        </VerticalStack>
                    )}
                </VerticalStack>
            </SectionCard>

            <SectionCard
                title="Block personal accounts"
                description="Prevent users with personal or consumer email accounts from accessing the AI agent. Enterprise accounts (company email domains) are allowed through."
            >
                <Checkbox
                    label="Enable personal account blocking"
                    checked={!!blockPersonalAccounts}
                    onChange={setBlockPersonalAccounts}
                />
            </SectionCard>
        </VerticalStack>
    );
};

export default BlockedHostsStep;
