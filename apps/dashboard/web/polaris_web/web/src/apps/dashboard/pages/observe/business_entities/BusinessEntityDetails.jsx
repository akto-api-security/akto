import { useCallback, useEffect, useState } from "react";
import { Badge, Box, Button, ButtonGroup, Collapsible, DataTable, HorizontalStack, Icon, Scrollable, Text, VerticalStack } from "@shopify/polaris";
import { ChevronDownMinor, ChevronUpMinor, ClockMinor, DynamicSourceMinor, GlobeMinor, LinkMinor } from "@shopify/polaris-icons";
import Dropdown from "../../../components/layouts/Dropdown";
import FlyLayout from "../../../components/layouts/FlyLayout";
import LayoutWithTabs from "../../../components/layouts/LayoutWithTabs";
import GithubCell from "../../../components/tables/cells/GithubCell";
import SampleDataList from "../../../components/shared/SampleDataList";
import api from "../api";
import transform from "../transform";

const MAX_ALIAS_TAGS = 5;

export const OPERATION_ORDER = ["create", "read", "list", "update", "delete", "action"];
export const OPERATION_LABELS = { create: "Create", read: "Read", list: "List", update: "Update", delete: "Delete", action: "Action" };

const headingHeaders = [
    { value: "name", itemOrder: 1, text: "Entity" },
    { value: "operationsText", itemOrder: 3, icon: LinkMinor, iconTooltip: "APIs that operate on this entity" },
    { value: "hostsText", itemOrder: 3, icon: GlobeMinor, iconTooltip: "Hosts" },
    { value: "collectionsText", itemOrder: 3, icon: DynamicSourceMinor, iconTooltip: "API collections" },
    { value: "lastDiscovered", itemOrder: 3, icon: ClockMinor, iconTooltip: "Last discovered" },
    { value: "aliasTags", itemOrder: 4, text: "Also known as" }
];

const humanize = (value) => {
    const text = value.replace(/_/g, " ");
    return text.charAt(0).toUpperCase() + text.slice(1);
};

const plural = (count, word) => `${count} ${word}${count === 1 ? "" : "s"}`;

const countChipStyle = (active) => ({
    padding: "4px 8px", width: "fit-content", color: "#202223", background: active ? "#ECEBFF" : "#E4E5E7", borderRadius: "4px"
});

export const row = (name, detail) => [
    <Text fontWeight="regular" variant="bodyMd" key="name">{name}</Text>,
    <Text variant="bodySm" fontWeight="regular" color="subdued" key="detail">{detail}</Text>
];

// Same look as the Schema tab (ApiSingleSchema): collapsible grey header, segmented tabs with counts, dense table
export function SchemaStyleSection({ title, tabs }) {
    const [open, setOpen] = useState(true);
    const [activeIndex, setActiveIndex] = useState(0);
    const handleToggle = useCallback(() => setOpen((value) => !value), []);
    const active = tabs[activeIndex] || tabs[0];

    return (
        <VerticalStack gap="2">
            <Box background="bg-subdued" width="100%" padding="2" onClick={handleToggle}>
                <HorizontalStack align="space-between">
                    <Text variant="headingSm">{title}</Text>
                    <Box>
                        <Icon source={open ? ChevronDownMinor : ChevronUpMinor} />
                    </Box>
                </HorizontalStack>
            </Box>
            <Collapsible open={open} id={`${title}-collapsible`} transition={{ duration: "200ms", timingFunction: "ease-in-out" }} expandOnPrint>
                <VerticalStack gap="2">
                    {tabs.length > 1 && (
                        <ButtonGroup segmented>
                            {tabs.map((tab, index) => (
                                <Button key={tab.label} primarySuccess={index === activeIndex} onClick={() => setActiveIndex(index)} size="slim">
                                    <Box paddingBlockStart="05" paddingBlockEnd="05">
                                        <HorizontalStack gap="2">
                                            <Text variant="bodyMd">{tab.label}</Text>
                                            <span style={countChipStyle(index === activeIndex)}>{tab.rows.length}</span>
                                        </HorizontalStack>
                                    </Box>
                                </Button>
                            ))}
                        </ButtonGroup>
                    )}
                    <Scrollable style={{ height: "25vh" }} focusable>
                        <DataTable headings={[]} columnContentTypes={["text", "numeric"]} rows={active.rows} increasedTableDensity truncate />
                    </Scrollable>
                </VerticalStack>
            </Collapsible>
        </VerticalStack>
    );
}

async function loadSamples(operationApi) {
    const res = await api.fetchSampleData(operationApi.url, operationApi.apiCollectionId, operationApi.method);
    const samples = (res.sampleDataList || []).map((x) => x.samples).reverse().flat();
    return transform.prepareSampleData(transform.convertSampleDataToSensitiveSampleData(samples, []), "");
}

const countBy = (items, key) => items.reduce((counts, item) => {
    counts[key(item)] = (counts[key(item)] || 0) + 1;
    return counts;
}, {});

function BusinessEntityDetails({ entity, showDetails, setShowDetails }) {
    const [selectedApiIndex, setSelectedApiIndex] = useState(0);
    const [sampleData, setSampleData] = useState([]);
    const [loading, setLoading] = useState(false);

    const operations = [...(entity?.operations || [])].sort((a, b) =>
        OPERATION_ORDER.indexOf(a.operation) - OPERATION_ORDER.indexOf(b.operation) || a.api.url.localeCompare(b.api.url));
    const selected = operations[selectedApiIndex];

    useEffect(() => {
        setSelectedApiIndex(0);
    }, [entity?.id]);

    useEffect(() => {
        if (!selected) {
            setSampleData([]);
            return;
        }
        let cancelled = false;
        setLoading(true);
        loadSamples(selected.api)
            .then((messages) => { if (!cancelled) setSampleData(messages); })
            .catch(() => { if (!cancelled) setSampleData([]); })
            .finally(() => { if (!cancelled) setLoading(false); });
        return () => { cancelled = true; };
    }, [entity?.id, selectedApiIndex]);

    if (!entity) return null;

    const stateFields = entity.evidence?.statefulAttributes || [];
    const otherAttributes = (entity.attributes || []).filter((a) => !stateFields.includes(a));

    const operationHosts = countBy(operations, (o) => o.api.hostName);
    const headingData = {
        name: entity.name,
        operationsText: plural(operations.length, "operation"),
        hostsText: plural(Object.keys(operationHosts).length, "host"),
        collectionsText: plural(new Set(operations.map((o) => o.api.apiCollectionId)).size, "collection"),
        lastDiscovered: entity.lastDiscovered,
        aliasTags: (entity.aliases || []).slice(0, MAX_ALIAS_TAGS).map((a) => a.name)
    };

    const headingComp = (
        <VerticalStack gap="3" key="heading">
            <GithubCell width="32vw" data={headingData} headers={headingHeaders} getStatus={() => "info"} />
            {(entity.parentContexts || []).length > 0 && (
                <HorizontalStack gap="2" blockAlign="center">
                    <Text color="subdued">Found inside:</Text>
                    {entity.parentContexts.slice(0, 4).map((p) => <Badge key={p.name}>{humanize(p.name)}</Badge>)}
                </HorizontalStack>
            )}
        </VerticalStack>
    );

    const apiOptions = operations.map((o, index) => ({
        label: `${OPERATION_LABELS[o.operation]} · ${o.api.method} ${o.api.url} · ${o.api.hostName}`,
        value: String(index)
    }));

    const ValuesTab = {
        id: "values",
        content: "Values",
        component: (
            <Box paddingBlockStart="4">
                <VerticalStack gap="4">
                    {operations.length > 0 && (
                        <VerticalStack gap="2">
                            <Dropdown
                                key={`example-api-${entity.id}`}
                                id="business-entity-example-api"
                                label="Example API"
                                menuItems={apiOptions}
                                initial={String(selectedApiIndex)}
                                selected={(value) => setSelectedApiIndex(Number(value))}
                            />
                            {selected && (
                                <Text color="subdued">
                                    {`${OPERATION_LABELS[selected.operation]} operation on ${entity.name}`}
                                </Text>
                            )}
                        </VerticalStack>
                    )}
                    {loading ? (
                        <Text color="subdued">Loading sample values...</Text>
                    ) : sampleData.length > 0 ? (
                        <SampleDataList
                            key={`samples-${entity.id}-${selectedApiIndex}`}
                            sampleData={sampleData}
                            heading={"Sample values"}
                            minHeight={"35vh"}
                            vertical={true}
                            isAPISampleData={true}
                        />
                    ) : (
                        <Text color="subdued">No sample values recorded for this API.</Text>
                    )}
                </VerticalStack>
            </Box>
        )
    };

    const AttributesTab = {
        id: "attributes",
        content: "Attributes",
        component: (
            <Box paddingBlockStart="4">
                <VerticalStack gap="4">
                    <SchemaStyleSection
                        title="Fields"
                        tabs={[
                            { label: "State", rows: stateFields.map((f) => row(f, "")) },
                            { label: "Other", rows: otherAttributes.map((f) => row(f, "")) }
                        ]}
                    />
                    <SchemaStyleSection
                        title="Identifiers"
                        tabs={[
                            { label: "Parameter names", rows: (entity.aliases || []).map((a) => row(a.name, plural(a.apiCount, "API"))) },
                            { label: "Also referred to as", rows: (entity.roles || []).map((r) => row(humanize(r.name), plural(r.apiCount, "API"))) }
                        ]}
                    />
                    <SchemaStyleSection
                        title="Hosts"
                        tabs={[{ label: "Hosts", rows: Object.entries(operationHosts).sort((a, b) => b[1] - a[1]).map(([host, count]) => row(host, plural(count, "operation"))) }]}
                    />
                </VerticalStack>
            </Box>
        )
    };

    return (
        <FlyLayout
            title="Business entity details"
            show={showDetails}
            setShow={setShowDetails}
            components={[
                headingComp,
                <LayoutWithTabs key="tabs" tabs={[ValuesTab, AttributesTab]} currTab={() => {}} />
            ]}
        />
    );
}

export default BusinessEntityDetails;
