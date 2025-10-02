import { Box, Text, HorizontalStack, DataTable, VerticalStack } from '@shopify/polaris';
import ChartypeComponent from './ChartypeComponent';
import observeFunc from "../../observe/transform";
import InfoCard from '../../dashboard/new_components/InfoCard';
import { CATEGORY_AGENTIC_SECURITY, CATEGORY_API_SECURITY, CATEGORY_GEN_AI, CATEGORY_MCP_SECURITY, getDashboardCategory, mapLabel } from '../../../../main/labelHelper';
import { mcpCategoryTestData, genAICategoryTestData, apiCategoryTestData } from './dummyData';
import api from '../api';
import { useState, useEffect } from 'react';
import func from "@/util/func";
import LocalStore from "../../../../main/LocalStorageStore";

function CategoryWiseScoreGraph({ 
    startTimestamp, 
    endTimestamp, 
    dataSource = 'redteaming', // 'redteaming', 'threat_detection', 'guardrails'
    title,
    apiEndpoint,
    fallbackData
}) {
    const [categoryTestData, setCategoryTestData] = useState([]);
    const [loading, setLoading] = useState(true);

    // Get current dashboard category
    const dashboardCategory = getDashboardCategory();
    
    // Get category map from local storage
    const categoryMap = LocalStore.getState().categoryMap;
    
    // Determine the appropriate API call and fallback data
    const getApiCall = () => {
        if (apiEndpoint) {
            return apiEndpoint;
        }
        
        switch (dataSource) {
            case 'redteaming':
                return api.fetchCategoryWiseScores;
            case 'threat_detection':
                // Add threat detection API call when available
                return null;
            case 'guardrails':
                // Add guardrails API call when available
                return null;
            default:
                return api.fetchCategoryWiseScores;
        }
    };

    const getDefaultFallbackData = () => {
        if (fallbackData) {
            return fallbackData;
        }
        
        switch (dashboardCategory) {
            case CATEGORY_MCP_SECURITY:
                return mcpCategoryTestData;
            case CATEGORY_GEN_AI:
                return genAICategoryTestData;
            case CATEGORY_AGENTIC_SECURITY:
                return [...mcpCategoryTestData, ...genAICategoryTestData]
            case CATEGORY_API_SECURITY:
            default:
                return apiCategoryTestData || [];
        }
    };

    useEffect(() => {
        const fetchCategoryData = async () => {
            try {
                setLoading(true);
                
                // For demo accounts, always use demo data and skip API calls
                if (func.isDemoAccount()) {
                    setCategoryTestData(getDefaultFallbackData());
                    return;
                }
                
                // For non-demo accounts, try to fetch real data
                const apiCall = getApiCall();
                
                if (apiCall) {
                    const response = await apiCall(startTimestamp, endTimestamp, dashboardCategory, dataSource);
                    if (response && response.length > 0) {
                        setCategoryTestData(response);
                        return;
                    }
                }
                
                // If no data is available, show empty state (no fallback to dummy data)
                setCategoryTestData([]);
                
            } catch (error) {
                console.error('Error fetching category wise data:', error);
                // On error, show empty state for non-demo accounts
                setCategoryTestData([]);
            } finally {
                setLoading(false);
            }
        };

        fetchCategoryData();
    }, [startTimestamp, endTimestamp, dataSource, apiEndpoint, dashboardCategory]);

    // Get dynamic title and labels based on data source and dashboard category
    const getTitle = () => {
        if (title) return title;
        
        const baseTitle = "Category wise scores";
        return mapLabel(baseTitle, dashboardCategory);
    };

    const getTooltip = () => {
        const context = dataSource === 'redteaming' ? 'results' : 
                       dataSource === 'threat_detection' ? 'threat detection results' :
                       dataSource === 'guardrails' ? 'policy enforcement results' : 'results';
        
        return `Comprehensive breakdown of ${context} by category, showing pass/fail percentages, counts, and skipped items with visual indicators.`;
    };

    const getChartSubtitle = () => {
        switch (dataSource) {
            case 'threat_detection':
                return mapLabel('Total threats', dashboardCategory);
            case 'guardrails':
                return mapLabel('Total policies', dashboardCategory);
            case 'redteaming':
            default:
                return mapLabel('Total tests', dashboardCategory);
        }
    };

    if (loading) {
        return (
            <InfoCard
                title={getTitle()}
                titleToolTip={getTooltip()}
                component={<Box padding="4"><Text>Loading...</Text></Box>}
            />
        );
    }

    // If no data available, don't show the component for non-demo accounts
    if (!func.isDemoAccount() && (!categoryTestData || categoryTestData.length === 0)) {
        return null;
    }
    
    // Calculate totals (skipped metric commented out for now)
    const totalPass = categoryTestData.reduce((sum, cat) => sum + (cat.pass || 0), 0);
    const totalFail = categoryTestData.reduce((sum, cat) => sum + (cat.fail || 0), 0);
    // const totalSkip = categoryTestData.reduce((sum, cat) => sum + (cat.skip || 0), 0);
    const total = totalPass + totalFail; // + totalSkip;
    
    // Prepare data for ChartypeComponent with dynamic labels based on data source
    const getStatusLabels = () => {
        switch (dataSource) {
            case 'redteaming':
                return { 
                    pass: 'Passed', 
                    fail: 'Failed', 
                    skip: 'Skipped',
                    hasSkip: true 
                };
            case 'threat_detection':
                return { 
                    pass: 'Safe', 
                    fail: 'Threat', 
                    skip: 'Skipped',
                    hasSkip: false 
                };
            case 'guardrails':
                return { 
                    pass: 'Allowed', 
                    fail: 'Blocked', 
                    skip: 'Skipped',
                    hasSkip: false 
                };
            default:
                return { 
                    pass: 'Passed', 
                    fail: 'Failed', 
                    skip: 'Skipped',
                    hasSkip: true 
                };
        }
    };

    const statusLabels = getStatusLabels();
    
    // Build chart data - only include skip if it exists and has data
    const chartData = {
        [statusLabels.pass]: {
            text: totalPass,
            color: '#54b074',
        },
        [statusLabels.fail]: {
            text: totalFail,
            color: '#f05352'
        }
    };

    // Skip metric commented out for now
    // if (statusLabels.hasSkip && totalSkip > 0) {
    //     chartData[statusLabels.skip] = {
    //         text: totalSkip,
    //         color: '#ffa500'
    //     };
    // }

    // Prepare table rows for category breakdown with comprehensive stats
    const tableRows = categoryTestData.map(cat => {
        const passCount = cat.pass || 0;
        const failCount = cat.fail || 0;
        // const skipCount = cat.skip || 0; // Commented out for now
        const executedTotal = passCount + failCount; // Only executed tests
        // const grandTotal = executedTotal + skipCount; // All tests
        
        // Calculate percentages based on executed tests only
        const passRate = executedTotal === 0 ? 0 : Number(((passCount / executedTotal) * 100).toFixed(1));
        const failRate = executedTotal === 0 ? 0 : Number(((failCount / executedTotal) * 100).toFixed(1));
        
        // Determine dominant result (>= for tie-breaking towards pass)
        const passed = passCount >= failCount;
        const primaryColor = passed ? '#54b074' : '#f05352';
        const primaryRate = passed ? passRate : failRate;
        const primaryLabel = passed ? statusLabels.pass : statusLabels.fail;

        return [
            (categoryMap && categoryMap[cat.categoryName]?.displayName) || cat.categoryName,
            <VerticalStack gap="2">
                {/* Main result indicator */}
                <HorizontalStack gap="1" align="center">
                    <div style={{ 
                        width: '8px', 
                        height: '8px', 
                        borderRadius: '50%', 
                        backgroundColor: primaryColor 
                    }} />
                    <Text>
                        <span style={{ color: primaryColor, fontWeight: '600' }}>
                            {executedTotal === 0 ? '0%' : `${primaryRate}%`} {executedTotal === 0 ? 'No data' : primaryLabel}
                        </span>
                    </Text>
                    <Text variant="bodySm" color='subdued'>
                        <span style={{ color: '#54b074', fontWeight: '500' }}>✓{passCount}</span>
                    </Text>
                    <Text variant="bodySm" color='subdued'>
                        <span style={{ color: '#f05352', fontWeight: '500' }}>✗{failCount}</span>
                    </Text>
                </HorizontalStack>
            </VerticalStack>
        ];
    });

    return (
        <InfoCard
            title={getTitle()}
            titleToolTip={getTooltip()}
            component={<HorizontalStack gap="8" align="center" wrap={false}>
                {/* Chart and Legend using ChartypeComponent */}
                <Box>
                    <ChartypeComponent
                        data={chartData}
                        title=""
                        charTitle={observeFunc.formatNumberWithCommas(total)}
                        chartSubtitle={getChartSubtitle()}
                        reverse={false}
                        isNormal={true}
                        boxHeight="250px"
                        chartOnLeft={true}
                        dataTableWidth="250px"
                        boxPadding={0}
                        pieInnerSize="65%"
                        chartSize={240}
                    />
                </Box>

                {/* Category Table with Scroll */}
                <Box>
                    <div style={{ 
                        maxHeight: '300px', 
                        overflowY: 'auto',
                        border: '1px solid #e1e3e5',
                        borderRadius: '6px'
                    }}>
                        <DataTable
                            columnContentTypes={[
                                'text',
                                'text'
                            ]}
                            headings={[]}
                            rows={tableRows}
                            increasedTableDensity
                            hoverable={false}
                        />
                    </div>
                </Box>
            </HorizontalStack>}
        />
    );
}

export default CategoryWiseScoreGraph;