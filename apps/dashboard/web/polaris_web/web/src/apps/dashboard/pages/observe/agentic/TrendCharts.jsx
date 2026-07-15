import React from "react";
import { Box, VerticalStack, Text } from "@shopify/polaris";
import Highcharts from "highcharts";
import HighchartsReact from "highcharts-react-official";

function makeOsTrendConfig(osTrend, monthLabels) {
    const categories = monthLabels || [];
    const n = categories.length;
    const tickInterval = n > 14 ? Math.ceil(n / 12) : 1;
    const series = [
        {name:"Mac",     data:osTrend.mac     || new Array(n).fill(0), color:"#7C3AED"},
        {name:"Windows", data:osTrend.windows || new Array(n).fill(0), color:"#10B981"},
        {name:"Linux",   data:osTrend.linux   || new Array(n).fill(0), color:"#F59E0B"},
    ];
    return {
        chart:{
            type:"areaspline", height:176, backgroundColor:"transparent",
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

function makeBrowserTrendConfig(browserTrend, monthLabels) {
    const categories = monthLabels || [];
    const n = categories.length;
    const tickInterval = n > 14 ? Math.ceil(n / 12) : 1;
    const series = [
        {name:"Chrome",  data:browserTrend.chrome  || new Array(n).fill(0), color:"#4285F4"},
        {name:"Firefox", data:browserTrend.firefox || new Array(n).fill(0), color:"#FF7139"},
        {name:"Edge",    data:browserTrend.edge    || new Array(n).fill(0), color:"#0078D7"},
        {name:"Safari",  data:browserTrend.safari  || new Array(n).fill(0), color:"#00B4D8"},
    ];
    return {
        chart:{
            type:"areaspline", height:176, backgroundColor:"transparent",
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

export function OsTrendChart({ osTrend = {}, monthLabels = [] }) {
    const options = React.useMemo(
        () => makeOsTrendConfig(osTrend, monthLabels),
        [osTrend, monthLabels]
    );
    return <TrendChartCard title="Endpoints Over Time by OS Type" options={options} />;
}

export function BrowserTrendChart({ browserTrend = {}, monthLabels = [] }) {
    const options = React.useMemo(
        () => makeBrowserTrendConfig(browserTrend, monthLabels),
        [browserTrend, monthLabels]
    );
    return <TrendChartCard title="Browser Extensions Over Time by Browser Type" options={options} />;
}

export default TrendChartCard;
