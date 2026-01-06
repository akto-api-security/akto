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
    number: 5,
    title: "Add sensitive information filters",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ piiTypes, regexPatterns, enableAnonymize }) => {
        const filters = [];

        if (piiTypes?.length > 0) {
            const piiNames = piiTypes.map(pii => pii.type).slice(0, 2);
            const moreCount = piiTypes.length > 2 ? ` +${piiTypes.length - 2} more PII${piiTypes.length - 2 > 1 ? 's' : ''}` : '';
            filters.push(`${piiNames.join(", ")}${moreCount}`);
        }

        if (regexPatterns?.length > 0) {
            const patternCount = regexPatterns.length;
            filters.push(`${patternCount} regex pattern${patternCount > 1 ? 's' : ''}`);
        }

        if (enableAnonymize) {
            filters.push('Sensitive data anonymization');
        }

        return filters.length > 0 ? filters.join(", ") : null;
    }
};

const SensitiveInfoStep = ({
    piiTypes,
    setPiiTypes,
    regexPatterns,
    setRegexPatterns,
    newRegexPattern,
    setNewRegexPattern,
    enableAnonymize,
    setEnableAnonymize,
    anonymizeConfidenceScore,
    setAnonymizeConfidenceScore
}) => {
    // PII types available (from AddPiiTypeModal)
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
            <Text variant="headingMd">Add sensitive information filters</Text>
            <Text variant="bodyMd" tone="subdued">
                Use these filters to handle any data related to privacy.
            </Text>

            <Box padding="4" borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface">
                <VerticalStack gap="3">
                    <Text variant="headingSm">Personally Identifiable Information (PII) types</Text>
                    <Text variant="bodyMd" tone="subdued">
                        Specify the types of PII to be filtered and the desired guardrail behavior.
                    </Text>

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

            <Box padding="4" borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface">
                <VerticalStack gap="3">
                    <Text variant="headingSm">Regex patterns</Text>
                    <Text variant="bodyMd" tone="subdued">
                        Add up to 10 regex patterns to filter custom types of sensitive information for your specific use case.
                    </Text>

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

            <VerticalStack gap="4">
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
