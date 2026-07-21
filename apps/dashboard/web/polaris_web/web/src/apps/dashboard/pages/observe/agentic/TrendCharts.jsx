import React, { useMemo } from "react";
import { Box, VerticalStack, Text } from "@shopify/polaris";
import Highcharts from "highcharts";
import HighchartsReact from "highcharts-react-official";

const OS_SERIES = [
    { key: "mac",     name: "Mac",     color: "#7C3AED" },
    { key: "windows", name: "Windows", color: "#10B981" },
    { key: "linux",   name: "Linux",   color: "#F59E0B" },
];

const BROWSER_SERIES = [
    { key: "chrome",  name: "Chrome",  color: "#4285F4" },
    { key: "firefox", name: "Firefox", color: "#FF7139" },
    { key: "edge",    name: "Edge",    color: "#0078D7" },
    { key: "safari",  name: "Safari",  color: "#00B4D8" },
];

function makeTrendConfig(trends, monthLabels, seriesDefs) {
    const categories = monthLabels || [];
    const n = categories.length;
    const tickInterval = n > 14 ? Math.ceil(n / 12) : 1;
    const series = seriesDefs.map(({ key, name, color, trend }) => ({
        name, color, data: (trend || trends)[key] || new Array(n).fill(0),
    }));
    return {
        chart:{
            type:"areaspline", height:340, backgroundColor:"transparent",
            style:{fontFamily:"Inter, -apple-system, sans-serif"},
            margin:[8,8,72,44],
        },
        title:null, credits:{enabled:false}, exporting:{enabled:false},
        xAxis:{
            categories,
            labels:{ style:{fontSize:"11px",color:"#8C9196"}, step: tickInterval },
            lineColor:"#DFE3E8", tickColor:"transparent",
        },
        yAxis:{title:null,labels:{style:{fontSize:"11px",color:"#8C9196"}},gridLineColor:"#F1F2F3",allowDecimals:false,min:0},
        legend:{enabled:true,align:"left",verticalAlign:"bottom",layout:"horizontal",itemStyle:{fontSize:"12px",fontWeight:"400",color:"#6D7175"},symbolRadius:4,margin:8,y:8},
        tooltip:{shared:true,backgroundColor:"white",borderColor:"#DFE3E8",borderRadius:8,style:{fontSize:"12px"}},
        plotOptions:{ areaspline:{ marker:{enabled:false}, lineWidth:2, fillOpacity:0.08 }, series:{ connectNulls:true } },
        series,
    };
}

function TrendChartCard({ title, options }) {
    return (
        <Box padding="4">
            <VerticalStack gap="2">
                <Text variant="headingMd" fontWeight="semibold">{title}</Text>
                <HighchartsReact highcharts={Highcharts} options={options} />
            </VerticalStack>
        </Box>
    );
}

export function EndpointBrowserTrendChart({ osTrend = {}, browserTrend = {}, monthLabels = [] }) {
    const seriesDefs = useMemo(() => [
        ...OS_SERIES.map(s => ({ ...s, trend: osTrend })),
        ...BROWSER_SERIES.map(s => ({ ...s, trend: browserTrend })),
    ], [osTrend, browserTrend]);
    const options = useMemo(
        () => makeTrendConfig(null, monthLabels, seriesDefs),
        [seriesDefs, monthLabels]
    );
    return <TrendChartCard title="Endpoints & Browser Extensions Over Time" options={options} />;
}

export default TrendChartCard;
