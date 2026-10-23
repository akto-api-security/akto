import { useEffect, useState } from "react";
import { IndexFiltersMode, Text } from "@shopify/polaris";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import func from "@/util/func";
import api from "../api";
import BusinessEntityDetails, { OPERATION_LABELS, OPERATION_ORDER } from "./BusinessEntityDetails";
import BusinessFlowDetails from "./BusinessFlowDetails";
import { humanize, nodeLabel } from "./FlowGraph";

const SCOPE_KEY = "chargepoint";
const ALIASES_IN_TABLE = 3;

const resourceName = {
    singular: "business entity",
    plural: "business entities",
};

const headers = [
    { title: "Entity", text: "Entity", value: "name" },
    { title: "Operations", text: "Operations", value: "operationCount", sortActive: true },
    { title: "Operation types", text: "Operation types", value: "operationTypes" },
    { title: "Hosts", text: "Hosts", value: "hostCount" },
    { title: "Also known as", text: "Also known as", value: "alsoKnownAs" },
    { title: "Last discovered", text: "Last discovered", value: "lastDiscovered" },
];

const sortOptions = [
    { label: "Operations", value: "operationCount asc", directionLabel: "Most", sortKey: "operationCount", columnIndex: 2 },
    { label: "Operations", value: "operationCount desc", directionLabel: "Least", sortKey: "operationCount", columnIndex: 2 },
    { label: "Hosts", value: "hostCount asc", directionLabel: "Most", sortKey: "hostCount", columnIndex: 4 },
    { label: "Hosts", value: "hostCount desc", directionLabel: "Least", sortKey: "hostCount", columnIndex: 4 },
];

function toRow(entity, operations) {
    const lastDiscovered = entity.updatedAt ? func.prettifyEpoch(new Date(entity.updatedAt).getTime() / 1000) : "-";
    const types = OPERATION_ORDER.filter((op) => operations.some((o) => o.operation === op));
    return {
        ...entity,
        operations,
        lastDiscovered,
        operationCount: operations.length,
        operationTypes: types.map((op) => OPERATION_LABELS[op]).join(", "),
        hostCount: new Set(operations.map((o) => o.api.hostName)).size,
        alsoKnownAs: (entity.aliases || []).slice(0, ALIASES_IN_TABLE).map((a) => a.name).join(", "),
    };
}

const flowResourceName = {
    singular: "business flow",
    plural: "business flows",
};

const flowHeaders = [
    { title: "Flow", text: "Flow", value: "name" },
    { title: "Type", text: "Type", value: "typeLabel" },
    { title: "Entities", text: "Entities", value: "entitiesText" },
    { title: "Steps", text: "Steps", value: "stepCount" },
    { title: "Precedence", text: "Precedence", value: "precedenceText" },
    { title: "Times observed", text: "Times observed", value: "volume", sortActive: true },
];

const flowSortOptions = [
    { label: "Precedence", value: "precedenceValue asc", directionLabel: "Highest", sortKey: "precedenceValue", columnIndex: 5 },
    { label: "Precedence", value: "precedenceValue desc", directionLabel: "Lowest", sortKey: "precedenceValue", columnIndex: 5 },
    { label: "Times observed", value: "volume asc", directionLabel: "Most", sortKey: "volume", columnIndex: 6 },
    { label: "Times observed", value: "volume desc", directionLabel: "Least", sortKey: "volume", columnIndex: 6 },
];

const TABS = ["Entities", "Flows"];

function toFlowRow(flow) {
    const steps = (flow.edges || []).filter((e) => e.kind === "step");
    const isJourney = flow.type === "journey";
    // Journeys are named by their steps; lifecycles by their entity
    const name = isJourney ? (flow.nodes || []).map(nodeLabel).join("  →  ") : `${humanize(flow.entityIds[0])} lifecycle`;
    const precedence = isJourney ? flow.precedence
        : steps.length ? steps.reduce((sum, e) => sum + (e.comesFrom || 0), 0) / steps.length : 0;
    return {
        ...flow,
        name,
        typeLabel: isJourney ? "Journey" : "Lifecycle",
        entitiesText: (flow.entityIds || []).map(humanize).join(", "),
        stepCount: steps.length,
        precedenceValue: precedence || 0,
        precedenceText: `${Math.round((precedence || 0) * 100)}%`,
        volume: isJourney ? (flow.count || 0) : (flow.edges || []).reduce((sum, e) => sum + (e.count || 0), 0),
    };
}

function BusinessEntitiesPage() {
    const [entities, setEntities] = useState([]);
    const [loading, setLoading] = useState(true);
    const [selected, setSelected] = useState(null);
    const [showDetails, setShowDetails] = useState(false);
    const [flows, setFlows] = useState([]);
    const [selectedFlow, setSelectedFlow] = useState(null);
    const [showFlowDetails, setShowFlowDetails] = useState(false);
    const [selectedTab, setSelectedTab] = useState(0);

    useEffect(() => {
        api.getBusinessEntities(SCOPE_KEY)
            .then((resp) => {
                const operationsByEntity = {};
                ((resp && resp.entityOperations) || []).forEach((o) => {
                    (operationsByEntity[o.entityId] = operationsByEntity[o.entityId] || []).push(o);
                });
                setEntities(((resp && resp.businessEntities) || []).map((e) => toRow(e, operationsByEntity[e.slug] || [])));
            })
            .finally(() => setLoading(false));
        api.getBusinessFlows(SCOPE_KEY)
            .then((resp) => setFlows(((resp && resp.businessFlows) || []).map(toFlowRow)));
    }, []);

    const tableTabs = TABS.map((tab, index) => ({
        content: tab,
        badge: String(index === 0 ? entities.length : flows.length),
        onAction: () => setSelectedTab(index),
        id: tab.toLowerCase(),
        index,
    }));
    const showingFlows = selectedTab === 1;

    const table = (
        <GithubSimpleTable
            key={showingFlows ? `business-flows-${flows.length}` : `business-entities-${entities.length}`}
            pageLimit={50}
            data={showingFlows ? flows : entities}
            resourceName={showingFlows ? flowResourceName : resourceName}
            sortOptions={showingFlows ? flowSortOptions : sortOptions}
            filters={[]}
            headers={showingFlows ? flowHeaders : headers}
            headings={showingFlows ? flowHeaders : headers}
            tableTabs={tableTabs}
            selected={selectedTab}
            onSelect={setSelectedTab}
            selectable={false}
            useNewRow={true}
            condensedHeight={true}
            loading={loading}
            mode={IndexFiltersMode.Default}
            onRowClick={(row) => {
                if (showingFlows) {
                    setSelectedFlow(row);
                    setShowFlowDetails(true);
                } else {
                    setSelected(row);
                    setShowDetails(true);
                }
            }}
        />
    );

    return (
        <>
            <PageWithMultipleCards
                title={<Text variant="headingLg">Business entities</Text>}
                subtitle="Business objects such as stations, drivers and charging sessions, and the flows they go through, discovered from your API traffic"
                isFirstPage={true}
                components={[table]}
            />
            <BusinessEntityDetails entity={selected} showDetails={showDetails} setShowDetails={setShowDetails} />
            <BusinessFlowDetails flow={selectedFlow} showDetails={showFlowDetails} setShowDetails={setShowFlowDetails} />
        </>
    );
}

export default BusinessEntitiesPage;
