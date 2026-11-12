import { Avatar, Text, VerticalStack } from "@shopify/polaris";
import CardWithHeader from "@/apps/dashboard/pages/dashboard/new_components/CardWithHeader";
import ItemListCard from "@/apps/dashboard/components/shared/ItemListCard";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import { initialsOf } from "@/apps/dashboard/components/shared/PeopleList";
import func from "@/util/func";
import { formatCount, plural } from "./atlasOverviewUtils";

const TOP_EMPLOYEES = 5;

function buildRows(employees, onOpenActivity) {
    return (employees || []).map((e) => {
        const tags = (e.tags || []).map((t) => func.toSentenceCase(t));
        const team = tags.length > 0 ? `${tags[0]}${tags.length > 1 ? ` +${tags.length - 1}` : ""}` : "";
        return {
            id: e.name,
            media: <Avatar size="small" initials={initialsOf(e.name)} name={e.name} />,
            title: e.name,
            onClick: onOpenActivity,
            description: [team, plural(e.devices, "device", "devices")].filter(Boolean).join(" · "),
            tag: (
                <VerticalStack gap="0" inlineAlign="end">
                    <Text variant="bodyMd" fontWeight="semibold">{formatCount(e.count)}</Text>
                    {e.critical > 0 && <Text variant="bodySm" color="critical">{`${formatCount(e.critical)} critical`}</Text>}
                </VerticalStack>
            ),
        };
    });
}

export default function TopEmployeesCard({ data, onOpenActivity }) {
    return (
        <CardWithHeader title="Employees with most violations" hasData>
            {data === null ? <SpinnerCentered height="200px" />
                : <ItemListCard items={buildRows(data?.employees, onOpenActivity)} limit={TOP_EMPLOYEES} emptyText="No employees with violations in this period." />}
        </CardWithHeader>
    );
}
