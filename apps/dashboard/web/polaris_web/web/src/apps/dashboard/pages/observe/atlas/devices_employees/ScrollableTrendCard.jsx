import { useEffect, useRef } from "react";
import { Box, Card } from "@shopify/polaris";
import { EndpointBrowserTrendChart } from "../../agentic/TrendCharts";
import { TREND_MONTH_WIDTH } from "./peopleConstants";

function ScrollableTrendCard({ trend }) {
    const scrollRef = useRef(null);
    const monthLabels = trend?.monthLabels || [];
    const months = monthLabels.length;

    useEffect(() => {
        const element = scrollRef.current;
        if (element) element.scrollLeft = element.scrollWidth;
    }, [months]);

    return (
        <Card padding="0">
            <Box ref={scrollRef} overflowX="auto">
                <Box minWidth={`${months * TREND_MONTH_WIDTH}px`}>
                    <EndpointBrowserTrendChart
                        key={months}
                        osTrend={trend?.osTrend || {}}
                        browserTrend={trend?.browserTrend || {}}
                        monthLabels={monthLabels}
                    />
                </Box>
            </Box>
        </Card>
    );
}

export default ScrollableTrendCard;
