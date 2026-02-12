import React from 'react'
import { Box, VerticalStack, Text } from '@shopify/polaris'
import DonutChart from './DonutChart'
import BarGraph from '../charts/BarGraph'
import LineChart from '../charts/LineChart'

export const ChartRenderer = ({ chartType, data }) => {
    try {
        const chartData = typeof data === 'string' ? JSON.parse(data) : data;

        // Render Pie/Donut Chart
        if (chartType === 'pie' || chartType === 'donut') {
            const formattedData = chartData.data || {};

            return (
                <Box style={{ display: 'flex', justifyContent: 'center' }}>
                    <DonutChart
                        data={formattedData}
                        title={chartData.title || ''}
                        size={200}
                        pieInnerSize={"70%"}
                        subtitle={chartData.subtitle}
                        type={chartType}
                    />
                </Box>
            );
        }

        // Render Bar Chart
        if (chartType === 'bar' || chartType === 'column') {
            let formattedData = [];

            // Check if data is in categories + series format (from markdown)
            if (chartData.categories && chartData.series && Array.isArray(chartData.series)) {
                const colors = ['#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFEAA7', '#DFE6E9'];

                // Take the first series (BarGraph only supports single series)
                const firstSeries = chartData.series[0];

                if (firstSeries && Array.isArray(firstSeries.data)) {
                    formattedData = chartData.categories.map((category, index) => ({
                        text: category,
                        value: firstSeries.data[index] || 0,
                        color: colors[index % colors.length]
                    }));
                }
            } else if (Array.isArray(chartData.data)) {
                // Data is already in correct format
                formattedData = chartData.data;
            }

            return (
                <BarGraph
                    title={chartData.title || ''}
                    data={formattedData}
                    height={chartData.height || 300}
                    xAxisTitle={chartData.xAxisTitle || ''}
                    yAxisTitle={chartData.yAxisTitle || ''}
                    barWidth={chartData.barWidth}
                    barGap={chartData.barGap}
                    showGridLines={chartData.showGridLines !== false}
                    showYAxis={chartData.showYAxis !== false}
                    backgroundColor={chartData.backgroundColor || 'white'}
                    defaultChartOptions={chartData.defaultChartOptions || {}}
                />
            );
        }

        // Render Line Chart
        if (chartType === 'line' || chartType === 'area') {
            let formattedData = [];

            // Check if data is in categories + series format (from markdown)
            if (chartData.categories && chartData.series && Array.isArray(chartData.series)) {
                const colors = ['#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFEAA7'];

                // Parse date strings to timestamps
                const parseDate = (dateStr) => {
                    // Handle formats like "Jul 2024", "Oct 2024", etc.
                    const months = {
                        'Jan': 0, 'Feb': 1, 'Mar': 2, 'Apr': 3, 'May': 4, 'Jun': 5,
                        'Jul': 6, 'Aug': 7, 'Sep': 8, 'Oct': 9, 'Nov': 10, 'Dec': 11
                    };

                    const parts = dateStr.trim().split(' ');
                    if (parts.length === 2) {
                        const month = months[parts[0]];
                        const year = parseInt(parts[1]);
                        if (month !== undefined && !isNaN(year)) {
                            return new Date(year, month, 1).getTime();
                        }
                    }

                    // Fallback: try to parse as date
                    const timestamp = Date.parse(dateStr);
                    return isNaN(timestamp) ? null : timestamp;
                };

                // Convert timestamps from categories
                const timestamps = chartData.categories.map(parseDate);

                formattedData = chartData.series.map((series, seriesIndex) => {
                    const dataPoints = series.data.map((value, index) => {
                        const timestamp = timestamps[index];
                        // If we have a valid timestamp, use it; otherwise use index-based timestamp
                        const xValue = timestamp !== null ? timestamp : Date.now() + (index * 86400000);
                        return [xValue, value];
                    });

                    return {
                        data: dataPoints,
                        color: series.color || colors[seriesIndex % colors.length],
                        name: series.name || `Series ${seriesIndex + 1}`
                    };
                });
            } else if (Array.isArray(chartData.data)) {
                // Data is already in correct format
                formattedData = chartData.data;
            }

            return (
                <Box paddingBlockStart="500" paddingBlockEnd="500">
                    <LineChart
                        title={chartData.title || ''}
                        data={formattedData}
                        height={chartData.height || 300}
                        yAxisTitle={chartData.yAxisTitle || ''}
                        type={chartType}
                        backgroundColor={chartData.backgroundColor || 'white'}
                        color={chartData.color}
                        width={chartData.width}
                        text={true}
                        showGridLines={chartData.showGridLines !== false}
                        defaultChartOptions={chartData.defaultChartOptions || {}}
                        areaFillHex={chartType === 'area'}
                    />
                </Box>
            );
        }

        // Fallback if chart type not recognized
        return (
            <Box paddingBlockStart="500" paddingBlockEnd="500">
                <Box background="bg-surface-secondary" padding="400" borderRadius="200">
                    <VerticalStack gap="300">
                        <Text as="p" tone="subdued">
                            Unsupported chart type: <Text as="span" fontWeight="bold">{chartType}</Text>
                        </Text>
                        <Box
                            as="pre"
                            style={{
                                margin: 0,
                                fontSize: '12px',
                                overflow: 'auto',
                                fontFamily: 'monospace'
                            }}
                        >
                            {JSON.stringify(chartData, null, 2)}
                        </Box>
                    </VerticalStack>
                </Box>
            </Box>
        );
    } catch (error) {
        console.error('Error rendering chart:', error);
        return (
            <Box paddingBlockStart="500" paddingBlockEnd="500">
                <Box background="bg-surface-critical" padding="400" borderRadius="200">
                    <VerticalStack gap="300">
                        <Text as="p" tone="critical">
                            <Text as="span" fontWeight="bold">Chart Rendering Error:</Text> {error.message}
                        </Text>
                        <Box
                            as="pre"
                            style={{
                                margin: 0,
                                fontSize: '12px',
                                overflow: 'auto',
                                fontFamily: 'monospace',
                                color: '#666'
                            }}
                        >
                            {data}
                        </Box>
                    </VerticalStack>
                </Box>
            </Box>
        );
    }
};

export default ChartRenderer;
