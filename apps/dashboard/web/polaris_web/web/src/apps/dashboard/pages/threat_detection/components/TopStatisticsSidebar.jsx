import { useEffect, useState } from "react";
import { Card, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import api from "../api";
import observeFunc from "../../observe/transform";
import { formatCategoryName, getFlagSrc, countryCodeToName, openThreatActivityPage } from "../utils/threatDashboardUtils";

function timeAgo(epochSeconds) {
    if (!epochSeconds) return "";
    const diff = Math.floor(Date.now() / 1000) - epochSeconds;
    if (diff < 60) return "just now";
    if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
    if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
    return `${Math.floor(diff / 86400)}d ago`;
}

const sidebarStyles = `
.sidebar-list-item:hover {
    background: var(--p-color-bg-hover, #F6F6F7);
    border-radius: 4px;
    margin-inline: -0.25rem;
    padding-inline: 0.25rem;
}
`;

function SidebarSection({ title, children, borderColor }) {
    return (
        <div style={borderColor ? { border: `1px solid ${borderColor}`, borderRadius: "var(--p-border-radius-2, 8px)", overflow: "hidden" } : undefined}>
            <Card padding={4}>
                <VerticalStack gap="3">
                    <Text variant="headingSm">{title}</Text>
                    {children}
                </VerticalStack>
            </Card>
        </div>
    );
}

function SidebarListItem({ icon, label, count, onClick, isLast }) {
    return (
        <div
            onClick={onClick}
            className="sidebar-list-item"
            style={{
                cursor: onClick ? "pointer" : "default",
                paddingBlock: "0.5rem",
                borderBottom: isLast ? "none" : "1px solid var(--p-color-border-subdued, #E1E3E5)",
            }}
        >
            <HorizontalStack align="space-between" blockAlign="center">
                <HorizontalStack gap="2" blockAlign="center">
                    {icon}
                    <Text variant="bodySm">{label}</Text>
                </HorizontalStack>
                <Text variant="bodySm">{observeFunc.formatNumberWithCommas(count)}</Text>
            </HorizontalStack>
        </div>
    );
}

function TopStatisticsSidebar({ startTimestamp, endTimestamp }) {
    const [topHosts, setTopHosts] = useState([]);
    const [countries, setCountries] = useState([]);
    const [recentActivity, setRecentActivity] = useState([]);

    useEffect(() => {
        const fetchData = async () => {
            try {
                const [topResp, countryResp, recentResp] = await Promise.all([
                    api.fetchThreatTopNData(startTimestamp, endTimestamp, [], 5),
                    api.getActorsCountPerCounty(startTimestamp, endTimestamp),
                    api.fetchSuspectSampleData(0, [], [], [], [], { detectedAt: -1 }, startTimestamp, endTimestamp, [], 5),
                ]);

                if (topResp?.topHosts) {
                    setTopHosts(topResp.topHosts);
                }
                if (countryResp?.actorsCountPerCountry) {
                    setCountries(
                        [...countryResp.actorsCountPerCountry]
                            .sort((a, b) => b.count - a.count)
                            .slice(0, 5)
                    );
                }
                if (recentResp?.maliciousEvents) {
                    setRecentActivity(recentResp.maliciousEvents.slice(0, 5));
                }
            } catch (err) {
                // keep empty
            }
        };
        fetchData();
    }, [startTimestamp, endTimestamp]);

    return (
        <VerticalStack gap="4">
            <style>{sidebarStyles}</style>

            <SidebarSection title="Recent Activity" borderColor="#E45858">
                <VerticalStack gap="2">
                    {recentActivity.length === 0 ? (
                        <Text variant="bodySm" color="subdued">No data</Text>
                    ) : (
                        recentActivity.map((event, idx) => (
                            <div
                                key={idx}
                                onClick={() => openThreatActivityPage({ latestAttack: event.subCategory || event.filterId })}
                                className="sidebar-list-item"
                                style={{
                                    cursor: "pointer",
                                    paddingBlock: "0.5rem",
                                    borderBottom: idx === recentActivity.length - 1 ? "none" : "1px solid var(--p-color-border-subdued, #E1E3E5)",
                                }}
                            >
                                <VerticalStack gap="1">
                                    <HorizontalStack align="space-between" blockAlign="center">
                                        <Text variant="bodySm" fontWeight="semibold">
                                            {formatCategoryName(event.subCategory || event.filterId)}
                                        </Text>
                                        <Text variant="bodySm" color="subdued">
                                            {timeAgo(event.timestamp)}
                                        </Text>
                                    </HorizontalStack>
                                    <Text variant="bodySm" color="subdued" truncate>
                                        {event.actor || "Unknown actor"}
                                    </Text>
                                </VerticalStack>
                            </div>
                        ))
                    )}
                </VerticalStack>
            </SidebarSection>

            <SidebarSection title="Top Attacked Hosts">
                <VerticalStack gap="2">
                    {topHosts.length === 0 ? (
                        <Text variant="bodySm" color="subdued">No data</Text>
                    ) : (
                        topHosts.map((host, idx) => (
                            <SidebarListItem
                                key={idx}
                                icon={<HostFavicon host={host.host} />}
                                label={host.host}
                                count={host.attacks}
                                onClick={() => openThreatActivityPage({ host: host.host })}
                                isLast={idx === topHosts.length - 1}
                            />
                        ))
                    )}
                </VerticalStack>
            </SidebarSection>

            <SidebarSection title="Top Countries">
                <VerticalStack gap="2">
                    {countries.length === 0 ? (
                        <Text variant="bodySm" color="subdued">No data</Text>
                    ) : (
                        countries.map((item, idx) => (
                            <SidebarListItem
                                key={idx}
                                icon={
                                    <img
                                        src={getFlagSrc(item.country)}
                                        alt={item.country || ""}
                                        style={{ width: "1.25em", height: "1.25em" }}
                                    />
                                }
                                label={countryCodeToName(item.country)}
                                count={item.count}
                                isLast={idx === countries.length - 1}
                            />
                        ))
                    )}
                </VerticalStack>
            </SidebarSection>
        </VerticalStack>
    );
}

function HostFavicon({ host }) {
    if (!host) return null;
    const cleanHost = host.replace(/:\d+$/, "");
    return (
        <img
            src={`https://www.google.com/s2/favicons?domain=${cleanHost}&sz=16`}
            alt=""
            style={{ width: "1em", height: "1em", borderRadius: "2px" }}
            onError={(e) => { e.target.style.display = "none"; }}
        />
    );
}

export default TopStatisticsSidebar;
