import { useState } from "react";
import {
    VerticalStack,
    HorizontalStack,
    Box,
    Text,
    Button,
    Badge,
    Autocomplete,
    Icon,
    IndexTable,
    LegacyCard
} from "@shopify/polaris";
import { DeleteMajor } from "@shopify/polaris-icons";

// Entry kept as an object so it can be extended later (paths, includeSubdomains, ...)
// without breaking stored data. For now we only capture the host.
const createEntry = (host) => ({ host, paths: [], includeSubdomains: false });

// Accepts hostnames like "chatgpt.com", "claude.ai", "team.example.co.uk".
const DOMAIN_REGEX = /^(([a-z0-9]|[a-z0-9][a-z0-9-]*[a-z0-9])\.)+[a-z]{2,}$/;

// Strip scheme / path / port and lowercase so "https://ChatGPT.com/foo" -> "chatgpt.com".
const normalizeDomain = (raw) =>
    (raw || "")
        .trim()
        .toLowerCase()
        .replace(/^https?:\/\//, "")
        .split("/")[0]
        .split(":")[0];

export const BlockedHostsConfig = {
    number: 11,
    title: "Block host / path",

    validate: () => ({ isValid: true, errorMessage: null }),

    getSummary: ({ blockedHosts }) => {
        const rows = (blockedHosts || []).filter((r) => (r.host || "").trim());
        if (rows.length === 0) {
            return "";
        }
        const names = rows.map((r) => r.host.trim()).slice(0, 2).join(", ");
        const more = rows.length > 2 ? ` +${rows.length - 2}` : "";
        return `Blocking ${rows.length} host${rows.length === 1 ? "" : "s"}: ${names}${more}`;
    }
};

const BlockedHostsStep = ({ blockedHosts, setBlockedHosts, hostSuggestions = [] }) => {
    const entries = blockedHosts || [];
    const [inputValue, setInputValue] = useState("");
    const [error, setError] = useState("");

    const existingHosts = entries.map((e) => (e.host || "").trim().toLowerCase());

    const addHost = (host) => {
        const h = normalizeDomain(host);
        if (!h) {
            setInputValue("");
            setError("");
            return;
        }
        if (!DOMAIN_REGEX.test(h)) {
            setError("Enter a valid domain, e.g. chatgpt.com");
            return;
        }
        if (existingHosts.includes(h)) {
            setError("This host is already in the block list");
            return;
        }
        setBlockedHosts([...entries, createEntry(h)]);
        setInputValue("");
        setError("");
    };

    const handleInputChange = (value) => {
        setInputValue(value);
        if (error) {
            setError("");
        }
    };

    const removeHost = (index) => {
        setBlockedHosts(entries.filter((_, i) => i !== index));
    };

    const options = hostSuggestions
        .filter((h) => !existingHosts.includes(h.toLowerCase()))
        .filter((h) => h.toLowerCase().includes(inputValue.toLowerCase()))
        .slice(0, 8)
        .map((h) => ({ value: h, label: h }));

    const rowMarkup = entries.map((entry, index) => (
        <IndexTable.Row id={`${index}`} key={index} position={index}>
            <IndexTable.Cell>
                <Box paddingBlockStart="2" paddingBlockEnd="2">
                    <Text variant="bodyMd" fontWeight="semibold" alignment="start">{entry.host}</Text>
                </Box>
            </IndexTable.Cell>
            <IndexTable.Cell>
                <HorizontalStack align="end">
                    <Button
                        plain
                        icon={<Icon source={DeleteMajor} color="critical" />}
                        onClick={() => removeHost(index)}
                        accessibilityLabel={`Delete ${entry.host}`}
                    />
                </HorizontalStack>
            </IndexTable.Cell>
        </IndexTable.Row>
    ));

    return (
        <VerticalStack gap="5">
            <Text variant="bodyMd" tone="subdued">
                Block all traffic coming from specific hosts. Pick a host from your browser configs
                or type one, then add it to the block list.
            </Text>

            <Autocomplete
                options={options}
                selected={[]}
                onSelect={(selected) => addHost(selected[0])}
                textField={
                    <Autocomplete.TextField
                        label="Host"
                        value={inputValue}
                        onChange={handleInputChange}
                        placeholder="e.g. chatgpt.com"
                        autoComplete="off"
                        error={error || undefined}
                        connectedRight={
                            <Button onClick={() => addHost(inputValue)} disabled={!inputValue.trim()}>
                                Add
                            </Button>
                        }
                        onKeyPress={(e) => {
                            if (e.key === "Enter") {
                                e.preventDefault();
                                addHost(inputValue);
                            }
                        }}
                    />
                }
            />

            <VerticalStack gap="3">
                <HorizontalStack gap="2" blockAlign="center">
                    <Text variant="headingSm" as="h3">Blocked hosts</Text>
                    {entries.length > 0 && <Badge status="critical">{`${entries.length}`}</Badge>}
                </HorizontalStack>

                <LegacyCard>
                    <IndexTable
                        resourceName={{ singular: "host", plural: "hosts" }}
                        itemCount={entries.length}
                        selectable={false}
                        headings={[{ title: "Host" }, { title: "Actions" }]}
                        emptyState={
                            <Box padding="6">
                                <Text variant="bodySm" tone="subdued" alignment="center">
                                    No hosts blocked yet. Add a host above to start blocking traffic from it.
                                </Text>
                            </Box>
                        }
                    >
                        {rowMarkup}
                    </IndexTable>
                </LegacyCard>
            </VerticalStack>
        </VerticalStack>
    );
};

export default BlockedHostsStep;
