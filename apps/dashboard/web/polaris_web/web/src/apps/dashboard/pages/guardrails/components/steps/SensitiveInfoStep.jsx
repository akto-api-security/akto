import {
    VerticalStack,
    Text,
    Box,
    DataTable,
    Select,
    Button,
    TextField,
    HorizontalStack
} from '@shopify/polaris';
import { DeleteMajor } from '@shopify/polaris-icons';
import DropdownSearch from "../../../../components/shared/DropdownSearch";

export const SensitiveInfoConfig = {
    number: 5,
    title: "Add sensitive information filters",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ piiTypes, regexPatterns }) => {
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

        return filters.length > 0 ? filters.join(", ") : null;
    }
};

const SensitiveInfoStep = ({
    piiTypes,
    setPiiTypes,
    regexPatterns,
    setRegexPatterns,
    newRegexPattern,
    setNewRegexPattern
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
        </VerticalStack>
    );
};

export default SensitiveInfoStep;
