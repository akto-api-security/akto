import { Box, Text, VerticalStack } from "@shopify/polaris";
import { ClockMinor, DynamicSourceMinor, LinkMinor } from "@shopify/polaris-icons";
import FlyLayout from "../../../components/layouts/FlyLayout";
import GithubCell from "../../../components/tables/cells/GithubCell";
import FlowGraph, { humanize, nodeLabel } from "./FlowGraph";
import { SchemaStyleSection, row } from "./BusinessEntityDetails";

const headingHeaders = [
    { value: "name", itemOrder: 1, text: "Flow" },
    { value: "typeText", itemOrder: 3, icon: DynamicSourceMinor, iconTooltip: "Flow type" },
    { value: "stepsText", itemOrder: 3, icon: LinkMinor, iconTooltip: "Steps" },
    { value: "volumeText", itemOrder: 3, icon: ClockMinor, iconTooltip: "Times observed" },
    { value: "entityTags", itemOrder: 4, text: "Entities" }
];

const pct = (value) => `${Math.round((value || 0) * 100)}%`;

function BusinessFlowDetails({ flow, showDetails, setShowDetails }) {
    if (!flow) return null;
    const nodesById = Object.fromEntries((flow.nodes || []).map((node) => [node.id, node]));
    const label = (id) => (nodesById[id] ? nodeLabel(nodesById[id]) : id);
    const example = (id) => {
        const api = ((nodesById[id] || {}).exampleApis || [])[0];
        return api ? `${api.method} ${api.url}` : "";
    };
    const steps = (flow.edges || []).filter((edge) => edge.kind === "step");
    const loops = (flow.edges || []).filter((edge) => edge.kind === "loop");

    const headingData = {
        name: flow.name,
        typeText: flow.typeLabel,
        stepsText: `${steps.length} steps${loops.length ? `, ${loops.length} loops` : ""}`,
        volumeText: `${flow.volume.toLocaleString()} times`,
        entityTags: (flow.entityIds || []).map(humanize)
    };

    return (
        <FlyLayout
            title="Business flow details"
            show={showDetails}
            setShow={setShowDetails}
            components={[
                <GithubCell key="heading" width="32vw" data={headingData} headers={headingHeaders} getStatus={() => "info"} />,
                <VerticalStack key="graph" gap="2">
                    <FlowGraph flow={flow} />
                    <Text variant="bodySm" color="subdued">
                        ← comes-from: share of the next step's calls that came right after this step. leads-to →: share of this step's
                        calls followed by the next step. Dashed: back-and-forth loop, not a one-way step.
                    </Text>
                </VerticalStack>,
                <Box key="steps" paddingBlockStart="2">
                    <SchemaStyleSection
                        title="Steps"
                        tabs={[
                            { label: "Steps", rows: steps.map((e) => row(`${label(e.from)}  →  ${label(e.to)}`,
                                `← ${pct(e.comesFrom)} · ${pct(e.leadsTo)} → · ${e.count.toLocaleString()}×`)) },
                            { label: "Loops", rows: loops.map((e) => row(`${label(e.from)}  ↔  ${label(e.to)}`, `${e.count.toLocaleString()}×`)) },
                            { label: "Example APIs", rows: (flow.nodes || []).map((n) => row(nodeLabel(n), example(n.id))) }
                        ]}
                    />
                </Box>
            ]}
        />
    );
}

export default BusinessFlowDetails;
