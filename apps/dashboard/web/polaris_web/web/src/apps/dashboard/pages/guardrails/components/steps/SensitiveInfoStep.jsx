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
    RangeSlider,
    Spinner
} from '@shopify/polaris';
import { DeleteMajor } from '@shopify/polaris-icons';
import { useState, useEffect } from 'react';
import DropdownSearch from "../../../../components/shared/DropdownSearch";
import observeApi from "../../../observe/api";

// Helper function to format label with domain count
const formatPiiLabel = (name, domainCount) => {
    // Show domain count with descriptive text in brackets
    // Grammar: 0 domain, 1 domain, 2+ domains
    const domainText = domainCount <= 1 ? 'domain' : 'domains';
    return `${name} (${domainCount} ${domainText})`;
};

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
    // State for dynamically fetched PII types
    const [availablePiiTypes, setAvailablePiiTypes] = useState([]);
    const [loadingPiiTypes, setLoadingPiiTypes] = useState(true);

    // Fetch enabled sensitive data types on mount
    useEffect(() => {
        const fetchSensitiveDataTypes = async () => {
            try {
                setLoadingPiiTypes(true);

                // Fetch data types and domain counts in parallel
                const [dataTypesResponse, countMapResponse] = await Promise.all([
                    observeApi.fetchDataTypes(),
                    observeApi.fetchCountMapOfApis()
                ]);

                const dataTypes = dataTypesResponse?.dataTypes || {};
                const aktoDataTypes = dataTypes.aktoDataTypes || [];
                const customDataTypes = dataTypes.customDataTypes || [];

                // Get domain counts - apiCollectionsMap maps data type name to collection IDs
                const apiCollectionsMap = countMapResponse?.apiCollectionsMap || {};

                // Combine and filter for enabled types only
                const allTypes = [...aktoDataTypes, ...customDataTypes];
                const enabledTypes = allTypes.filter(type => {
                    // AktoDataType uses 'inactive' (inverted logic), CustomDataType uses 'active'
                    if (type.inactive !== undefined) {
                        return !type.inactive;
                    }
                    return type.active !== false;
                });

                // Transform to dropdown format with domain counts
                const piiOptions = enabledTypes.map(type => {
                    // Get domain count - could be array, Set, or object
                    const domainsData = apiCollectionsMap[type.name];
                    let domainCount = 0;
                    if (Array.isArray(domainsData)) {
                        domainCount = domainsData.length;
                    } else if (domainsData && typeof domainsData === 'object') {
                        domainCount = Object.keys(domainsData).length;
                    }

                    return {
                        label: formatPiiLabel(type.name, domainCount),
                        value: type.name.toLowerCase().replace(/\s+/g, '_'),
                        originalLabel: type.name,
                        domainCount: domainCount
                    };
                });

                setAvailablePiiTypes(piiOptions);
            } catch (error) {
                console.error('Failed to fetch sensitive data types:', error);
                // Fallback to empty list on error
                setAvailablePiiTypes([]);
            } finally {
                setLoadingPiiTypes(false);
            }
        };

        fetchSensitiveDataTypes();
    }, []);

    const behaviorOptions = [
        { label: "Block", value: "block" },
        { label: "Mask", value: "mask" }
    ];

    return (
        <VerticalStack gap="4">
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
                                {loadingPiiTypes ? (
                                    <HorizontalStack gap="2" blockAlign="center">
                                        <Spinner size="small" />
                                        <Text variant="bodyMd" tone="subdued">Loading sensitive data types...</Text>
                                    </HorizontalStack>
                                ) : (
                                    <div style={{ maxWidth: '400px' }}>
                                        <DropdownSearch
                                            label=""
                                            placeholder={availablePiiTypes.length > 0 ? "Select PII types" : "No enabled sensitive data types found"}
                                            optionsList={availablePiiTypes}
                                            setSelected={(selectedValues) => {
                                                // Convert selected values to piiTypes format
                                                const newPiiTypes = (selectedValues || []).map(value => {
                                                    // Check if already exists to preserve behavior setting
                                                    const existing = piiTypes.find(p => p.type === value);
                                                    if (existing) {
                                                        return existing;
                                                    }
                                                    const option = availablePiiTypes.find(p => p.value === value);
                                                    return {
                                                        type: value,
                                                        behavior: 'block',
                                                        domainCount: option?.domainCount || 0
                                                    };
                                                });
                                                setPiiTypes(newPiiTypes);
                                            }}
                                            preSelected={piiTypes.map(p => p.type)}
                                            allowMultiple={true}
                                            itemName="PII type"
                                            searchDisable={false}
                                        />
                                    </div>
                                )}

                                {piiTypes.length > 0 && (
                                    <Box style={{ border: "1px solid #d1d5db", borderRadius: "8px", overflow: "hidden" }}>
                                        <DataTable
                                            columnContentTypes={['text', 'text', 'text']}
                                            headings={['PII type', 'Guardrail behavior', 'Actions']}
                                            verticalAlign="middle"
                                            rows={piiTypes.map((pii, index) => {
                                                const option = availablePiiTypes.find(p => p.value === pii.type);
                                                const displayName = option?.originalLabel || pii.type;
                                                const domainCount = pii.domainCount ?? option?.domainCount ?? 0;

                                                const domainText = domainCount <= 1 ? 'domain' : 'domains';
                                                return [
                                                    <HorizontalStack key={`name-${index}`} gap="1" blockAlign="center">
                                                        <Text as="span" variant="bodyMd">{displayName}</Text>
                                                        <Text as="span" variant="bodyMd" color="subdued">
                                                            ({domainCount} {domainText})
                                                        </Text>
                                                    </HorizontalStack>,
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
                                                ];
                                            })}
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
