import { useEffect, useState, useRef } from "react";
import InfoCard from "../../dashboard/new_components/InfoCard";
import { Spinner, Text, SkeletonBodyText, SkeletonDisplayText } from "@shopify/polaris";
import StackedAreaChart from "../../../components/charts/StackedAreaChart";
import api from "../api";
import dayjs from "dayjs";

// ============================================================================
// CONSTANTS
// ============================================================================
const CHART_CONFIG = {
  TOP_CATEGORIES: 6,
  HEIGHT: 380,
  COLOR_SATURATION: 60,
  COLOR_LIGHTNESS: 75,
  GOLDEN_ANGLE: 137.5,
  MILLISECOND_THRESHOLD: 1e12,
};

const BASE_COLORS = [
  "#7FB3D5", // soft blue
  "#85C1E9", // light sky
  "#AED6F1", // pale blue
  "#76D7C4", // mint
  "#82E0AA", // light green
  "#F7DC6F", // soft yellow
  "#F8C471", // warm amber
  "#F5B7B1", // pale coral
  "#F1948A", // light red
  "#D7BDE2", // lavender
  "#BB8FCE", // muted purple
  "#FADBD8", // pale pink
];

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Convert value to number with fallback
 */
const toNumber = (val, defaultVal = 0) => Number(val) || defaultVal;

/**
 * Format internal names for display: snake_case to Title Case.
 */
const formatName = (name) => {
  if (!name) return "Unknown";
  return String(name)
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (l) => l.toUpperCase());
};

/**
 * Validate and normalize timestamp
 */
const normalizeTimestamp = (ts) => {
  const num = toNumber(ts);
  if (!num || num < 0) return 0;
  return num > CHART_CONFIG.MILLISECOND_THRESHOLD ? Math.floor(num / 1000) : num;
};

/**
 * Generates a dynamic color palette for chart categories.
 * Extends with HSL colors using golden angle if more colors are needed.
 */
const generateColorPalette = (count) => {
  const colors = BASE_COLORS.slice();
  while (colors.length < count) {
    const hue = (colors.length * CHART_CONFIG.GOLDEN_ANGLE) % 360;
    colors.push(`hsl(${hue}, ${CHART_CONFIG.COLOR_SATURATION}%, ${CHART_CONFIG.COLOR_LIGHTNESS}%)`);
  }
  return colors.slice(0, count);
};

/**
 * Aggregate threat data by day and subcategory
 */
const aggregateDailyData = (timelines) => {
  const dayMap = new Map();

  (timelines || []).forEach((item) => {
    const ts = normalizeTimestamp(item.ts || item.timestamp || item.time);
    const dayKey = dayjs(ts * 1000).startOf("day").unix();

    if (!dayMap.has(dayKey)) dayMap.set(dayKey, {});
    const bucket = dayMap.get(dayKey);

    const subList = item.subCategoryWiseData || [];
    subList.forEach((s) => {
      const rawKey = s.subcategory || "Unknown";
      const key = String(rawKey).trim();
      const cnt = toNumber(s?.activityCount);
      bucket[key] = toNumber(bucket[key]) + cnt;
    });
  });

  return dayMap;
};

/**
 * Process threat data into chart series format
 */
const processChartData = (dayMap) => {
  const dayKeys = Array.from(dayMap.keys()).sort((a, b) => a - b);

  // Collect all unique subcategories
  const subSet = new Set();
  dayKeys.forEach((k) =>
    Object.keys(dayMap.get(k)).forEach((sk) => subSet.add(sk))
  );

  if (!subSet.size) return null;

  // Compute total counts per subcategory
  const totals = {};
  subSet.forEach((s) => { totals[s] = 0; });
  dayKeys.forEach((k) => {
    Object.entries(dayMap.get(k)).forEach(([sk, v]) => {
      totals[sk] = toNumber(totals[sk]) + toNumber(v);
    });
  });

  // Identify top N subcategories plus 'Other' group
  const sortedSubs = Array.from(subSet).sort(
    (a, b) => (totals[b] || 0) - (totals[a] || 0)
  );
  const topSubs = sortedSubs.slice(0, CHART_CONFIG.TOP_CATEGORIES);
  const otherSubs = sortedSubs.slice(CHART_CONFIG.TOP_CATEGORIES);

  // Build series map
  const seriesMap = {};
  topSubs.forEach((s) => { seriesMap[s] = dayKeys.map(() => 0); });
  if (otherSubs.length) seriesMap["Other"] = dayKeys.map(() => 0);

  dayKeys.forEach((k, di) => {
    Object.entries(dayMap.get(k)).forEach(([sk, val]) => {
      const numericVal = toNumber(val);
      if (topSubs.includes(sk)) {
        seriesMap[sk][di] = toNumber(seriesMap[sk][di]) + numericVal;
      } else if (otherSubs.length) {
        seriesMap["Other"][di] = toNumber(seriesMap["Other"][di]) + numericVal;
      }
    });
  });

  const seriesKeys = topSubs.concat(otherSubs.length ? ["Other"] : []);

  return { seriesMap, seriesKeys, dayKeys, totals };
};

/**
 * Calculate percentage distribution for legend
 */
const calculatePercentages = (seriesData) => {
  const grandTotals = {};
  let grandSum = 0;

  seriesData.forEach((s) => {
    const sum = (s.data || []).reduce((acc, v) => acc + toNumber(v[1]), 0);
    grandTotals[s.rawName] = sum;
    grandSum += sum;
  });

  return seriesData
    .map((s) => ({
      name: s.name,
      rawName: s.rawName,
      percent: grandSum > 0 ? Math.round((grandTotals[s.rawName] / grandSum) * 1000) / 10 : 0,
      color: s.color,
    }))
    .sort((a, b) => b.percent - a.percent);
};

// ============================================================================
// COMPONENTS
// ============================================================================

/**
 * Skeleton loading component
 */
const ChartSkeleton = () => (
  <div style={{ padding: "16px" }}>
    <div style={{ display: "flex", gap: 20, marginBottom: 16 }}>
      {[1, 2, 3, 4].map((i) => (
        <div key={i} style={{ display: "flex", gap: 8, alignItems: "center" }}>
          <div style={{ width: 12, height: 12, background: "#E5E7EB", borderRadius: 2 }} />
          <SkeletonBodyText lines={1} />
        </div>
      ))}
    </div>
    <div style={{ height: CHART_CONFIG.HEIGHT }}>
      <SkeletonDisplayText size="large" />
      <SkeletonBodyText lines={8} />
    </div>
  </div>
);

/**
 * Empty state component
 */
const EmptyState = ({ message }) => (
  <div style={{ padding: 40, textAlign: "center" }}>
    <Text variant="bodyMd" color="subdued">
      {message}
    </Text>
  </div>
);

/**
 * Error state component
 */
const ErrorState = ({ message }) => (
  <div style={{ padding: 40, textAlign: "center" }}>
    <Text variant="bodyMd" color="critical">
      {message}
    </Text>
  </div>
);

/**
 * Legend item component
 */
const LegendItem = ({ item, onToggle }) => (
  <div
    onClick={() => onToggle(item.name)}
    title={item.visible === false ? "Click to show" : "Click to hide"}
    style={{
      display: "flex",
      alignItems: "center",
      gap: 8,
      cursor: "pointer",
      opacity: item.visible === false ? 0.45 : 1,
    }}
  >
    <div
      style={{
        width: 12,
        height: 12,
        background: item.color,
        borderRadius: 2,
      }}
    />
    <Text variant="bodySm" color="subdued">
      <Text variant="bodySm" fontWeight="bold" color="base">
        {item.percent}%
      </Text>{" "}
      {item.name}
    </Text>
  </div>
);

/**
 * Chart legend component
 */
const ChartLegend = ({ items, onToggle }) => {
  if (!items || items.length === 0) return null;

  return (
    <div
      style={{
        display: "flex",
        gap: 20,
        alignItems: "center",
        padding: "12px 16px",
        borderBottom: "1px solid #E5E7EB",
      }}
    >
      {items.map((item) => (
        <LegendItem key={item.name} item={item} onToggle={onToggle} />
      ))}
    </div>
  );
};

/**
 * Stacked Percent Area Chart for threat categories over time.
 */
function ThreatCategoryStackedChart({ startTimestamp, endTimestamp }) {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [chartData, setChartData] = useState([]);
  const [latestPercents, setLatestPercents] = useState([]);
  const [visibleSeries, setVisibleSeries] = useState({});
  const baseDataRef = useRef(null);

  useEffect(() => {
    let mounted = true;

    const loadThreatData = async () => {
      setLoading(true);
      setError(null);

      try {
        // Fetch time-series threat activity for given range
        const resp = await api.getThreatActivityTimeline(
          startTimestamp,
          endTimestamp
        );

        if (!mounted) return;

        if (!resp?.threatActivityTimelines || !resp.threatActivityTimelines.length) {
          setChartData([]);
          setLatestPercents([]);
          return;
        }

        // Aggregate counts per day and subcategory
        const dayMap = aggregateDailyData(resp.threatActivityTimelines);

        // Process chart data
        const processed = processChartData(dayMap);

        if (!processed) {
          setChartData([]);
          setLatestPercents([]);
          return;
        }

        const { seriesMap, seriesKeys, dayKeys } = processed;

        // Assign color palette & format series for StackedChart
        const palette = generateColorPalette(seriesKeys.length);
        const seriesData = seriesKeys.map((k, i) => ({
          name: formatName(k),
          rawName: k,
          data: seriesMap[k].map((value, index) => [
            dayKeys[index] * 1000, // x: timestamp in milliseconds
            value // y: value
          ]),
          color: palette[i],
          visible: true,
        }));

        // Calculate percentages for legend
        const initialLatest = calculatePercentages(seriesData);
        setLatestPercents(initialLatest);

        // Store base data for toggling updates
        baseDataRef.current = {
          seriesData,
          seriesKeys,
        };

        setChartData(seriesData);
      } catch (err) {
        console.error("Failed to load threat activity data:", err);
        if (mounted) {
          setError(err.message || "Failed to load threat activity data. Please try again.");
          setChartData([]);
          setLatestPercents([]);
        }
      } finally {
        if (mounted) setLoading(false);
      }
    };

    loadThreatData();
    return () => {
      mounted = false;
    };
  }, [startTimestamp, endTimestamp]);

  // When visibleSeries changes, update chart series visibility but keep percentages constant
  useEffect(() => {
    if (!baseDataRef.current) return;
    const base = baseDataRef.current;
    const newSeriesData = (base.seriesData || []).map((s) => ({
      ...s,
      visible: visibleSeries[s.name] !== false,
    }));

    setLatestPercents((prev) =>
      prev.map((item) => ({
        ...item,
        visible: visibleSeries[item.name] !== false,
      }))
    );

    setChartData(newSeriesData);
  }, [visibleSeries]);

  const toggleSeries = (name) => {
    setVisibleSeries((prev) => ({ ...prev, [name]: !(prev[name] !== false) }));
  };

  return (
    <InfoCard
      title="Threat Activity (by Category)"
      titleToolTip="Stacked area view showing distribution of activity across categories over time"
      component={
        loading ? (
          <ChartSkeleton />
        ) : error ? (
          <ErrorState message={error} />
        ) : (
          <>
            <ChartLegend items={latestPercents} onToggle={toggleSeries} />
            {chartData.length > 0 ? (
              <StackedAreaChart
                height={CHART_CONFIG.HEIGHT}
                backgroundColor="#ffffff"
                data={chartData}
                yAxisTitle="Percentage"
                showGridLines={true}
                customXaxis={{
                  type: 'datetime',
                  dateTimeLabelFormats: {
                    day: '%b %e',
                    month: '%b',
                  },
                  title: {
                    text: 'Date',
                  },
                  visible: true,
                  gridLineWidth: 0,
                }}
                defaultChartOptions={{
                  tooltip: {
                    shared: true,
                    useHTML: true,
                    backgroundColor: "rgba(255,255,255,0.95)",
                    borderColor: "#E5E7EB",
                    borderRadius: 8,
                    padding: 12,
                    style: { color: "#111827", fontSize: "12px" },
                    formatter() {
                      let total = 0;
                      this.points.forEach((p) => {
                        total += p.y;
                      });
                      let tooltipHtml = `<div style="font-weight: 600; margin-bottom: 8px;">${dayjs(this.x).format("ddd, D MMM")}</div>`;
                      this.points.forEach((p) => {
                        const percentage =
                          total > 0 ? ((p.y / total) * 100).toFixed(1) : 0;
                        tooltipHtml += `<div style="display: flex; align-items: center; margin: 4px 0;">
                            <span style="display: inline-block; width: 10px; height: 10px; background: ${p.color}; border-radius: 2px; margin-right: 8px;"></span>
                            <span style="flex: 1;">${p.series.name}:</span>
                            <span style="font-weight: 600; margin-left: 8px;">${percentage}%</span>
                          </div>`;
                      });
                      return tooltipHtml;
                    },
                  },
                }}
              />
            ) : (
              <EmptyState message="No threat activity data found for the selected time period." />
            )}
          </>
        )
      }
    />
  );
}

export default ThreatCategoryStackedChart;
