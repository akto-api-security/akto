import { VerticalStack, Text, FormLayout, Box } from "@shopify/polaris";
import ScannerDetectionStep from "./ScannerDetectionStep";

export const AdvancedScannersConfig = {
    number: 9,
    title: "Advanced Scanners",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: (stateData) => {
        const scanners = [];
        const scannerKeys = [
            { key: 'enableAnonymize', label: 'Anonymize' },
            { key: 'enableBanCode', label: 'Ban Code' },
            { key: 'enableBanCompetitors', label: 'Ban Competitors' },
            { key: 'enableBanSubstrings', label: 'Ban Substrings' },
            { key: 'enableBanTopics', label: 'Ban Topics' },
            { key: 'enableIntentAnalysis', label: 'Intent Analysis' },
            { key: 'enableLanguage', label: 'Language' },
            { key: 'enableSecrets', label: 'Secrets' },
            { key: 'enableSentiment', label: 'Sentiment' },
            { key: 'enableTokenLimit', label: 'Token Limit' }
        ];

        scannerKeys.forEach(({ key, label }) => {
            if (stateData[key]) {
                scanners.push(label);
            }
        });

        return scanners.length > 0 ? scanners.join(', ') : null;
    }
};

const AdvancedScannersStep = ({
    // Anonymize
    enableAnonymize,
    setEnableAnonymize,
    anonymizeConfidenceScore,
    setAnonymizeConfidenceScore,
    // BanCode
    enableBanCode,
    setEnableBanCode,
    banCodeConfidenceScore,
    setBanCodeConfidenceScore,
    // BanCompetitors
    enableBanCompetitors,
    setEnableBanCompetitors,
    banCompetitorsConfidenceScore,
    setBanCompetitorsConfidenceScore,
    // BanSubstrings
    enableBanSubstrings,
    setEnableBanSubstrings,
    banSubstringsConfidenceScore,
    setBanSubstringsConfidenceScore,
    // BanTopics
    enableBanTopics,
    setEnableBanTopics,
    banTopicsConfidenceScore,
    setBanTopicsConfidenceScore,
    // IntentAnalysis
    enableIntentAnalysis,
    setEnableIntentAnalysis,
    intentAnalysisConfidenceScore,
    setIntentAnalysisConfidenceScore,
    // Language
    enableLanguage,
    setEnableLanguage,
    languageConfidenceScore,
    setLanguageConfidenceScore,
    // Secrets
    enableSecrets,
    setEnableSecrets,
    secretsConfidenceScore,
    setSecretsConfidenceScore,
    // Sentiment
    enableSentiment,
    setEnableSentiment,
    sentimentConfidenceScore,
    setSentimentConfidenceScore,
    // TokenLimit
    enableTokenLimit,
    setEnableTokenLimit,
    tokenLimitConfidenceScore,
    setTokenLimitConfidenceScore
}) => {
    return (
        <VerticalStack gap="4">
            <Text variant="headingMd">Advanced Scanners</Text>
            <Text variant="bodyMd" tone="subdued">
                Configure additional ML-based scanners to detect and block specific types of content in user inputs.
            </Text>

            <Box padding="4" borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface">
                <ScannerDetectionStep
                    title="Anonymize Detection"
                    description="Detect and prevent anonymization attempts in user inputs."
                    checkboxLabel="Enable anonymize detection"
                    enabled={enableAnonymize}
                    onEnabledChange={setEnableAnonymize}
                    confidenceScore={anonymizeConfidenceScore}
                    onConfidenceScoreChange={setAnonymizeConfidenceScore}
                    helpText="Set the confidence threshold (0-1). Higher values are more permissive, lower values are more strict in detecting anonymization attempts."
                />
            </Box>

            <Box padding="4" borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface">
                <ScannerDetectionStep
                    title="Ban Code Detection"
                    description="Detect and block programming code in user inputs. This helps prevent code injection attacks."
                    checkboxLabel="Enable ban code detection"
                    enabled={enableBanCode}
                    onEnabledChange={setEnableBanCode}
                    confidenceScore={banCodeConfidenceScore}
                    onConfidenceScoreChange={setBanCodeConfidenceScore}
                    helpText="Set the confidence threshold (0-1). Higher values are more permissive, lower values are more strict in detecting code."
                />
            </Box>

            <Box padding="4" borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface">
                <ScannerDetectionStep
                    title="Ban Competitors Detection"
                    description="Detect and block references to competitor products or services in user inputs."
                    checkboxLabel="Enable ban competitors detection"
                    enabled={enableBanCompetitors}
                    onEnabledChange={setEnableBanCompetitors}
                    confidenceScore={banCompetitorsConfidenceScore}
                    onConfidenceScoreChange={setBanCompetitorsConfidenceScore}
                    helpText="Set the confidence threshold (0-1). Higher values are more permissive, lower values are more strict in detecting competitor references."
                />
            </Box>

            <Box padding="4" borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface">
                <ScannerDetectionStep
                    title="Ban Substrings Detection"
                    description="Detect and block specific substrings or patterns in user inputs."
                    checkboxLabel="Enable ban substrings detection"
                    enabled={enableBanSubstrings}
                    onEnabledChange={setEnableBanSubstrings}
                    confidenceScore={banSubstringsConfidenceScore}
                    onConfidenceScoreChange={setBanSubstringsConfidenceScore}
                    helpText="Set the confidence threshold (0-1). Higher values are more permissive, lower values are more strict in detecting banned substrings."
                />
            </Box>

            <Box padding="4" borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface">
                <ScannerDetectionStep
                    title="Ban Topics Detection"
                    description="Detect and block specific topics or themes in user inputs."
                    checkboxLabel="Enable ban topics detection"
                    enabled={enableBanTopics}
                    onEnabledChange={setEnableBanTopics}
                    confidenceScore={banTopicsConfidenceScore}
                    onConfidenceScoreChange={setBanTopicsConfidenceScore}
                    helpText="Set the confidence threshold (0-1). Higher values are more permissive, lower values are more strict in detecting banned topics."
                />
            </Box>

            <Box padding="4" borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface">
                <ScannerDetectionStep
                    title="Intent Analysis Detection"
                    description="Analyze user intent to detect malicious or inappropriate behavior in inputs."
                    checkboxLabel="Enable intent analysis detection"
                    enabled={enableIntentAnalysis}
                    onEnabledChange={setEnableIntentAnalysis}
                    confidenceScore={intentAnalysisConfidenceScore}
                    onConfidenceScoreChange={setIntentAnalysisConfidenceScore}
                    helpText="Set the confidence threshold (0-1). Higher values are more permissive, lower values are more strict in detecting malicious intent."
                />
            </Box>

            <Box padding="4" borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface">
                <ScannerDetectionStep
                    title="Language Detection"
                    description="Detect the language of user inputs and block content in unauthorized languages."
                    checkboxLabel="Enable language detection"
                    enabled={enableLanguage}
                    onEnabledChange={setEnableLanguage}
                    confidenceScore={languageConfidenceScore}
                    onConfidenceScoreChange={setLanguageConfidenceScore}
                    helpText="Set the confidence threshold (0-1). Higher values are more permissive, lower values are more strict in language detection."
                />
            </Box>

            <Box padding="4" borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface">
                <ScannerDetectionStep
                    title="Secrets Detection"
                    description="Detect and block secrets, API keys, passwords, and other sensitive information in user inputs."
                    checkboxLabel="Enable secrets detection"
                    enabled={enableSecrets}
                    onEnabledChange={setEnableSecrets}
                    confidenceScore={secretsConfidenceScore}
                    onConfidenceScoreChange={setSecretsConfidenceScore}
                    helpText="Set the confidence threshold (0-1). Higher values are more permissive, lower values are more strict in detecting secrets."
                />
            </Box>

            <Box padding="4" borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface">
                <ScannerDetectionStep
                    title="Sentiment Detection"
                    description="Analyze sentiment in user inputs to detect negative, toxic, or inappropriate emotional content."
                    checkboxLabel="Enable sentiment detection"
                    enabled={enableSentiment}
                    onEnabledChange={setEnableSentiment}
                    confidenceScore={sentimentConfidenceScore}
                    onConfidenceScoreChange={setSentimentConfidenceScore}
                    helpText="Set the confidence threshold (0-1). Higher values are more permissive, lower values are more strict in detecting negative sentiment."
                />
            </Box>

            <Box padding="4" borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface">
                <ScannerDetectionStep
                    title="Token Limit Detection"
                    description="Detect when user inputs exceed token limits and block overly long inputs."
                    checkboxLabel="Enable token limit detection"
                    enabled={enableTokenLimit}
                    onEnabledChange={setEnableTokenLimit}
                    confidenceScore={tokenLimitConfidenceScore}
                    onConfidenceScoreChange={setTokenLimitConfidenceScore}
                    helpText="Set the confidence threshold (0-1). Higher values are more permissive, lower values are more strict in enforcing token limits."
                />
            </Box>
        </VerticalStack>
    );
};

export default AdvancedScannersStep;

