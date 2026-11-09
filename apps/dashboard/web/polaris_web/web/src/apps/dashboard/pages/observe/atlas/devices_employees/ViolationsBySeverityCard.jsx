import { useMemo } from "react";
import { Box, Card, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import DonutChart from "../../../../components/shared/DonutChart";
import { SEVERITY_SEGMENTS, formatNumber } from "./peopleConstants";

const CHART_SIZE = 180;
const SEVERITY_ORDER = ["Critical", "High", "Medium", "Low"];

function ViolationsBySeverityCard({ counts, onSelect }) {
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

    return (
        <Card padding="4">
            <VerticalStack gap="2">
                <Text variant="headingMd" fontWeight="semibold">Violations by Severity</Text>
                <HorizontalStack align="center">
                    <Box width={`${CHART_SIZE}px`}>
                        <DonutChart data={data} title={formatNumber(total)} subtitle="Violations" size={CHART_SIZE} pieInnerSize="55%" titleColor={titleColor} onSegmentClick={onSelect} />
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
        </Card>
    );
}

export default ViolationsBySeverityCard;
