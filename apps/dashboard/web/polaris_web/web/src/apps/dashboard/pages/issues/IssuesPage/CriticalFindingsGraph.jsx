import { Link, Text } from '@shopify/polaris';
import React, { useEffect, useState } from 'react'
import InfoCard from '../../dashboard/new_components/InfoCard';
import StackedChart from '../../../components/charts/StackedChart';
import EmptyCard from '../../dashboard/new_components/EmptyCard';
import testingApi from "../../testing/api.js"
import testingFunc from "../../testing/transform.js"
import func from "@/util/func";

const CriticalFindingsGraph = ({ linkText, linkUrl }) => {
    const [criticalFindingsData, setCriticalFindingsData] = useState([])
    const [showTestingComponents, setShowTestingComponents] = useState(false)

    function convertSubCategoryInfo(tempSubCategoryMap) {
        const entries = Object.values(tempSubCategoryMap);
        entries.sort((a, b) => b.text - a.text);
        const topEntries = entries.slice(0, 5);
        const data = topEntries.map(entry => [entry.filterKey, entry.text]);
        setCriticalFindingsData([{ "data": data, "color": "#E45357", name: "Critical Issues" }])
    }

    const fetchGraphData = async () => {
        setShowTestingComponents(false)
        const subcategoryDataResp = await testingApi.getSummaryInfo(0, func.timeNow())
        const tempResult = testingFunc.convertSubIntoSubcategory(subcategoryDataResp)
        convertSubCategoryInfo(tempResult.subCategoryMap)
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
        title="Vulnerabilities findings"
        titleToolTip="Overview of the most critical security issues detected, including the number of issues and APIs affected for each type of vulnerability."
        linkText={linkText}
        linkUrl={linkUrl}
    /> : <EmptyCard title="Vulnerabilities findings" subTitleComponent={showTestingComponents ? <Text alignment='center' color='subdued'>No Vulnerabilities found</Text>: runTestEmptyCardComponent} />

    return (
        {...criticalFindings}
      )
}

export default CriticalFindingsGraph