import React, { useCallback, useState } from "react";
import {
    Box,
    Button,
    ChoiceList,
    HorizontalStack,
    Popover,
    Tag,
    Text,
    TextField,
    Tooltip,
    VerticalStack,
} from "@shopify/polaris";
import { addAdvancedFilter, filterChipLabel, filterLabel } from "./attributeSearch";

const SIDE_OPTIONS = [
    { label: "Request or response", value: "any" },
    { label: "Request", value: "request" },
    { label: "Response", value: "response" },
];

const PART_OPTIONS = [
    { label: "Payload or headers", value: "any" },
    { label: "Payload", value: "payload" },
    { label: "Headers", value: "headers" },
];

export default function AdvancedPayloadSearch({ filters = [], onChange, showButton = true, showTags = true }) {
    const [open, setOpen] = useState(false);
    const [side, setSide] = useState("any");
    const [part, setPart] = useState("any");
    const [key, setKey] = useState("");
    const [value, setValue] = useState("");

    const addFilter = useCallback(() => {
        const next = addAdvancedFilter(filters, { key: key.trim(), value: value.trim(), exact: false, side, part });
        if (next !== filters) {
            onChange?.(next);
            setValue("");
            setOpen(false);
        }
    }, [filters, key, onChange, part, side, value]);

    const removeFilter = useCallback((index) => {
        onChange?.(filters.filter((_, i) => i !== index));
    }, [filters, onChange]);

    const activator = (
        <Button size="slim" disclosure={open ? "up" : "down"} onClick={() => setOpen((v) => !v)}>
            Advanced search
        </Button>
    );

    const popover = (
            <Popover
                active={open}
                activator={activator}
                onClose={() => setOpen(false)}
                preferredAlignment="right"
            >
                <Box padding="4" minWidth="280px">
                    <VerticalStack gap="3">
                        <Text variant="headingSm">Search headers or payload</Text>
                        <ChoiceList
                            title="Search in"
                            choices={SIDE_OPTIONS}
                            selected={[side]}
                            onChange={(selected) => setSide(selected[0])}
                        />
                        <ChoiceList
                            title="Section"
                            choices={PART_OPTIONS}
                            selected={[part]}
                            onChange={(selected) => setPart(selected[0])}
                        />
                        <TextField label="Field name" value={key} onChange={setKey} placeholder="host or email" autoComplete="off" />
                        <TextField
                            label="Value"
                            value={value}
                            onChange={setValue}
                            placeholder="us-east-1"
                            autoComplete="off"
                            helpText="Matches if the field contains this value. Spaces are kept."
                        />
                        <Button primary disabled={!key.trim() || !value.trim()} onClick={addFilter}>
                            Add filter
                        </Button>
                    </VerticalStack>
                </Box>
            </Popover>
    );

    if (!showButton && (!showTags || filters.length === 0)) return null;
    if (showButton && !showTags) return popover;

    return (
        <VerticalStack gap="2">
            {showButton ? popover : null}
            {showTags && filters.length > 0 && (
                <HorizontalStack gap="2" wrap>
                    {filters.map((clause, index) => {
                        const full = filterLabel(clause);
                        return (
                            <Box key={`${clause.side}:${clause.part}:${clause.key}:${clause.value}:${index}`} maxWidth="280px">
                                <Tooltip content={full} dismissOnMouseOut width="wide">
                                    <Tag onRemove={() => removeFilter(index)}>
                                        <Text as="span" truncate>{filterChipLabel(clause)}</Text>
                                    </Tag>
                                </Tooltip>
                            </Box>
                        );
                    })}
                </HorizontalStack>
            )}
        </VerticalStack>
    );
}
