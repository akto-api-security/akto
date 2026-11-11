import { useEffect, useState } from "react";
import { Avatar, Box, Card, HorizontalGrid, Text, VerticalStack } from "@shopify/polaris";
import { OverviewCardHeader, OverviewListRow, OverviewShowMore } from "./OverviewCard";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";

function initials(name) {
    return name.split("@")[0].split(/[ ._-]/).filter(Boolean).map((part) => part[0]).join("").slice(0, 2).toUpperCase();
}

export default function TopEmployeesCard({ employees, pageSize, loading }) {
    const [visible, setVisible] = useState(pageSize);
    useEffect(() => setVisible(pageSize), [employees, pageSize]);

    if (loading) return <Card><VerticalStack gap="3"><OverviewCardHeader title="Employees with most violations" /><SpinnerCentered height="200px" /></VerticalStack></Card>;

    const shown = employees.slice(0, visible);
    return (
        <Card padding="0">
            <Box padding="4" paddingBlockEnd="2">
                <OverviewCardHeader title="Employees with most violations" />
            </Box>
            {shown.map((employee) => (
                <OverviewListRow key={employee.name}>
                    <HorizontalGrid columns="auto minmax(0, 1fr) auto" gap="3" alignItems="center">
                        <Avatar size="small" initials={initials(employee.name)} name={employee.name} />
                        <VerticalStack gap="0">
                            <Text variant="bodyMd" fontWeight="medium" truncate>{employee.name}</Text>
                            <Text variant="bodySm" color="subdued" truncate>{employee.detail}</Text>
                        </VerticalStack>
                        <VerticalStack gap="0" inlineAlign="end">
                            <Text variant="bodyMd" fontWeight="semibold">{employee.count}</Text>
                            {employee.critical && <Text variant="bodySm" color="critical">{employee.critical}</Text>}
                        </VerticalStack>
                    </HorizontalGrid>
                </OverviewListRow>
            ))}
            <OverviewShowMore total={employees.length} shown={shown.length} pageSize={pageSize} onChange={setVisible} />
        </Card>
    );
}
