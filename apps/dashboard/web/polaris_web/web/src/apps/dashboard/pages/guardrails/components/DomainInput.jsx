import ChipInputWithAutocomplete from "../../../components/shared/ChipInputWithAutocomplete";

const SUGGESTED_DOMAINS = [
    "chatgpt.com",
    "grok.com",
    "claude.ai",
    "gemini.google.com",
    "copilot.microsoft.com",
    "perplexity.ai"
];

const DomainInput = ({ domains = [], setDomains }) => {
    const validateDomain = (domain) => {
        const domainPattern = /^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$/i;

        // Suggested domains always pass validation
        if (SUGGESTED_DOMAINS.includes(domain)) {
            return { isValid: true };
        }

        // Validate domain format
        if (domainPattern.test(domain)) {
            return { isValid: true };
        }

        return {
            isValid: false,
            errorMessage: "Please enter a valid domain (e.g., example.com)"
        };
    };

    return (
        <ChipInputWithAutocomplete
            items={domains}
            setItems={setDomains}
            suggestions={SUGGESTED_DOMAINS}
            validate={validateDomain}
            normalize={(value) => value.trim().toLowerCase()}
            label="Target Domains for Extensions (Optional)"
            placeholder="Type domain and press Enter"
            helperText="For browser extensions: Specify external AI service domains to monitor."
        />
    );
};

export default DomainInput;
