import { useEffect, useState } from "react";
import { Box } from "@shopify/polaris";
import PeopleList from "@/apps/dashboard/components/shared/PeopleList";
import { OverviewShowMore } from "./OverviewCard";
import DiscoveryCard from "../../observe/atlas/discovery/shared/DiscoveryCard";
import { LOAD_STATE } from "../../observe/atlas/discovery/constants";

export default function TopEmployeesCard({ employees, pageSize, loading }) {
    const [visible, setVisible] = useState(pageSize);
    useEffect(() => setVisible(pageSize), [employees, pageSize]);

    const shown = employees.slice(0, visible);
    return (
        <DiscoveryCard flush title="Employees with most violations" status={loading ? LOAD_STATE.LOADING : LOAD_STATE.READY}>
            <Box paddingInlineStart="4" paddingInlineEnd="4" paddingBlockEnd="4">
                <PeopleList people={shown} emptyText="No employees with violations in this period." />
            </Box>
            <OverviewShowMore total={employees.length} shown={shown.length} pageSize={pageSize} onChange={setVisible} />
        </DiscoveryCard>
    );
}
