import { HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import Pressable from "../../../dashboard/ask/components/Pressable";
import InfoCard from "../../../dashboard/new_components/InfoCard";
import { LegendDot } from "../../agentic/AgenticStatsCard";
import SegmentBar from "../../../../components/shared/SegmentBar";
import { CATEGORY_BACKGROUNDS, CATEGORY_LABELS, CATEGORY_ORDER, OS_LABELS, tokenVar } from "./peopleConstants";
import transform from "./transform";

const OS_CATEGORIES = ["agents", "mcp"];
const DARK_CATEGORIES = ["agents"];
const BROWSER_COLORS = ["bg-info-strong", "bg-success-strong", "bg-caution-strong", "bg-info"];
const LIGHT_TEXT_BROWSER_COLORS = ["bg-info-strong", "bg-success-strong"];
const OS_BAR_HEIGHT = "22px";

const browserLabel = (browser) => browser.charAt(0).toUpperCase() + browser.slice(1);
const browserColor = (index) => tokenVar(BROWSER_COLORS[index % BROWSER_COLORS.length]);
const devicesLabel = (count) => `${count.toLocaleString("en-US")} ${count === 1 ? "device" : "devices"}`;
const formatCount = (count) => count.toLocaleString("en-US");

function BarRow({ label, meta, segments, onSegmentClick }) {
    return (
        <VerticalStack gap="1">
            <HorizontalStack align="space-between" blockAlign="center">
                <Text as="span" fontWeight="semibold">{label}</Text>
                <Text as="span" variant="bodySm" color="subdued">{meta}</Text>
            </HorizontalStack>
            <SegmentBar segments={segments} height={OS_BAR_HEIGHT} showLabels onSegmentClick={onSegmentClick} />
        </VerticalStack>
    );
}

function LegendItem({ color, label, count, active, onClick }) {
    return (
        <Pressable
            accessibilityLabel={label}
            onClick={onClick}
            background={active ? "bg-primary-subdued-hover" : undefined}
            hoverBackground="bg-subdued"
            borderRadius="2"
            padding="1"
        >
            <HorizontalStack gap="1" blockAlign="center" wrap={false}>
                <LegendDot color={color} />
                <Text as="span" variant="bodySm" color={active ? undefined : "subdued"}>{label} · {count.toLocaleString("en-US")}</Text>
            </HorizontalStack>
        </Pressable>
    );
}

function AiToolsByOsCard({ overview, status, activeCategory, onSelect, onSelectBrowser, onSelectCategory }) {
    const osRows = (overview?.osMatrix || []).filter((row) => row.os !== "unknown");
    const browserRows = (overview?.browserMatrix || []).map((row, index) => ({
        ...row,
        label: browserLabel(row.browser),
        count: row.toolsByCategory.browser || 0,
        token: BROWSER_COLORS[index % BROWSER_COLORS.length],
        color: browserColor(index),
    }));
    const browserDevices = browserRows.reduce((sum, row) => sum + row.devices, 0);
    const toolsAvailable = overview?.toolsAvailable !== false;

    const body = (
        <VerticalStack gap="4">
            {!toolsAvailable && <Text as="p" color="subdued">Tool data is unavailable right now.</Text>}
            {toolsAvailable && osRows.length === 0 && browserRows.length === 0 && <Text as="p" color="subdued">No managed devices in this scope.</Text>}
            {toolsAvailable && <Text as="p" color="subdued">Devices with each type of AI tool, by operating system and browser</Text>}
            {toolsAvailable && osRows.map((row) => (
                <BarRow
                    key={row.os}
                    label={OS_LABELS[row.os] || row.os}
                    meta={`${formatCount(row.devices)} devices`}
                    segments={OS_CATEGORIES.map((category) => ({
                        key: category,
                        label: CATEGORY_LABELS[category],
                        count: row.toolsByCategory[category] || 0,
                        color: tokenVar(CATEGORY_BACKGROUNDS[category]),
                        lightText: DARK_CATEGORIES.includes(category),
                        tooltip: `${CATEGORY_LABELS[category]} on ${OS_LABELS[row.os] || row.os}: ${devicesLabel(row.toolsByCategory[category] || 0)}`,
                    }))}
                    onSegmentClick={(category) => onSelect(row.os, category)}
                />
            ))}
            {toolsAvailable && browserRows.length > 0 && (
                <BarRow
                    label="Browsers"
                    meta={`${formatCount(browserDevices)} browsers`}
                    segments={browserRows.map((row) => ({
                        key: row.browser,
                        label: row.label,
                        count: row.count,
                        color: row.color,
                        lightText: LIGHT_TEXT_BROWSER_COLORS.includes(row.token),
                        tooltip: `${row.label}: ${devicesLabel(row.count)} with Browser AI`,
                    }))}
                    onSegmentClick={(browser) => onSelectBrowser(browser, "browser")}
                />
            )}
            <HorizontalStack gap="3" wrap>
                {CATEGORY_ORDER.map((category) => (
                    <LegendItem
                        key={category}
                        color={tokenVar(CATEGORY_BACKGROUNDS[category])}
                        label={CATEGORY_LABELS[category]}
                        count={overview?.categoryTotals?.[category] || 0}
                        active={activeCategory === category}
                        onClick={() => onSelectCategory(category)}
                    />
                ))}
                {browserRows.map((row) => (
                    <LegendItem
                        key={row.browser}
                        color={row.color}
                        label={row.label}
                        count={row.count}
                        active={false}
                        onClick={() => onSelectBrowser(row.browser, "browser")}
                    />
                ))}
            </HorizontalStack>
        </VerticalStack>
    );

    return (
        <InfoCard
            title="AI tools per device"
            titleToolTip="Number of devices with each type of AI tool, per operating system for desktop devices and per browser for browser extensions. A device with several types counts in each. Click a segment to filter the table."
            component={transform.cardBody(status, body)}
        />
    );
}

export default AiToolsByOsCard;
