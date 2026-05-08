import React, { useState, useEffect } from 'react';
import { HighchartsReact } from "highcharts-react-official";
import Highcharts from "highcharts";
import InfoCard from "../../pages/dashboard/new_components/InfoCard";
import { Spinner } from '@shopify/polaris';
import dayjs from 'dayjs';

const P95LatencyGraph = ({ title, subtitle, dataType = 'mcp-security', startTimestamp, endTimestamp, onLatencyClick, latencyData }) => {
    const [seriesData, setSeriesData] = useState([]);
    const [loading, setLoading] = useState(false);
    const [sortedTimelines, setSortedTimelines] = useState([]);

    const COLORMAP = {
        'incoming': 'rgb(255, 107, 107)', // Red
        'output': 'rgb(78, 205, 196)',   // Teal  
        'total': 'rgb(69, 183, 209)'     // Blue
    }


    const fetchLatencyData = async () => {
        setLoading(true);
        
        if (latencyData && latencyData.length > 0) {
            // Use provided latency data from threat page
            setSortedTimelines(latencyData);
            
            const series = [
                {
                    color: COLORMAP.incoming,
                    name: 'Incoming Request P95',
                    data: latencyData.map(item => item.incomingRequestP95),
                    events: {
                        click: (e) => {
                            if (onLatencyClick) onLatencyClick('incoming');
                        }
                    }
                },
                {
                    color: COLORMAP.output,
                    name: 'Output Result P95', 
                    data: latencyData.map(item => item.outputResultP95),
                    events: {
                        click: (e) => {
                            if (onLatencyClick) onLatencyClick('output');
                        }
                    }
                },
                {
                    color: COLORMAP.total,
                    name: 'Total Latency P95',
                    data: latencyData.map(item => item.totalP95),
                    events: {
                        click: (e) => {
                            if (onLatencyClick) onLatencyClick('total');
                        }
                    }
                }
            ];
            
            setSeriesData(series);
        } else {
            // No data provided, show empty state
            setSortedTimelines([]);
            setSeriesData([]);
        }
        
        setLoading(false);
    };

    useEffect(() => {
        fetchLatencyData();
    }, [startTimestamp, endTimestamp, dataType, latencyData]);

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
            categories: sortedTimelines.map(item => dayjs(item.timestamp * 1000).format('D MMM')),
            labels: {
                step: Math.max(1, Math.floor(sortedTimelines.length / 8)), // Show ~8 labels max
                rotation: -45
            }
        },
        yAxis: {
            title: {
                text: "Latency (ms)"
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
                const timestamp = dayjs(this.x).format('MMM DD, HH:mm');
                let tooltip = `<b>${timestamp}</b><br/>`;
                this.points.forEach(point => {
                    tooltip += `<span style="color:${point.color}">●</span> ${point.series.name}: <b>${point.y}ms</b><br/>`;
                });
                return tooltip;
            },
            shared: true
        },
        series: seriesData,
    }

    if (loading) {
        return <Spinner />;
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
