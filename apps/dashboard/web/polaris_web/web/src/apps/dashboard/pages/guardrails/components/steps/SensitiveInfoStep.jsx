import {
    VerticalStack,
    Text,
    Box,
    DataTable,
    Select,
    Button,
    TextField,
    HorizontalStack,
    Checkbox,
    RangeSlider
} from '@shopify/polaris';
import { DeleteMajor } from '@shopify/polaris-icons';
import DropdownSearch from "../../../../components/shared/DropdownSearch";

export const SensitiveInfoConfig = {
    number: 4,
    title: "Sensitive Information Guardrails",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ enablePiiTypes, piiTypes, enableRegexPatterns, regexPatterns, enableSecrets, enableAnonymize }) => {
        const filters = [];

        if (enablePiiTypes && piiTypes?.length > 0) {
            const piiNames = piiTypes.map(pii => pii.type).slice(0, 2);
            const moreCount = piiTypes.length > 2 ? ` +${piiTypes.length - 2} more PII${piiTypes.length - 2 > 1 ? 's' : ''}` : '';
            filters.push(`${piiNames.join(", ")}${moreCount}`);
        }

        if (enableRegexPatterns && regexPatterns?.length > 0) {
            const patternCount = regexPatterns.length;
            filters.push(`${patternCount} regex pattern${patternCount > 1 ? 's' : ''}`);
        }

        if (enableSecrets) {
            filters.push('Secrets detection');
        }

        if (enableAnonymize) {
            filters.push('Sensitive data anonymization');
        }

        return filters.length > 0 ? filters.join(", ") : null;
    }
};

const SensitiveInfoStep = ({
    // PII types
    enablePiiTypes,
    setEnablePiiTypes,
    piiTypes,
    setPiiTypes,
    // Regex patterns
    enableRegexPatterns,
    setEnableRegexPatterns,
    regexPatterns,
    setRegexPatterns,
    newRegexPattern,
    setNewRegexPattern,
    // Secrets detection
    enableSecrets,
    setEnableSecrets,
    secretsConfidenceScore,
    setSecretsConfidenceScore,
    // Anonymize
    enableAnonymize,
    setEnableAnonymize,
    anonymizeConfidenceScore,
    setAnonymizeConfidenceScore
}) => {
    // PII types available
    const availablePiiTypes = [
        { label: "Email", value: "email" },
        { label: "Name", value: "name" },
        { label: "Phone", value: "phone" },
        { label: "Address", value: "address" },
        { label: "Age", value: "age" },
        { label: "Username", value: "username" },
        { label: "Password", value: "password" },
        { label: "Driver ID", value: "driver_id" },
        { label: "License plate", value: "license_plate" },
        { label: "Vehicle Identification number (VIN)", value: "vin" }
    ];

    const behaviorOptions = [
        { label: "Block", value: "block" },
        { label: "Mask", value: "mask" }
    ];

    return (
        <VerticalStack gap="4">
            <Text variant="headingMd">Sensitive Information Guardrails</Text>
            <Text variant="bodyMd" tone="subdued">
                Use these filters to handle any data related to privacy.
            </Text>

            <VerticalStack gap="4">
                {/* PII Types */}
                <Box>
                    <Checkbox
                        label="Personally Identifiable Information (PII) types"
                        checked={enablePiiTypes}
                        onChange={setEnablePiiTypes}
                        helpText="Specify the types of PII to be filtered and the desired guardrail behavior."
                    />
                    {enablePiiTypes && (
                        <Box paddingBlockStart="4" style={{ paddingLeft: '28px' }}>
                            <VerticalStack gap="3">
                                <DropdownSearch
                                    label=""
                                    placeholder="Select PII type to add"
                                    optionsList={availablePiiTypes.filter(piiType => !piiTypes.some(p => p.type === piiType.value))}
                                    setSelected={(value) => {
                                        if (value) {
                                            setPiiTypes([...piiTypes, { type: value, behavior: 'block' }]);
                                        }
                                    }}
                                    value=""
                                    searchDisable={false}
                                />

                                {piiTypes.length > 0 && (
                                    <Box style={{ border: "1px solid #d1d5db", borderRadius: "8px", overflow: "hidden" }}>
                                        <DataTable
                                            columnContentTypes={['text', 'text', 'text']}
                                            headings={['PII type', 'Guardrail behavior', 'Actions']}
                                            rows={piiTypes.map((pii, index) => [
                                                availablePiiTypes.find(p => p.value === pii.type)?.label || pii.type,
                                                <Select
                                                    key={`behavior-${index}`}
                                                    value={pii.behavior}
                                                    options={behaviorOptions}
                                                    onChange={(value) => {
                                                        const updatedPiiTypes = [...piiTypes];
                                                        updatedPiiTypes[index].behavior = value;
                                                        setPiiTypes(updatedPiiTypes);
                                                    }}
                                                />,
                                                <Button
                                                    key={`delete-${index}`}
                                                    icon={DeleteMajor}
                                                    variant="plain"
                                                    onClick={() => {
                                                        const updatedPiiTypes = piiTypes.filter((_, i) => i !== index);
                                                        setPiiTypes(updatedPiiTypes);
                                                    }}
                                                />
                                            ])}
                                        />
                                    </Box>
                                )}
                            </VerticalStack>
                        </Box>
                    )}
                </Box>

                {/* Regex Patterns */}
                <Box>
                    <Checkbox
                        label="Regex patterns"
                        checked={enableRegexPatterns}
                        onChange={setEnableRegexPatterns}
                        helpText="Add up to 10 regex patterns to filter custom types of sensitive information for your specific use case."
                    />
                    {enableRegexPatterns && (
                        <Box paddingBlockStart="4" style={{ paddingLeft: '28px' }}>
                            <VerticalStack gap="3">
                                <HorizontalStack gap="2">
                                    <Box style={{ flexGrow: 1 }}>
                                        <TextField
                                            label=""
                                            value={newRegexPattern}
                                            onChange={setNewRegexPattern}
                                            placeholder="Enter regex pattern (e.g., \d{3}-\d{2}-\d{4})"
                                        />
                                    </Box>
                                    <Button
                                        onClick={() => {
                                            if (newRegexPattern.trim()) {
                                                setRegexPatterns([...regexPatterns, { pattern: newRegexPattern.trim(), behavior: 'block' }]);
                                                setNewRegexPattern("");
                                            }
                                        }}
                                        disabled={!newRegexPattern.trim()}
                                    >
                                        Add pattern
                                    </Button>
                                </HorizontalStack>

                                {regexPatterns.length > 0 && (
                                    <Box style={{ border: "1px solid #d1d5db", borderRadius: "8px", overflow: "hidden" }}>
                                        <DataTable
                                            columnContentTypes={['text', 'text', 'text']}
                                            headings={['Regex pattern', 'Guardrail behavior', 'Actions']}
                                            rows={regexPatterns.map((regex, index) => [
                                                regex.pattern || 'Invalid pattern',
                                                <Select
                                                    key={`regex-behavior-${index}`}
                                                    value={regex.behavior}
                                                    options={behaviorOptions}
                                                    onChange={(value) => {
                                                        const updatedRegexPatterns = [...regexPatterns];
                                                        updatedRegexPatterns[index].behavior = value;
                                                        setRegexPatterns(updatedRegexPatterns);
                                                    }}
                                                />,
                                                <Button
                                                    key={`regex-delete-${index}`}
                                                    icon={DeleteMajor}
                                                    variant="plain"
                                                    onClick={() => {
                                                        const updatedRegexPatterns = regexPatterns.filter((_, i) => i !== index);
                                                        setRegexPatterns(updatedRegexPatterns);
                                                    }}
                                                />
                                            ])}
                                        />
                                    </Box>
                                )}
                            </VerticalStack>
                        </Box>
                    )}
                </Box>

                {/* Secrets Detection */}
                <Box>
                    <Checkbox
                        label="Enable secrets detection"
                        checked={enableSecrets}
                        onChange={setEnableSecrets}
                        helpText="Detect and block secrets, API keys, passwords, and other sensitive information in user inputs."
                    />
                    {enableSecrets && (
                        <Box paddingBlockStart="4" style={{ paddingLeft: '28px' }}>
                            <VerticalStack gap="3">
                                <Text variant="bodyMd" fontWeight="medium">Confidence Threshold</Text>
                                <RangeSlider
                                    label=""
                                    value={secretsConfidenceScore}
                                    min={0}
                                    max={1}
                                    step={0.1}
                                    output
                                    onChange={setSecretsConfidenceScore}
                                    helpText="Set the confidence threshold (0-1). Higher values are more permissive, lower values are more strict in detecting secrets."
                                />
                            </VerticalStack>
                        </Box>
                    )}
                </Box>

                {/* Sensitive Data Anonymization */}
                <Box>
                    <Checkbox
                        label="Enable sensitive data anonymization"
                        checked={enableAnonymize}
                        onChange={setEnableAnonymize}
                        helpText="Detect and automatically anonymize sensitive data (emails, credit cards, phone numbers, SSN, etc.) in user inputs by replacing them with placeholders like [REDACTED_EMAIL_1]. Original values are stored securely for later restoration if needed."
                    />
                    {enableAnonymize && (
                        <Box paddingBlockStart="4" style={{ paddingLeft: '28px' }}>
                            <VerticalStack gap="3">
                                <Text variant="bodyMd" fontWeight="medium">Confidence Threshold</Text>
                                <RangeSlider
                                    label=""
                                    value={anonymizeConfidenceScore}
                                    min={0}
                                    max={1}
                                    step={0.1}
                                    output
                                    onChange={setAnonymizeConfidenceScore}
                                    helpText="Set the confidence threshold (0-1). Higher values are more permissive, lower values are more strict in detecting sensitive data that should be anonymized."
                                />
                            </VerticalStack>
                        </Box>
                    )}
                </Box>
            </VerticalStack>
        </VerticalStack>
    );
};

export default SensitiveInfoStep;
