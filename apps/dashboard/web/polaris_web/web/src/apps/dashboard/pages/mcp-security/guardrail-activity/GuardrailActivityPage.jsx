import React, { useState, useEffect, useReducer } from 'react';
import DateRangeFilter from '../../../components/layouts/DateRangeFilter';
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards';
import TitleWithInfo from '../../../components/shared/TitleWithInfo';
import SusDataTable from '../../threat_detection/components/SusDataTable';
import values from '@/util/values';
import { produce } from 'immer';
import func from '@/util/func';
import { HorizontalGrid, VerticalStack as PolarisVerticalStack } from '@shopify/polaris';
import TopThreatTypeChart from '../../threat_detection/components/TopThreatTypeChart';
import InfoCard from '../../dashboard/new_components/InfoCard';
import BarGraph from '../../../components/charts/BarGraph';
import { getDashboardCategory, mapLabel } from '../../../../main/labelHelper';

const convertToGraphData = (severityMap) => {
    let dataArr = []
    Object.keys(severityMap).forEach((x) => {
        const color = func.getHexColorForSeverity(x)
        let text = func.toSentenceCase(x)
        const value = severityMap[x]
        dataArr.push({
            text, value, color
        })
    })
    return dataArr
}

const ChartComponent = ({ subCategoryCount, severityCountMap }) => {
    return (
        <PolarisVerticalStack gap={4} columns={2}>
            <HorizontalGrid gap={4} columns={2}>
                <TopThreatTypeChart
                    key={"top-threat-types"}
                    data={subCategoryCount}
                />
                <InfoCard
                    title={"Threats by severity"}
                    titleToolTip={`Number of ${mapLabel("APIs", getDashboardCategory())} per each category`}
                    component={
                        <BarGraph
                            data={convertToGraphData(severityCountMap)}
                            areaFillHex="true"
                        />
                    }
                />
            </HorizontalGrid>
        </PolarisVerticalStack>
    );
};

function GuardrailActivityPage({ showCharts = false, showTable = true }) {
    // Date range state
    const initialVal = values.ranges[3];
    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        initialVal
    );

    // Chart data state
    const [subCategoryCount, setSubCategoryCount] = useState([]);
    const [severityCountMap, setSeverityCountMap] = useState({});

    useEffect(() => {
        // You can add any initialization logic here
        // For now, we'll use empty data for charts
        setSubCategoryCount([]);
        setSeverityCountMap({Critical: 0, High: 0, Medium: 0, Low: 0});
    }, []);

    const handleRowClick = (data) => {
        // Handle row click if needed
        console.log('Row clicked:', data);
    };

    // Build components array based on boolean parameters
    const components = [];
    
    if (showCharts) {
        components.push(
            <ChartComponent
                key={"guardrail-activity-chart-component"}
                subCategoryCount={subCategoryCount}
                severityCountMap={severityCountMap}
            />
        );
    }
    
    if (showTable) {
        components.push(
            <SusDataTable
                key={"guardrail-activity-data-table"}
                currDateRange={currDateRange}
                rowClicked={handleRowClick}
            />
        );
    }

    return (
        <PageWithMultipleCards
            title={<TitleWithInfo titleText={"Guardrail Activity"} />}
            isFirstPage={true}
            primaryAction={
                <DateRangeFilter
                    initialDispatch={currDateRange}
                    dispatch={(dateObj) =>
                        dispatchCurrDateRange({
                            type: "update",
                            period: dateObj.period,
                            title: dateObj.title,
                            alias: dateObj.alias,
                        })
                    }
                />
            }
            components={components}
        />
    );
}

export default GuardrailActivityPage;
