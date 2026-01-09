import { useState, useCallback, useRef, useEffect } from "react";
import { VerticalStack, HorizontalStack, Text, Tag, Box } from "@shopify/polaris";
import "./DomainInput.css";

const SUGGESTED_DOMAINS = [
    "chatgpt.com",
    "grok.com",
    "claude.ai",
    "gemini.google.com",
    "copilot.microsoft.com",
    "perplexity.ai"
];

const DomainInput = ({ domains = [], setDomains }) => {
    const [inputValue, setInputValue] = useState("");
    const [isFocused, setIsFocused] = useState(false);
    const [errorMessage, setErrorMessage] = useState("");
    const inputRef = useRef(null);
    const blurTimeoutRef = useRef(null);

    const filteredSuggestions = SUGGESTED_DOMAINS.filter(
        domain => !domains.includes(domain) &&
                 (!inputValue || domain.toLowerCase().includes(inputValue.toLowerCase()))
    );

    const isValidDomain = (domain) => {
        // Basic domain validation: must have at least one dot and valid characters
        const domainPattern = /^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$/i;
        return domainPattern.test(domain);
    };

    const addDomain = useCallback((domain) => {
        const trimmed = domain.trim().toLowerCase();
        if (!trimmed) return;

        if (domains.includes(trimmed)) {
            setErrorMessage("Domain already added");
            return;
        }

        // If it's from suggestions, add directly
        // If it's custom input, validate domain format
        if (SUGGESTED_DOMAINS.includes(trimmed) || isValidDomain(trimmed)) {
            setDomains([...domains, trimmed]);
            setInputValue("");
            setErrorMessage("");
        } else {
            setErrorMessage("Please enter a valid domain (e.g., example.com)");
        }
    }, [domains, setDomains]);

    const removeDomain = useCallback((domainToRemove) => {
        setDomains(domains.filter(d => d !== domainToRemove));
    }, [domains, setDomains]);

    const handleKeyDown = useCallback((e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            // If there are suggestions, always select the first one
            // If no suggestions but input has value, add what's typed
            if (filteredSuggestions.length > 0) {
                addDomain(filteredSuggestions[0]);
            } else if (inputValue.trim()) {
                addDomain(inputValue);
            }
        } else if (e.key === 'Backspace' && !inputValue && domains.length) {
            setDomains(domains.slice(0, -1));
        }
    }, [inputValue, domains, addDomain, setDomains, filteredSuggestions]);

    const handleFocus = useCallback(() => {
        if (blurTimeoutRef.current) {
            clearTimeout(blurTimeoutRef.current);
            blurTimeoutRef.current = null;
        }
        setIsFocused(true);
    }, []);

    const handleBlur = useCallback(() => {
        blurTimeoutRef.current = setTimeout(() => {
            setIsFocused(false);
        }, 200);
    }, []);

    return (
        <VerticalStack gap="1">
            <Text variant="bodyMd" as="label">Target Domains for Extensions (Optional)</Text>

            <div className="domain-input-wrapper">
                <Box
                    padding="2"
                    borderWidth="1"
                    borderColor={errorMessage ? "critical" : "border"}
                    borderRadius="1"
                    background="bg-surface"
                    minHeight="2.5rem"
                >
                    <Box onClick={() => inputRef.current?.focus()} style={{ cursor: 'text' }}>
                        <HorizontalStack gap="1" wrap={true} blockAlign="center">
                            {domains.map(domain => (
                                <Tag key={domain} onRemove={() => removeDomain(domain)}>{domain}</Tag>
                            ))}
                            <input
                                ref={inputRef}
                                className="domain-input-field"
                                type="text"
                                value={inputValue}
                                onChange={e => {
                                    setInputValue(e.target.value);
                                    setErrorMessage("");
                                }}
                                onKeyDown={handleKeyDown}
                                onFocus={handleFocus}
                                onBlur={handleBlur}
                                placeholder={domains.length ? "" : "Type domain and press Enter"}
                            />
                        </HorizontalStack>
                    </Box>
                </Box>

                {isFocused && filteredSuggestions.length > 0 && (
                    <div className="domain-suggestions-dropdown">
                        {filteredSuggestions.map((domain, index) => (
                            <div
                                key={domain}
                                className={`domain-suggestion-item ${index === 0 ? 'domain-suggestion-selected' : ''}`}
                                onMouseDown={(e) => {
                                    e.preventDefault();
                                    addDomain(domain);
                                    inputRef.current?.focus();
                                }}
                            >
                                {domain}
                            </div>
                        ))}
                    </div>
                )}
            </div>

            {errorMessage && (
                <Text variant="bodySm" tone="critical">
                    <span style={{ color: '#D72C0D' }}>{errorMessage}</span>
                </Text>
            )}

            <Text variant="bodySm" tone="subdued">
                For browser extensions: Specify external AI service domains to monitor.
            </Text>
        </VerticalStack>
    );
};

export default DomainInput;
