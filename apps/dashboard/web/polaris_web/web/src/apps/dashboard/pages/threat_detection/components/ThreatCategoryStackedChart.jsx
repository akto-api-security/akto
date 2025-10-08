import { useEffect, useState, useRef } from "react";
import Highcharts from "highcharts";
import HighchartsReact from "highcharts-react-official";
import InfoCard from "../../dashboard/new_components/InfoCard";
import { Spinner } from "@shopify/polaris";
import api from "../api";
import dayjs from "dayjs";

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
 * Generates a dynamic color palette for chart categories.
 * Extends with HSL colors using golden angle if more colors are needed.
 */
const generateColorPalette = (count) => {
  // Softer, pastel palette for better readability
  const baseColors = [
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
  const colors = baseColors.slice();
  while (colors.length < count) {
    // generate soft pastels using golden angle with higher lightness
    const hue = (colors.length * 137.5) % 360;
    colors.push(`hsl(${hue}, 60%, 75%)`);
  }
  return colors.slice(0, count);
};

/**
 * Stacked Percent Area Chart for threat categories over time.
 */
function ThreatCategoryStackedChart({ startTimestamp, endTimestamp }) {
  const [loading, setLoading] = useState(true);
  const [options, setOptions] = useState(null);
  const [latestPercents, setLatestPercents] = useState([]);
  const [visibleSeries, setVisibleSeries] = useState({});
  const baseOptionsRef = useRef(null);

  useEffect(() => {
    let mounted = true;

    const loadThreatData = async () => {
      setLoading(true);
      try {
        // Fetch time-series threat activity for given range
        const resp = await api.getThreatActivityTimeline(
          startTimestamp,
          endTimestamp
        );

        if (!mounted) return;

        if (
          !resp?.threatActivityTimelines ||
          !resp.threatActivityTimelines.length
        ) {
          setOptions(null);
          setLatestPercents([]);
          return;
        }

        // Aggregate counts per day and subcategory
        const dayMap = new Map();
        (resp.threatActivityTimelines || []).forEach((item) => {
          // Normalize and coerce timestamp
          let ts = Number(item.ts || item.timestamp || item.time) || 0;
          if (ts > 1e12) ts = Math.floor(ts / 1000); // milliseconds -> seconds
          const dayKey = dayjs(ts * 1000)
            .startOf("day")
            .unix();
          if (!dayMap.has(dayKey)) dayMap.set(dayKey, {});
          const bucket = dayMap.get(dayKey);

          // Accept multiple naming conventions for the category list
          const subList =
            item.subCategoryWiseData ||
            item.sub_category_wise_data ||
            item.subCategoryData ||
            item.categoryData ||
            item.categories ||
            [];

          subList.forEach((s) => {
            // Normalize the category key (trim + fallback) and coerce count to number
            const rawKey =
              s.subcategory ||
              s.subCategory ||
              s.sub ||
              s.subCategoryName ||
              s.category ||
              s.name ||
              "Unknown";
            const key = String(rawKey).trim();
            const cnt = Number(s.activityCount ?? s.count ?? s.value ?? 0) || 0;

            // Ensure numeric accumulation (avoid string concatenation)
            bucket[key] = Number(bucket[key] || 0) + cnt;
          });
        });

        const dayKeys = Array.from(dayMap.keys()).sort((a, b) => a - b);
        const categories = dayKeys.map((k) =>
          dayjs(k * 1000).format("ddd, D MMM")
        );

        // Collect all unique subcategories
        const subSet = new Set();
        dayKeys.forEach((k) =>
          Object.keys(dayMap.get(k)).forEach((sk) => subSet.add(sk))
        );
        if (!subSet.size) {
          setOptions(null);
          setLatestPercents([]);
          return;
        }

        // Compute total counts per subcategory (for prominence ranking)
        const totals = {};
        subSet.forEach((s) => {
          totals[s] = 0;
        });
        dayKeys.forEach((k) => {
          Object.entries(dayMap.get(k)).forEach(([sk, v]) => {
            totals[sk] = Number(totals[sk] || 0) + (Number(v) || 0);
          });
        });

        // Identify top N subcategories plus an 'Other' group
        const TOP_N = 6;
        const sortedSubs = Array.from(subSet).sort(
          (a, b) => (totals[b] || 0) - (totals[a] || 0)
        );
        const topSubs = sortedSubs.slice(0, TOP_N);
        const otherSubs = sortedSubs.slice(TOP_N);
        const seriesMap = {};
        topSubs.forEach((s) => {
          seriesMap[s] = dayKeys.map(() => 0);
        });
        if (otherSubs.length) seriesMap["Other"] = dayKeys.map(() => 0);
        dayKeys.forEach((k, di) => {
          Object.entries(dayMap.get(k)).forEach(([sk, val]) => {
            const numericVal = Number(val) || 0;
            if (topSubs.includes(sk)) {
              seriesMap[sk][di] = Number(seriesMap[sk][di] || 0) + numericVal;
            } else if (otherSubs.length) {
              seriesMap["Other"][di] =
                Number(seriesMap["Other"][di] || 0) + numericVal;
            }
          });
        });

        const seriesKeys = topSubs.concat(otherSubs.length ? ["Other"] : []);

        // Assign color palette & format series for chart
        const palette = generateColorPalette(seriesKeys.length);
        const series = seriesKeys.map((k, i) => ({
          name: formatName(k),
          rawName: k,
          data: seriesMap[k],
          color: palette[i],
          fillOpacity: 0.9,
          visible: true,
        }));

        // Compute header percentages using sums over the series data (ensures header matches chart)
        const grandTotals = {};
        let grandSum = 0;
        series.forEach((s) => {
          const sum = (s.data || []).reduce(
            (acc, v) => acc + (Number(v) || 0),
            0
          );
          grandTotals[s.rawName] = sum;
          grandSum += sum;
        });

        const initialLatest = series
          .map((s) => ({
            name: s.name,
            rawName: s.rawName,
            percent:
              grandSum > 0
                ? Math.round(
                    ((grandTotals[s.rawName] || 0) / grandSum) * 1000
                  ) / 10
                : 0,
            color: s.color,
          }))
          .sort((a, b) => b.percent - a.percent);
        setLatestPercents(initialLatest);

        // Build Highcharts options
        const opts = {
          chart: {
            type: "area",
            height: 380,
            backgroundColor: "#ffffff",
            style: {
              fontFamily:
                '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
            },
          },
          title: { text: undefined },
          credits: { enabled: false },
          xAxis: {
            categories,
            labels: { style: { color: "#6B7280", fontSize: "11px" } },
            lineColor: "#E5E7EB",
            tickColor: "#E5E7EB",
          },
          yAxis: {
            title: { text: "" },
            labels: {
              formatter() {
                return this.value + "%";
              },
              style: { color: "#6B7280", fontSize: "11px" },
            },
            max: 100,
            gridLineColor: "#F3F4F6",
          },
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
              let tooltipHtml = `<div style="font-weight: 600; margin-bottom: 8px;">${this.x}</div>`;
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
          plotOptions: {
            area: {
              stacking: "percent",
              marker: { enabled: false },
              lineWidth: 0,
              states: { hover: { lineWidthPlus: 0 } },
            },
          },
          legend: { enabled: false },
          series,
        };

        // keep base options and totals for toggling updates
        baseOptionsRef.current = {
          ...opts,
          _totals: totals,
          _seriesKeys: seriesKeys,
        };
        setOptions(opts);
      } catch (err) {
        if (mounted) {
          setOptions(null);
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
    if (!baseOptionsRef.current) return;
    const base = baseOptionsRef.current;
    const newSeries = (base.series || []).map((s) => ({
      ...s,
      visible: visibleSeries[s.name] !== false,
    }));

    setLatestPercents((prev) =>
      prev.map((item) => ({
        ...item,
        visible: visibleSeries[item.name] !== false,
      }))
    );

    setOptions((prev) => ({ ...prev, series: newSeries }));
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
          <div
            style={{
              display: "flex",
              justifyContent: "center",
              alignItems: "center",
              height: "300px",
            }}
          >
            <Spinner size="large" />
          </div>
        ) : (
          <div style={{ width: "100%" }}>
            {/* Header with latest percentages */}
            {latestPercents.length > 0 && (
              <div
                style={{
                  display: "flex",
                  gap: 20,
                  alignItems: "center",
                  padding: "12px 16px",
                  borderBottom: "1px solid #E5E7EB",
                }}
              >
                {latestPercents.map((item) => (
                  <div
                    key={item.name}
                    onClick={() => toggleSeries(item.name)}
                    title={
                      item.visible === false ? "Click to show" : "Click to hide"
                    }
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
                    <div style={{ fontSize: 13, color: "#374151" }}>
                      <span style={{ fontWeight: 600, color: "#111827" }}>
                        {item.percent}%
                      </span>{" "}
                      <span style={{ color: "#6B7280" }}>{item.name}</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
            {/* Chart or no data found message */}
            <div style={{ width: "100%", padding: "8px 0" }}>
              {options ? (
                <HighchartsReact highcharts={Highcharts} options={options} />
              ) : (
                <div
                  style={{
                    padding: 40,
                    textAlign: "center",
                    color: "#6B7280",
                    fontSize: 14,
                  }}
                >
                  No threat activity data found for the selected time period.
                </div>
              )}
            </div>
          </div>
        )
      }
    />
  );
}

export default ThreatCategoryStackedChart;
