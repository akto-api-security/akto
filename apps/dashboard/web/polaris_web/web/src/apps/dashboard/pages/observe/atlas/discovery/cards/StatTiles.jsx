import { useNavigate } from "react-router-dom";
import { Text, VerticalStack } from "@shopify/polaris";
import SummaryCardInfo from "@/apps/dashboard/components/shared/SummaryCardInfo";
import openInventory, { openNewLayoutPage } from "../shared/openInventory";
import { CATEGORY_LABELS, CATEGORY_ORDER, DEVICES_PATH, DEVICE_SCOPES, INVENTORY_CATEGORIES } from "../constants";
import observeFunc from "@/apps/dashboard/pages/observe/transform";

const EMPTY_VALUE = "—";
const format = (count) => (count === undefined || count === null ? EMPTY_VALUE : observeFunc.formatNumberWithCommas(Number(count)));

// A count with an optional line under it, sized like the Devices & employees tiles.
function statValue(value, subtext) {
    return (
        <VerticalStack gap="1">
            <Text as="p" variant="heading2xl">{format(value)}</Text>
            {subtext && <Text as="p" variant="bodySm" color={subtext.color || "subdued"}>{subtext.text}</Text>}
        </VerticalStack>
    );
}

// "N% of fleet" under a device count, capped at 100; none until the fleet size is known.
const fleetShare = (devices, managedDevices) => (devices != null && managedDevices > 0
    ? { text: `${Math.min(100, Math.round((100 * devices) / managedDevices))}% of fleet` }
    : null);

// The line under each count: the thing in that category most worth a look.
function subLine(id, tile) {
    if (tile.count === 0) return { text: "None found" };
    if (id === "browser" && tile.personalAccounts > 0) return { text: `${format(tile.personalAccounts)} personal accounts`, critical: true };
    // Only malicious skills are put up for a decision, so that is the one line a skills tile can carry.
    if (id === "skills") return tile.malicious > 0 ? { text: `${format(tile.malicious)} flagged malicious`, critical: true } : { text: "" };
    return tile.unapproved > 0 ? { text: `${format(tile.unapproved)} need a decision`, critical: true } : { text: "All approved" };
}

export default function StatTiles({ tiles, deviceScope, fleet }) {
    const navigate = useNavigate();
    // A narrower scope has no fleet-wide count, so it uses its own.
    const devicesWithAi = deviceScope === DEVICE_SCOPES[0].value && fleet.devicesWithAi !== null ? fleet.devicesWithAi : tiles?.devicesWithAi;
    const items = CATEGORY_ORDER.map((id) => {
        const tile = tiles?.categories?.find((candidate) => candidate.id === id);
        const sub = tile ? subLine(id, tile) : { text: "" };
        return { title: CATEGORY_LABELS[id], isComp: true, data: statValue(tile?.count, sub.text && { text: sub.text, color: sub.critical ? "critical" : undefined }), onClick: () => openInventory(navigate, INVENTORY_CATEGORIES[id]) };
    });
    items.push({ title: "Devices with AI", isComp: true, data: statValue(devicesWithAi, fleetShare(devicesWithAi, fleet.managedDevices)), onClick: () => openNewLayoutPage(navigate, DEVICES_PATH) });
    return <SummaryCardInfo summaryItems={items} />;
}
