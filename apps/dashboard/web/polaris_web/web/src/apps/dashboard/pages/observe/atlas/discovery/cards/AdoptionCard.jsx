import { HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import { CustomersMajor } from "@shopify/polaris-icons";
import SegmentBar from "@/apps/dashboard/components/shared/SegmentBar";
import Pressable from "@/apps/dashboard/pages/dashboard/ask/components/Pressable";
import DiscoveryCard from "../shared/DiscoveryCard";
import { ADOPTION_BAR, CATEGORY_SHORT, SUBTITLES } from "../constants";
import observeFunc from "@/apps/dashboard/pages/observe/transform";

const BAR_HEIGHT = "6px";
// A filled part and an empty part, so the leader fills the track.
const barOf = (employees, top) => [
    { label: "Employees", count: employees, color: ADOPTION_BAR.fill, tooltip: `${employees} employees` },
    { label: "Rest", count: top - employees, color: ADOPTION_BAR.track, tooltip: `${employees} employees` },
];

export default function AdoptionCard({ rows, status, onOpen }) {
    // Bars are relative to the most-used tool, so the leader fills the row.
    const topEmployees = Math.max(1, ...(rows || []).map((row) => row.employees));
    return (
        <DiscoveryCard
            title="Adoption"
            subtitle={SUBTITLES.adoption}
            status={status}
            empty={!rows || rows.length === 0}
            emptyIcon={CustomersMajor}
            emptyTone="subdued"
            emptyText="No employee has used an AI tool in the last 30 days."
        >
            <VerticalStack gap="3">
                {(rows || []).map((row) => (
                    <Pressable key={row.key} onClick={() => onOpen(row)} accessibilityLabel={`Open ${row.name}`} hoverBackground="bg-subdued" borderRadius="2" padding="1">
                        <VerticalStack gap="1">
                            <HorizontalStack align="space-between">
                                <Text as="span">{row.name} <Text as="span" color="subdued">· {CATEGORY_SHORT[row.category]}</Text></Text>
                                <Text as="span" fontWeight="semibold">{observeFunc.formatNumberWithCommas(row.employees)}</Text>
                            </HorizontalStack>
                            <SegmentBar segments={barOf(row.employees, topEmployees)} height={BAR_HEIGHT} />
                        </VerticalStack>
                    </Pressable>
                ))}
            </VerticalStack>
        </DiscoveryCard>
    );
}
