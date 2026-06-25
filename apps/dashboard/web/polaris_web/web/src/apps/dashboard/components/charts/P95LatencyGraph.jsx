import React, { useState, useEffect } from 'react';
import { HighchartsReact } from "highcharts-react-official";
import Highcharts from "highcharts";
import InfoCard from "../../pages/dashboard/new_components/InfoCard";
import dayjs from 'dayjs';

const COLORMAP = {
    incoming: 'rgb(255, 107, 107)',
    output: 'rgb(78, 205, 196)',
    total: 'rgb(69, 183, 209)',
};

const P95LatencyGraph = ({ title, subtitle, dataType = 'mcp-security', startTimestamp, endTimestamp, onLatencyClick, latencyData }) => {
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
            // Note: no combined "total" series — request and response are separate
            // events, so P95(request) + P95(response) is not a real percentile.
        ];

        setSeriesData(series.filter(s => s.data.some(v => v > 0)));
    }, [startTimestamp, endTimestamp, dataType, latencyData, onLatencyClick]);

    const chartOptions = {
        chart: {
            type: "spline",
            height: 350
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

    return (
        <InfoCard
            title={title || "P95 Latency Metrics"}
            titleToolTip={subtitle || "95th percentile latency measurements over time"}
            component={<HighchartsReact highcharts={Highcharts} options={chartOptions} />}
        />
    )
};

export default P95LatencyGraph;
