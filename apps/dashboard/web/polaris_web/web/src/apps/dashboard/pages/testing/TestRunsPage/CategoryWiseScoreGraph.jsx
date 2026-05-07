import { Box, Text, DataTable, VerticalStack, HorizontalGrid } from '@shopify/polaris';
import ChartypeComponent from './ChartypeComponent';
import observeFunc from "../../observe/transform";
import InfoCard from '../../dashboard/new_components/InfoCard';
import { CATEGORY_AGENTIC_SECURITY, CATEGORY_API_SECURITY, CATEGORY_GEN_AI, CATEGORY_MCP_SECURITY, getDashboardCategory, mapLabel } from '../../../../main/labelHelper';
import { mcpCategoryTestData, genAICategoryTestData, apiCategoryTestData } from './dummyData';
import api from '../api';
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import func from "@/util/func";
import LocalStore from "../../../../main/LocalStorageStore";
import PersistStore from "../../../../main/PersistStore";
import TitleWithInfo from '../../../components/shared/TitleWithInfo';

function CategoryWiseScoreGraph({ 
    startTimestamp, 
    endTimestamp, 
    dataSource = 'redteaming', // 'redteaming', 'threat_detection', 'guardrails'
    title,
    apiEndpoint,
    fallbackData,
    apiCollectionIds,
}) {
    const [categoryTestData, setCategoryTestData] = useState([]);
    const [loading, setLoading] = useState(true);
    const navigate = useNavigate();

    // Get current dashboard category
    const dashboardCategory = getDashboardCategory();
    
    // Get category map from local storage
    const subcategoryMap = LocalStore.getState().subCategoryMap;
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
                    const response = await apiCall(startTimestamp, endTimestamp, dashboardCategory, dataSource, apiCollectionIds);
                    if (response && response.length > 0) {
                        setCategoryTestData(response);
                        return;
                    }
                }
                
                // If no data is available, show empty state (no fallback to dummy data)
                setCategoryTestData([]);
                
            } catch (error) {
                // On error, show empty state for non-demo accounts
                setCategoryTestData([]);
            } finally {
                setLoading(false);
            }
        };

        fetchCategoryData();
    }, [startTimestamp, endTimestamp, dataSource, apiEndpoint, dashboardCategory, apiCollectionIds]);

    // Get dynamic title and labels based on data source and dashboard category
    const getTitle = () => {
        if (title) return title;
        
        const baseTitle = "Category wise scores";
        return mapLabel(baseTitle, dashboardCategory);
    };

    const getTooltip = () => {
        if (dataSource === 'redteaming') {
            return 'Breakdown by category for the latest test result summary of each testing run scheduled in the selected time range (pass/fail counts and skipped items). Older reruns for the same run are excluded.';
        }
        const context = dataSource === 'threat_detection' ? 'threat detection results' :
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

    const handleCategoryRowClick = (categoryName) => {
        if (dataSource !== 'redteaming') return;
        const pageKey = '/dashboard/reports/issues/#open';
        const prev = PersistStore.getState().filtersMap;
        let nextFilters = (prev[pageKey]?.filters || []).filter(f => f.key !== 'issueCategory');
        nextFilters.push({ key: 'issueCategory', value: [categoryName] });
        PersistStore.getState().setFiltersMap({ ...prev, [pageKey]: { filters: nextFilters, sort: prev[pageKey]?.sort || [] } });
        const params = new URLSearchParams();
        if (startTimestamp > 0 && endTimestamp > 0) {
            params.set('since', String(startTimestamp));
            params.set('until', String(endTimestamp));
            params.set('range', 'custom');
        }
        const query = params.toString();
        navigate(query ? `/dashboard/reports/issues?${query}#open` : '/dashboard/reports/issues#open');
    };

    const handlePieSegmentClick = (segmentName) => {
        if (dataSource !== 'redteaming') {
            return;
        }
        const pageKey = '/dashboard/reports/issues/#open';
        const prev = PersistStore.getState().filtersMap;
        let nextFilters = (prev[pageKey]?.filters || []).filter((f) => f.key !== 'apiCollectionId');
        if (apiCollectionIds && apiCollectionIds.length > 0) {
            nextFilters = [...nextFilters, { key: 'apiCollectionId', value: apiCollectionIds }];
        }
        PersistStore.getState().setFiltersMap({
            ...prev,
            [pageKey]: { filters: nextFilters, sort: prev[pageKey]?.sort || [] },
        });
        const params = new URLSearchParams();
        if (startTimestamp > 0 && endTimestamp > 0) {
            params.set('since', String(startTimestamp));
            params.set('until', String(endTimestamp));
            params.set('range', 'custom');
        }
        const query = params.toString();
        navigate(query ? `/dashboard/reports/issues?${query}#open` : '/dashboard/reports/issues#open');
        if (segmentName === statusLabels.fail) {
            func.setToast(true, false, 'Open issues for this period (failed tests). Scroll the issues table to review.');
        }
    };
    
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

    let finalMap = {}
    categoryTestData.forEach(cat => {
        const storeId = cat.categoryName
        if(!subcategoryMap){
           return;
        }
        if(!subcategoryMap[storeId]) {
            return;
        }
        const categoryName = subcategoryMap[storeId]?.superCategory?.name;
        if(!categoryMap[categoryName]) {
            return;
        }
        const displayName = subcategoryMap[storeId]?.superCategory?.displayName;
        if (!finalMap[categoryName]) {
            finalMap[categoryName] = {
                displayName: displayName,
                categoryName: categoryName,
                pass: cat.pass,
                fail: cat.fail,
                skip: cat.skip,
            }
        } else {
            finalMap[categoryName].pass += cat.pass;
            finalMap[categoryName].fail += cat.fail;
            finalMap[categoryName].skip += cat.skip;
        }
    });

    const finalTestCategoryData = Object.values(finalMap);

    // Prepare table rows for category breakdown with comprehensive stats
    const tableRows = finalTestCategoryData.sort((a,b) => b.fail - a.fail).map(cat => {
        const passCount = cat.pass || 0;
        const failCount = cat.fail || 0;
        // const skipCount = cat.skip || 0; // Commented out for now
        const executedTotal = passCount + failCount; // Only executed tests
        // const grandTotal = executedTotal + skipCount; // All tests
        
        // Determine dominant result (>= for tie-breaking towards pass)
        const passed = passCount >= failCount;
        const primaryColor = passed ? '#54b074' : '#f05352';
        const primaryRate = passed
            ? func.formatSplitSharePercent(passCount, executedTotal, failCount)
            : func.formatSplitSharePercent(failCount, executedTotal, passCount);
        const primaryLabel = passed ? statusLabels.pass : statusLabels.fail;

        const displayName = cat.displayName;
        return [
            <span
                style={{ cursor: dataSource === 'redteaming' && failCount > 0 ? 'pointer' : undefined }}
                onClick={dataSource === 'redteaming' && failCount > 0 ? () => handleCategoryRowClick(cat.categoryName) : undefined}
            >{displayName}</span>,
            <VerticalStack gap="2">
                {/* Main result indicator */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
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
                </div>
            </VerticalStack>
        ];
    });

    return (
        <VerticalStack gap={"4"} align='center'>
            <Box padding={"3"}>
            <TitleWithInfo
                titleText={getTitle()}
                tooltipContent={getTooltip()}
                textProps={{variant: 'headingMd'}}
            />
            <HorizontalGrid columns={2} gap={4} alignItems='center'>
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
                        onSegmentClick={dataSource === 'redteaming' ? handlePieSegmentClick : undefined}
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
            </HorizontalGrid>
            </Box>
        </VerticalStack>
    )
}

export default CategoryWiseScoreGraph;