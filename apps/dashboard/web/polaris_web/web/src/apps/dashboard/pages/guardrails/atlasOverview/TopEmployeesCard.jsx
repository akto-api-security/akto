import { Avatar, Box, Card, HorizontalGrid, Text, VerticalStack } from "@shopify/polaris";
import { OverviewCardHeader, OverviewListRow } from "./OverviewCard";

function initials(name) {
    return name.split(/[ .]/).map((part) => part[0]).join("").slice(0, 2).toUpperCase();
}

export default function TopEmployeesCard({ employees }) {
    return (
        <Card padding="0">
            <Box padding="4" paddingBlockEnd="2">
                <OverviewCardHeader title="Employees with most violations" />
            </Box>
            {employees.map((employee) => (
                <OverviewListRow key={employee.name}>
                    <HorizontalGrid columns="auto minmax(0, 1fr) auto" gap="3" alignItems="center">
                        <Avatar size="small" initials={initials(employee.name)} name={employee.name} />
                        <VerticalStack gap="0">
                            <Text variant="bodyMd" fontWeight="medium">{employee.name}</Text>
                            <Text variant="bodySm" color="subdued">{`${employee.department} · ${employee.devices} ${employee.devices === 1 ? "device" : "devices"}`}</Text>
                        </VerticalStack>
                        <VerticalStack gap="0" inlineAlign="end">
                            <Text variant="bodyMd" fontWeight="semibold">{employee.count}</Text>
                            {employee.critical > 0 && <Text variant="bodySm" color="critical">{`${employee.critical} critical`}</Text>}
                        </VerticalStack>
                    </HorizontalGrid>
                </OverviewListRow>
            ))}
        </Card>
    );
}
