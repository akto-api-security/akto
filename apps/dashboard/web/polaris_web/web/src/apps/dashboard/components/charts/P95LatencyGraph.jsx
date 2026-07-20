import React, { useState, useEffect } from 'react';
import { Box, Card, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import { HighchartsReact } from "highcharts-react-official";
import Highcharts from "highcharts";
import InfoTooltipIcon from "@/apps/dashboard/components/shared/InfoTooltipIcon";
import dayjs from 'dayjs';

const COLORMAP = {
    incoming: 'rgb(255, 107, 107)',
    output: 'rgb(78, 205, 196)',
    total: 'rgb(69, 183, 209)',
};

const P95LatencyGraph = ({ title, subtitle, dataType = 'mcp-security', startTimestamp, endTimestamp, onLatencyClick, latencyData, height = 350 }) => {
    const [seriesData, setSeriesData] = useState([]);
    const [sortedTimelines, setSortedTimelines] = useState([]);

    useEffect(() => {
        if (!latencyData || latencyData.length === 0) {
            setSortedTimelines([]);
            setSeriesData([]);
            return;
        }

        setSortedTimelines(latencyData);

        const series = [
            {
                color: COLORMAP.incoming,
                name: 'Incoming Request P95',
                data: latencyData.map(item => item.incomingRequestP95),
                events: { click: () => { if (onLatencyClick) onLatencyClick('incoming'); } }
            },
            {
                color: COLORMAP.output,
                name: 'Output Result P95',
                data: latencyData.map(item => item.outputResultP95),
                events: { click: () => { if (onLatencyClick) onLatencyClick('output'); } }
            },
            {
                color: COLORMAP.total,
                name: 'Total Latency P95',
                data: latencyData.map(item => item.totalP95),
                events: { click: () => { if (onLatencyClick) onLatencyClick('total'); } }
            },
        ];

        setSeriesData(series.filter(s => s.data.some(v => v > 0)));
    }, [startTimestamp, endTimestamp, dataType, latencyData, onLatencyClick]);

    const chartOptions = {
        chart: {
            type: "spline",
            height
        },
        credits: {
            enabled: false
        },
        title: {
            text: undefined,
        },
        exporting: {
            enabled: false
        },
        xAxis: {
            title: {
                text: "Timeline"
            },
            categories: sortedTimelines.map(item => dayjs(item.timestamp * 1000).format('D MMM HH:mm')),
            labels: {
                step: Math.max(1, Math.floor(sortedTimelines.length / 8)),
                rotation: -45
            }
        },
        yAxis: {
            title: {
                text: "Latency (ms)"
            },
            labels: {
                formatter: function() {
                    return this.value;
                }
            }
        },
        plotOptions: {
            spline: {
                marker: {
                    enabled: false
                },
                lineWidth: 3
            }
        },
        legend: {
            enabled: true,
            align: 'right',
            verticalAlign: 'top',
            layout: 'vertical',
            itemStyle: {
                fontSize: '12px'
            }
        },
        tooltip: {
            formatter: function() {
                let tooltip = '<b>' + this.x + '</b><br/>';
                this.points.forEach(function(point) {
                    tooltip += '<span style="color:' + point.color + '">&#9679;</span> ' + point.series.name + ': <b>' + Math.round(point.y) + 'ms</b><br/>';
                });
                return tooltip;
            },
            shared: true
        },
        series: seriesData,
    }

    const hasData = latencyData && latencyData.length > 0;

    return (
        <Card padding="5">
            <VerticalStack gap="4">
                <HorizontalStack gap="1" blockAlign="center">
                    <Text variant="headingMd">{title || "P95 Latency Metrics"}</Text>
                    <InfoTooltipIcon content={subtitle || "95th percentile latency measurements over time"} />
                </HorizontalStack>
                {hasData ? (
                    <HighchartsReact highcharts={Highcharts} options={chartOptions} />
                ) : (
                    <Box padding="8" minHeight={`${height}px`}>
                        <Text variant="bodySm" color="subdued" alignment="center">No latency data available for the selected time period.</Text>
                    </Box>
                )}
            </VerticalStack>
        </Card>
    )
};

export default P95LatencyGraph;
