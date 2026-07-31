import { useState, useEffect } from "react";
import { Avatar, Badge, Button, HorizontalStack, Text } from "@shopify/polaris";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import func from "@/util/func";
import api from "../../guardrails/api";

const resourceName = {
    singular: "config",
    plural: "configs",
};

const headings = [
    { text: "Host", value: "hostComp", title: "Host" },
    { text: "Status", value: "statusComp", title: "Status", filterKey: "status", showFilter: true },
];

const sortOptions = [
    { label: "Host", value: "host asc", directionLabel: "A-Z", sortKey: "host", columnIndex: 1 },
    { label: "Host", value: "host desc", directionLabel: "Z-A", sortKey: "host", columnIndex: 1 },
];

const PAGE_LIMIT = 50;

function BrowserExtensionSettings() {
    const [configs, setConfigs] = useState([]);
    const [rawConfigs, setRawConfigs] = useState([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        fetchConfigs();
    }, []);

    const fetchConfigs = async () => {
        setLoading(true);
        try {
            const response = await api.fetchBrowserExtensionConfigsCommon();
            if (response && response.browserExtensionConfigsCommon) {
                setRawConfigs(response.browserExtensionConfigsCommon);
                const formatted = response.browserExtensionConfigsCommon.map((config, index) => {
                    const isBeta = (config.tag || "").toLowerCase() === "beta";
                    const status = config.active ? "Active" : "Inactive";
                    return {
                        id: config.hexId || `${config.host}-${index}`,
                        host: config.host,
                        status,
                        hostComp: (
                            <HorizontalStack gap="2" blockAlign="center">
                                <Avatar size="extraSmall" shape="square" source={config.iconUrl || undefined} name={config.host} />
                                <Text variant="bodyMd">{config.host}</Text>
                                {isBeta && <Badge size="small" status="info">Beta</Badge>}
                            </HorizontalStack>
                        ),
                        statusComp: (
                            <Badge tone={config.active ? "success" : undefined} size="small">
                                {status}
                            </Badge>
                        ),
                    };
                });
                setConfigs(formatted);
            } else {
                setRawConfigs([]);
                setConfigs([]);
            }
        } catch (error) {
            func.setToast(true, true, "Failed to load browser extension configs");
        } finally {
            setLoading(false);
        }
    };

    const disambiguateLabel = (key, value) => {
        return func.convertToDisambiguateLabelObj(value, null, 2);
    };

    // Build flat rows (arrays joined so they don't break CSV columns) and reuse func.downloadAsCSV.
    const handleDownload = () => {
        if (rawConfigs.length === 0) {
            func.setToast(true, true, "No configs to download");
            return;
        }
        const rows = rawConfigs.map((config) => ({
            Host: config.host || "-",
            Transport: config.transport || "-",
            Method: config.method || "-",
            Operations: (config.operations || []).join(" & ") || "-",
            Paths: (config.paths || []).join(" & ") || "-",
            Format: config.format || "-",
            IconUrl: config.iconUrl || "-",
        }));
        func.downloadAsCSV(rows, { name: "browser_extension_configs" });
    };

    const configTable = (
        <GithubSimpleTable
            key="ext-common-configs"
            resourceName={resourceName}
            useNewRow={true}
            headers={headings}
            headings={headings}
            data={configs}
            pageLimit={PAGE_LIMIT}
            sortOptions={sortOptions}
            disambiguateLabel={disambiguateLabel}
            loading={loading}
            showFooter={false}
        />
    );

    return (
        <PageWithMultipleCards
            title={"Browser Extension"}
            subtitle={"Available in Extension v1.0.61 and later"}
            isFirstPage={true}
            primaryAction={<Button primary onClick={handleDownload} disabled={loading || rawConfigs.length === 0}>Download</Button>}
            components={[configTable]}
        />
    );
}

export default BrowserExtensionSettings;
