import { Divider, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import InfoCard from "../../../dashboard/new_components/InfoCard";
import { BreakdownChip } from "../../agentic/AgenticStatsCard";
import SegmentBar from "../../../../components/shared/SegmentBar";
import { CATEGORY_BACKGROUNDS, CATEGORY_LABELS, CATEGORY_ORDER, osLabel, tokenVar } from "./peopleConstants";
import transform from "./transform";

const OS_CATEGORIES = ["agents", "mcp"];
const DARK_CATEGORIES = ["agents"];
const DARK_TEXT_OS = ["linux"];
const BROWSER_COLORS = ["bg-info-strong", "bg-success-strong", "bg-caution-strong", "bg-info"];
const LIGHT_TEXT_BROWSER_COLORS = ["bg-info-strong", "bg-success-strong"];
const OS_BAR_HEIGHT = "22px";
const OS_COLORS = {
    macos: ["bg-magic-strong", "bg-magic-active"],
    windows: ["bg-interactive", "bg-interactive-subdued-active"],
    linux: ["bg-caution-strong", "bg-caution"],
};

const browserLabel = (browser) => browser.charAt(0).toUpperCase() + browser.slice(1);
const browserColor = (index) => tokenVar(BROWSER_COLORS[index % BROWSER_COLORS.length]);
const devicesLabel = (count) => `${count.toLocaleString("en-US")} ${count === 1 ? "device" : "devices"}`;
const formatCount = (count) => count.toLocaleString("en-US");

function BarRow({ label, meta, segments, onSegmentClick, activeKey, divider }) {
    return (
        <VerticalStack gap="4">
            {divider && <Divider />}
            <VerticalStack gap="1">
                <HorizontalStack align="space-between" blockAlign="center">
                    <Text as="span" fontWeight="semibold">{label}</Text>
                    <Text as="span" variant="bodySm" color="subdued">{meta}</Text>
                </HorizontalStack>
                <SegmentBar segments={segments} height={OS_BAR_HEIGHT} showLabels onSegmentClick={onSegmentClick} activeKey={activeKey} />
            </VerticalStack>
        </VerticalStack>
    );
}

function AiToolsByOsCard({ overview, status, activeCategory, activeBrowser, onSelect, onSelectBrowser, onSelectCategory }) {
    const osRows = (overview?.osMatrix || []).filter((row) => row.os !== "unknown");
    const browserRows = (overview?.browserMatrix || []).map((row, index) => ({
        ...row,
        label: browserLabel(row.browser),
        count: row.toolsByCategory.browser || 0,
        token: BROWSER_COLORS[index % BROWSER_COLORS.length],
        color: browserColor(index),
    }));
    const browserAiDevices = browserRows.reduce((sum, row) => sum + row.count, 0);
    const toolsAvailable = overview?.toolsAvailable !== false;
    const activeCategorySet = new Set(activeCategory ? [activeCategory] : []);
    const activeBrowserSet = new Set(activeBrowser ? [activeBrowser] : []);

    const body = (
        <VerticalStack gap="4">
            {!toolsAvailable && <Text as="p" color="subdued">Tool data is unavailable right now.</Text>}
            {toolsAvailable && osRows.length === 0 && browserRows.length === 0 && <Text as="p" color="subdued">No managed devices in this scope.</Text>}
            {toolsAvailable && osRows.map((row, index) => (
                <BarRow
                    key={row.os}
                    divider={index > 0}
                    label={osLabel(row.os)}
                    meta={`${formatCount(row.devicesWithTools)} with AI tools`}
                    segments={OS_CATEGORIES.map((category, index) => ({
                        key: category,
                        label: CATEGORY_LABELS[category],
                        count: row.toolsByCategory[category] || 0,
                        color: tokenVar(OS_COLORS[row.os]?.[index] || CATEGORY_BACKGROUNDS[category]),
                        lightText: DARK_CATEGORIES.includes(category) && !DARK_TEXT_OS.includes(row.os),
                        tooltip: `${CATEGORY_LABELS[category]} on ${osLabel(row.os)}: ${devicesLabel(row.toolsByCategory[category] || 0)}`,
                    }))}
                    onSegmentClick={(category) => onSelect(row.os, category)}
                />
            ))}
            {toolsAvailable && browserRows.length > 0 && (
                <BarRow
                    label="Browsers"
                    divider={osRows.length > 0}
                    meta={`${formatCount(browserAiDevices)} with Browser AI`}
                    segments={browserRows.map((row) => ({
                        key: row.browser,
                        label: row.label,
                        count: row.count,
                        color: row.color,
                        lightText: LIGHT_TEXT_BROWSER_COLORS.includes(row.token),
                        tooltip: `${row.label}: ${devicesLabel(row.count)} with Browser AI`,
                    }))}
                    onSegmentClick={(browser) => onSelectBrowser(browser, "browser")}
                    activeKey={activeBrowser}
                />
            )}
            <VerticalStack gap="1">
                <HorizontalStack gap="1" wrap>
                    {CATEGORY_ORDER.map((category) => (
                        <BreakdownChip
                            key={category}
                            b={{ key: category, label: CATEGORY_LABELS[category], count: overview?.categoryTotals?.[category] || 0, color: tokenVar(CATEGORY_BACKGROUNDS[category]) }}
                            activeFilter={activeCategorySet}
                            onFilterClick={onSelectCategory}
                        />
                    ))}
                </HorizontalStack>
                <HorizontalStack gap="1" wrap>
                    {browserRows.map((row) => (
                        <BreakdownChip
                            key={row.browser}
                            b={{ key: row.browser, label: row.label, count: row.count, color: row.color }}
                            activeFilter={activeBrowserSet}
                            onFilterClick={(browser) => onSelectBrowser(browser, "browser")}
                        />
                    ))}
                </HorizontalStack>
            </VerticalStack>
        </VerticalStack>
    );

    return (
        <InfoCard
            title="AI tools per device"
            subtitle="AI tools by operating system and browser"
            titleToolTip={transform.tooltip("Number of devices with each type of AI tool, per operating system for desktop devices and per browser for browser extensions. A device with several types counts in each. Click a segment to filter the table.")}
            component={transform.cardBody(status, body)}
        />
    );
}

export default AiToolsByOsCard;
