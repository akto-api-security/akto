import { useMemo } from "react";
import { Box, Card, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import TitleWithInfo from "../../../../components/shared/TitleWithInfo";
import transform from "./transform";
import DonutChart from "../../../../components/shared/DonutChart";
import { SEVERITY_SEGMENTS, formatNumber } from "./peopleConstants";

const CHART_SIZE = 180;
const SEVERITY_ORDER = ["Critical", "High", "Medium", "Low"];
const TOOLTIP = "Guardrail violations across managed devices, grouped by severity. Click a segment to filter the table by that severity.";

const heading = (
    <VerticalStack gap="1">
        <HorizontalStack>
            <TitleWithInfo titleText="Violations by Severity" tooltipContent={transform.tooltip(TOOLTIP)} textProps={{ variant: "headingMd", fontWeight: "semibold" }} />
        </HorizontalStack>
        <Text as="p" color="subdued">Guardrail violations across managed devices</Text>
    </VerticalStack>
);

function ViolationsBySeverityCard({ counts, status, onSelect }) {
    const { data, total, titleColor } = useMemo(() => {
        const segments = SEVERITY_SEGMENTS.map(({ key, name, color }) => ({ name, key, y: counts?.[key] || 0, color }));
        const chartData = {};
        segments.forEach(({ name, key, y, color }) => { chartData[name] = { text: y, color, filterValue: key }; });
        const worst = SEVERITY_ORDER.find((name) => chartData[name].text > 0);
        return {
            data: chartData,
            total: segments.reduce((sum, segment) => sum + segment.y, 0),
            titleColor: worst ? chartData[worst].color : undefined,
        };
    }, [counts]);

    if (status === "error") {
        return (
            <Card padding="5">
                <VerticalStack gap="5">
                    {heading}
                    <Text as="p" color="subdued">Violation counts could not be loaded.</Text>
                </VerticalStack>
            </Card>
        );
    }

    return (
        <Card padding="5">
            <VerticalStack gap="5">
                {heading}
                <VerticalStack gap="2">
                    <HorizontalStack align="center">
                        <Box width={`${CHART_SIZE}px`}>
                            <DonutChart data={data} title={formatNumber(total)} subtitle="Violations" size={CHART_SIZE} pieInnerSize="55%" titleColor={titleColor} titleWeight="500" onSegmentClick={onSelect} />
                        </Box>
                    </HorizontalStack>
                    <HorizontalStack gap="3" wrap align="center">
                        {Object.entries(data).map(([name, { text, color }]) => (
                            <HorizontalStack key={name} gap="1" blockAlign="center">
                                <Box className="agentic-dot" style={{ "--dot-color": color }} />
                                <Text variant="bodySm" color="subdued">{name} ({formatNumber(text)})</Text>
                            </HorizontalStack>
                        ))}
                    </HorizontalStack>
                </VerticalStack>
            </VerticalStack>
        </Card>
    );
}

export default ViolationsBySeverityCard;
