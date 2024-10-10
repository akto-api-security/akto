import { HorizontalGrid, Link, Text } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import EmptyCard from '../../dashboard/new_components/EmptyCard'
import StackedChart from '../../../components/charts/StackedChart'
import InfoCard from '../../dashboard/new_components/InfoCard'
import dashboardApi from "../../dashboard/api.js"
import testingApi from "../../testing/api.js"
import testingFunc from "../../testing/transform.js"
import func from "@/util/func";

const CriticalIssuesDetailGraph = () => {
    const [unsecuredAPIs, setUnsecuredAPIs] = useState([])
    const [criticalFindingsData, setCriticalFindingsData] = useState([])
    const [showTestingComponents, setShowTestingComponents] = useState(false)

    function convertSubCategoryInfo(tempSubCategoryMap) {
        const entries = Object.values(tempSubCategoryMap);
        entries.sort((a, b) => b.text - a.text);
        const topEntries = entries.slice(0, 5);
        const data = topEntries.map(entry => [entry.filterKey, entry.text]);
        setCriticalFindingsData([{ "data": data, "color": "#E45357", name: "Critical Issues" }])
    }

    function buildUnsecuredAPIs(input) {
        const CRITICAL_COLOR = "#E45357"
        const transformed = []
        const criticalData = { data: [], color: CRITICAL_COLOR, name: "Critical Issues" }
        for (const epoch in input) {
            const epochMillis = Number(epoch) * 86400000
            criticalData.data.push([epochMillis, input[epoch]])
        }
        transformed.push(criticalData)

        setUnsecuredAPIs(transformed)
    }

    const fetchGraphData = async () => {
        setShowTestingComponents(false)
        const subcategoryDataResp = await testingApi.getSummaryInfo(0, func.timeNow())
        const tempResult = testingFunc.convertSubIntoSubcategory(subcategoryDataResp)
        convertSubCategoryInfo(tempResult.subCategoryMap)

        const criticalIssuesTrendResp = await dashboardApi.fetchCriticalIssuesTrend()

        buildUnsecuredAPIs(criticalIssuesTrendResp)
        setShowTestingComponents(true)
    }

    useEffect(() => {
        fetchGraphData()
    }, [])
    
    const defaultChartOptions = {
        "legend": {
            enabled: false
        },
    }

    function extractCategoryNames(data) {
        if (!data || !Array.isArray(data) || data.length === 0) {
            return [];
        }

        const findings = data[0]?.data || [];
        return findings.map(([category]) => category);
    }

    const runTestEmptyCardComponent = <Text alignment='center' color='subdued'>There’s no data to show. <Link url="/dashboard/testing" target='_blank'>Run test</Link> to get data populated. </Text>
    const criticalUnsecuredAPIsOverTime = (unsecuredAPIs && unsecuredAPIs.length > 0 && unsecuredAPIs[0].data && unsecuredAPIs[0].data.length > 0) ? <InfoCard
        component={
            <StackedChart
                type='column'
                color='#6200EA'
                areaFillHex="true"
                height="280"
                background-color="#ffffff"
                data={unsecuredAPIs}
                defaultChartOptions={defaultChartOptions}
                text="true"
                yAxisTitle="Number of issues"
                width={40}
                gap={10}
                showGridLines={true}
                exportingDisabled={true}
            />
        }
        title="Critical Unsecured APIs Over Time"
        titleToolTip="Chart showing the number of critical unsecured APIs detected each month over the past year. Helps track security trends over time."
        linkText=""
        linkUrl=""
    /> : <EmptyCard title="Critical Unsecured APIs Over Time" subTitleComponent={showTestingComponents ? <Text alignment='center' color='subdued'>No Unsecured APIs found</Text>: runTestEmptyCardComponent} />

    const criticalFindings = (criticalFindingsData && criticalFindingsData.length > 0 && criticalFindingsData[0].data && criticalFindingsData[0].data.length > 0) ?
    <InfoCard
        component={
            <StackedChart
                type='column'
                color='#6200EA'
                areaFillHex="true"
                height="280"
                background-color="#ffffff"
                data={criticalFindingsData}
                defaultChartOptions={defaultChartOptions}
                text="true"
                yAxisTitle="Number of issues"
                width={40}
                gap={10}
                showGridLines={true}
                customXaxis={
                    {
                        categories: extractCategoryNames(criticalFindingsData),
                        crosshair: true
                    }
                }
                exportingDisabled={true}
            />
        }
        title="Critical Findings"
        titleToolTip="Overview of the most critical security issues detected, including the number of issues and APIs affected for each type of vulnerability."
        linkText=""
        linkUrl=""
    /> : <EmptyCard title="Critical Findings" subTitleComponent={showTestingComponents ? <Text alignment='center' color='subdued'>No Critical findings found</Text>: runTestEmptyCardComponent} />

    return (
        <HorizontalGrid gap={5} columns={2}>
            {criticalUnsecuredAPIsOverTime}
            {criticalFindings}
        </HorizontalGrid>
    )
}

export default CriticalIssuesDetailGraph