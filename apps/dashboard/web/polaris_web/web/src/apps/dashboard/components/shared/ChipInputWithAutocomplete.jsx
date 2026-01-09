import { useState, useCallback, useRef } from "react";
import { VerticalStack, HorizontalStack, Text, Tag, Box } from "@shopify/polaris";
import "./ChipInputWithAutocomplete.css";

const ChipInputWithAutocomplete = ({
    items = [],
    setItems,
    suggestions = [],
    validate,
    normalize,
    filterSuggestions,
    label,
    placeholder = "Type and press Enter",
    helperText,
    errorColor = "#D72C0D",
    errorBorderColor = "critical",
    containerClassName,
    inputClassName,
    tagColor,
    suggestionClassName,
    onItemAdd,
    onItemRemove,
    onInputChange
}) => {
    const [inputValue, setInputValue] = useState("");
    const [isFocused, setIsFocused] = useState(false);
    const [errorMessage, setErrorMessage] = useState("");
    const inputRef = useRef(null);
    const blurTimeoutRef = useRef(null);

    // Default filter: case-insensitive substring match, excludes already-selected items
    const defaultFilter = (suggestions, inputValue, items) => {
        return suggestions.filter(
            suggestion => !items.includes(normalize ? normalize(suggestion) : suggestion) &&
                         (!inputValue || suggestion.toLowerCase().includes(inputValue.toLowerCase()))
        );
    };

    const filteredSuggestions = filterSuggestions
        ? filterSuggestions(suggestions, inputValue, items)
        : defaultFilter(suggestions, inputValue, items);

    const addItem = useCallback((item) => {
        if (!item) return;

        // Normalize the item if normalize function is provided
        const normalizedItem = normalize ? normalize(item) : item.trim();

        // Check for duplicates (case-insensitive after normalization)
        if (items.includes(normalizedItem)) {
            setErrorMessage("Item already added");
            return;
        }

        // Validate if validate function is provided
        if (validate) {
            const validationResult = validate(normalizedItem);
            if (!validationResult.isValid) {
                setErrorMessage(validationResult.errorMessage || "Invalid input");
                return;
            }
        }

        // Add the item
        setItems([...items, normalizedItem]);
        setInputValue("");
        setErrorMessage("");

        // Call optional callback
        if (onItemAdd) {
            onItemAdd(normalizedItem);
        }
    }, [items, setItems, validate, normalize, onItemAdd]);

    const removeItem = useCallback((itemToRemove) => {
        setItems(items.filter(item => item !== itemToRemove));

        // Call optional callback
        if (onItemRemove) {
            onItemRemove(itemToRemove);
        }
    }, [items, setItems, onItemRemove]);

    const handleKeyDown = useCallback((e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            // If there are suggestions, always select the first one
            // If no suggestions but input has value, add what's typed
            if (filteredSuggestions.length > 0) {
                addItem(filteredSuggestions[0]);
            } else if (inputValue.trim()) {
                addItem(inputValue);
            }
        } else if (e.key === 'Backspace' && !inputValue && items.length) {
            const lastItem = items[items.length - 1];
            setItems(items.slice(0, -1));
            if (onItemRemove) {
                onItemRemove(lastItem);
            }
        }
    }, [inputValue, items, addItem, setItems, filteredSuggestions, onItemRemove]);

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

    const handleInputChange = useCallback((e) => {
        const value = e.target.value;
        setInputValue(value);
        setErrorMessage("");

        // Call optional callback
        if (onInputChange) {
            onInputChange(value);
        }
    }, [onInputChange]);

    return (
        <VerticalStack gap="1">
            <Text variant="bodyMd" as="label">{label}</Text>

            <div className={`chip-input-wrapper ${containerClassName || ''}`}>
                <Box
                    padding="2"
                    borderWidth="1"
                    borderColor={errorMessage ? errorBorderColor : "border"}
                    borderRadius="1"
                    background="bg-surface"
                    minHeight="2.5rem"
                >
                    <Box onClick={() => inputRef.current?.focus()} style={{ cursor: 'text' }}>
                        <HorizontalStack gap="1" wrap={true} blockAlign="center">
                            {items.map(item => (
                                <Tag
                                    key={item}
                                    onRemove={() => removeItem(item)}
                                    tone={tagColor}
                                >
                                    {item}
                                </Tag>
                            ))}
                            <input
                                ref={inputRef}
                                className={`chip-input-field ${inputClassName || ''}`}
                                type="text"
                                value={inputValue}
                                onChange={handleInputChange}
                                onKeyDown={handleKeyDown}
                                onFocus={handleFocus}
                                onBlur={handleBlur}
                                placeholder={items.length ? "" : placeholder}
                            />
                        </HorizontalStack>
                    </Box>
                </Box>

                {isFocused && filteredSuggestions.length > 0 && (
                    <div className={`chip-suggestions-dropdown ${suggestionClassName || ''}`}>
                        {filteredSuggestions.map((suggestion, index) => (
                            <div
                                key={suggestion}
                                className={`chip-suggestion-item ${index === 0 ? 'chip-suggestion-selected' : ''}`}
                                onMouseDown={(e) => {
                                    e.preventDefault();
                                    addItem(suggestion);
                                    inputRef.current?.focus();
                                }}
                            >
                                {suggestion}
                            </div>
                        ))}
                    </div>
                )}
            </div>

            {errorMessage && (
                <Text variant="bodySm" tone="critical">
                    <span style={{ color: errorColor }}>
                        {errorMessage}
                    </span>
                </Text>
            )}

            {helperText && (
                <Text variant="bodySm" tone="subdued">
                    {helperText}
                </Text>
            )}
        </VerticalStack>
    );
};

export default ChipInputWithAutocomplete;
