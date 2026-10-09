import { Button, Select, Text, TextField, VerticalStack } from "@shopify/polaris"
import { DeleteMinor } from "@shopify/polaris-icons"
import { useEffect, useState } from "react"
import observeApi from "../../../observe/api"

export const MAX_WINDOW_DAYS = 30

// Same validation the server applies, so a bad rule is caught before saving.
export function invalidRuleMessage(rules, selectedOptions) {
    if (selectedOptions && selectedOptions.length > 1) {
        // The periodic sender skips webhooks with this option, so other options would never send.
        return "Sensitive data threshold alerts can't be combined with other options in one webhook"
    }
    if (!rules || rules.length === 0) {
        return "Add at least one data type for sensitive data threshold alerts"
    }
    const seen = new Set()
    for (const rule of rules) {
        const threshold = Number(rule.threshold)
        const windowDays = Number(rule.windowDays)
        if (!rule.subType || !(threshold > 0) || !(windowDays >= 1 && windowDays <= MAX_WINDOW_DAYS)) {
            return `Each sensitive data alert needs a data type, a threshold above 0 and a window of 1 to ${MAX_WINDOW_DAYS} days`
        }
        if (seen.has(rule.subType)) {
            return `Data type ${rule.subType} is added more than once`
        }
        seen.add(rule.subType)
    }
    return null
}

function SensitiveDataAlertRules({ rules, setRules }) {

    const [dataTypeNames, setDataTypeNames] = useState([])
    const [loadFailed, setLoadFailed] = useState(false)

    useEffect(() => {
        // Only custom sensitive data types are counted by the runtime.
        observeApi.fetchDataTypes().then((res) => {
            const customTypes = res?.dataTypes?.customDataTypes || []
            setDataTypeNames(customTypes
                .filter(type => type.active && (type.sensitiveAlways || (type.sensitivePosition || []).length > 0))
                .map(type => type.name))
        }).catch(() => setLoadFailed(true))
    }, [])

    const currentRules = rules || []

    // A saved type must stay selectable even if the list has not loaded, failed to load, or no
    // longer includes it (say, the type was deactivated); otherwise the dropdown would show the
    // placeholder and saving would silently drop the rule's type.
    const dataTypeOptions = [...new Set([...dataTypeNames, ...currentRules.map(rule => rule.subType).filter(Boolean)])]
        .sort()
        .map(name => ({ label: name, value: name }))

    function updateRule(index, field, value) {
        setRules(currentRules.map((rule, i) => i === index ? { ...rule, [field]: value } : rule))
    }

    function addRule() {
        setRules([...currentRules, { subType: "", threshold: 30, windowDays: 7 }])
    }

    function removeRule(index) {
        setRules(currentRules.filter((_, i) => i !== index))
    }

    return (
        <VerticalStack gap="3">
            <Text variant="bodyMd" fontWeight="semibold">
                Alert when one API collection has more than N events of a data type within the window
            </Text>
            {currentRules.map((rule, index) => (
                <div key={index} style={{ display: "grid", gridTemplateColumns: "minmax(0, 2fr) minmax(0, 1fr) minmax(0, 1fr) auto", gap: "12px", alignItems: "end" }}>
                    <Select
                        label="Data type"
                        labelHidden={index > 0}
                        placeholder="Select a data type"
                        options={dataTypeOptions}
                        value={rule.subType}
                        onChange={(value) => updateRule(index, "subType", value)}
                    />
                    <TextField
                        label="More than (events)"
                        labelHidden={index > 0}
                        type="number"
                        min={1}
                        autoComplete="off"
                        value={String(rule.threshold ?? "")}
                        onChange={(value) => updateRule(index, "threshold", value === "" ? "" : parseInt(value))}
                    />
                    <TextField
                        label="Window (days)"
                        labelHidden={index > 0}
                        type="number"
                        min={1}
                        max={MAX_WINDOW_DAYS}
                        autoComplete="off"
                        value={String(rule.windowDays ?? "")}
                        onChange={(value) => updateRule(index, "windowDays", value === "" ? "" : parseInt(value))}
                    />
                    <Button icon={DeleteMinor} accessibilityLabel="Remove data type" onClick={() => removeRule(index)} />
                </div>
            ))}
            <div>
                <Button onClick={addRule}>Add data type</Button>
            </div>
            {loadFailed ?
                <Text variant="bodySm" color="critical">Could not load data types. Saved rules are kept; reload the page to pick new ones.</Text>
                : null}
            <Text variant="bodySm" color="subdued">
                Custom sensitive data types only. Every API collection is checked hourly; an event is one request or response carrying the type.
                Each data type and collection pair alerts once when it crosses its threshold, and again only after its window has passed.
            </Text>
        </VerticalStack>
    )
}

export default SensitiveDataAlertRules
