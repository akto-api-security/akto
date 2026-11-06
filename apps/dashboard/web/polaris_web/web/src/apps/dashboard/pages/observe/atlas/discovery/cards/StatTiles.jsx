import { Text, VerticalStack } from "@shopify/polaris";
import SummaryCardInfo from "@/apps/dashboard/components/shared/SummaryCardInfo";
import { CATEGORY_LABELS, CATEGORY_ORDER } from "../constants";

const EMPTY_VALUE = "—";
const format = (count) => (count === undefined || count === null ? EMPTY_VALUE : Number(count).toLocaleString("en-US"));

// The line under each count: the thing in that category most worth a look.
function subLine(id, tile) {
    if (tile.count === 0) return { text: "None found" };
    if (id === "browser" && tile.personalAccounts > 0) return { text: `${format(tile.personalAccounts)} personal accounts`, critical: true };
    // Only malicious skills are put up for a decision, so that is the one line a skills tile can carry.
    if (id === "skills") return tile.malicious > 0 ? { text: `${format(tile.malicious)} flagged malicious`, critical: true } : { text: "" };
    return tile.unapproved > 0 ? { text: `${format(tile.unapproved)} need a decision`, critical: true } : { text: "All approved" };
}

function Tile({ value, sub, critical }) {
    return (
        <VerticalStack gap="0">
            <Text as="p" variant="headingLg">{value}</Text>
            {sub && <Text as="p" variant="bodySm" color={critical ? "critical" : "subdued"}>{sub}</Text>}
        </VerticalStack>
    );
}

export default function StatTiles({ tiles }) {
    const items = CATEGORY_ORDER.map((id) => {
        const tile = tiles?.categories?.find((candidate) => candidate.id === id);
        const sub = tile ? subLine(id, tile) : { text: "" };
        return { title: CATEGORY_LABELS[id], isComp: true, data: <Tile value={format(tile?.count)} sub={sub.text} critical={sub.critical} /> };
    });
    items.push({
        title: "Devices with AI",
        isComp: true,
        data: <Tile value={format(tiles?.devicesWithAi)} />,
    });
    return <SummaryCardInfo summaryItems={items} />;
}
