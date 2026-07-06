import { useState } from "react";
import {
    VerticalStack,
    HorizontalStack,
    Box,
    Text,
    Button,
    Badge,
    TextField,
    Checkbox,
} from "@shopify/polaris";
import { DeleteMajor } from "@shopify/polaris-icons";

export const MAX_IGNORE_PHRASES = 50;
export const MIN_IGNORE_PHRASE_LENGTH = 3;
export const MAX_IGNORE_PHRASE_LENGTH = 200;

const createEntry = (phrase, isRegex, caseSensitive) => ({ phrase, isRegex, caseSensitive });

// Validate a single phrase entry. Returns an error string, or "" when valid.
const validatePhrase = (phrase, isRegex) => {
    const trimmed = (phrase || "").trim();
    if (!trimmed) {
        return "Enter a phrase to ignore";
    }
    if (trimmed.length < MIN_IGNORE_PHRASE_LENGTH) {
        return `Phrase must be at least ${MIN_IGNORE_PHRASE_LENGTH} characters — very short phrases would silently gut detection`;
    }
    if (trimmed.length > MAX_IGNORE_PHRASE_LENGTH) {
        return `Phrase must be at most ${MAX_IGNORE_PHRASE_LENGTH} characters`;
    }
    if (isRegex) {
        try {
            // eslint-disable-next-line no-new
            new RegExp(trimmed);
        } catch (e) {
            return "Invalid regex pattern";
        }
    }
    return "";
};

export const ExceptionsConfig = {
    number: 13,
    title: "Exceptions",

    validate: () => ({ isValid: true, errorMessage: null }),

    getSummary: ({ ignorePhrases }) => {
        const rows = (ignorePhrases || []).filter((r) => (r.phrase || "").trim());
        if (rows.length === 0) {
            return null;
        }
        const names = rows.map((r) => r.phrase.trim()).slice(0, 2).join(", ");
        const more = rows.length > 2 ? ` +${rows.length - 2}` : "";
        return `${rows.length} phrase${rows.length === 1 ? "" : "s"}: ${names}${more}`;
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

const ExceptionsStep = ({ ignorePhrases, setIgnorePhrases }) => {
    const entries = ignorePhrases || [];
    const [inputValue, setInputValue] = useState("");
    const [isRegex, setIsRegex] = useState(false);
    const [caseSensitive, setCaseSensitive] = useState(false);
    const [error, setError] = useState("");

    const addPhrase = () => {
        const trimmed = inputValue.trim();
        const validationError = validatePhrase(trimmed, isRegex);
        if (validationError) {
            setError(validationError);
            return;
        }
        if (entries.length >= MAX_IGNORE_PHRASES) {
            setError(`You can add up to ${MAX_IGNORE_PHRASES} ignore phrases per policy`);
            return;
        }
        if (entries.some((e) => (e.phrase || "").trim() === trimmed)) {
            setError("This phrase is already in the exception list");
            return;
        }
        setIgnorePhrases([...entries, createEntry(trimmed, isRegex, caseSensitive)]);
        setInputValue("");
        setIsRegex(false);
        setCaseSensitive(false);
        setError("");
    };

    const removePhrase = (index) => {
        setIgnorePhrases(entries.filter((_, i) => i !== index));
    };

    const handleInputChange = (value) => {
        setInputValue(value);
        if (error) {
            setError("");
        }
    };

    return (
        <VerticalStack gap="5">
            <SectionCard
                title="Ignore phrases"
                description="Phrases known to be safe (your product name, sample test data) won't be flagged by this policy's detectors — matched as a whole word by default."
            >
                <VerticalStack gap="3">
                    <TextField
                        label="Phrase"
                        labelHidden
                        value={inputValue}
                        onChange={handleInputChange}
                        placeholder="e.g. Acme Corp"
                        autoComplete="off"
                        error={error || undefined}
                        onKeyPress={(e) => {
                            if (e.key === "Enter") {
                                e.preventDefault();
                                addPhrase();
                            }
                        }}
                    />
                    <HorizontalStack gap="4" blockAlign="center">
                        <Checkbox
                            label="Regex"
                            checked={isRegex}
                            onChange={setIsRegex}
                        />
                        <Checkbox
                            label="Case sensitive"
                            checked={caseSensitive}
                            onChange={setCaseSensitive}
                        />
                        <Button onClick={addPhrase} disabled={!inputValue.trim()}>
                            Add phrase
                        </Button>
                    </HorizontalStack>
                    <Text variant="bodySm" tone="subdued">
                        This only affects this policy's own detectors — other guardrail policies evaluated on the same traffic still see the real text.
                    </Text>

                    <HorizontalStack gap="2" blockAlign="center">
                        <Text variant="headingSm" as="h3">Ignored phrases</Text>
                        {entries.length > 0 && <Badge>{`${entries.length}`}</Badge>}
                    </HorizontalStack>

                    {entries.length === 0 ? (
                        <Box padding="4" borderColor="border" borderWidth="1" borderRadius="3" background="bg-subdued">
                            <Text variant="bodySm" tone="subdued" alignment="center">
                                No exceptions added yet. Add a phrase above to exclude it from
                                evaluation.
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
                                        <HorizontalStack gap="2" blockAlign="center">
                                            <Text variant="bodyMd" fontWeight="semibold">{entry.phrase}</Text>
                                            {entry.isRegex && <Badge status="info">Regex</Badge>}
                                            {entry.caseSensitive && <Badge>Case sensitive</Badge>}
                                        </HorizontalStack>
                                        <Button
                                            plain
                                            icon={DeleteMajor}
                                            onClick={() => removePhrase(index)}
                                            accessibilityLabel={`Delete ${entry.phrase}`}
                                        />
                                    </HorizontalStack>
                                </Box>
                            ))}
                        </VerticalStack>
                    )}
                </VerticalStack>
            </SectionCard>
        </VerticalStack>
    );
};

export default ExceptionsStep;
