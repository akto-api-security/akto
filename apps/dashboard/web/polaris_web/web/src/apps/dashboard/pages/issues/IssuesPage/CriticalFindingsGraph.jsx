import { Link, Text } from '@shopify/polaris';
import React, { useEffect, useState } from 'react'
import InfoCard from '../../dashboard/new_components/InfoCard';
import EmptyCard from '../../dashboard/new_components/EmptyCard';
import testingApi from "../../testing/api.js"
import testingFunc from "../../testing/transform.js"
import func from "@/util/func";
import BarGraph from '../../../components/charts/BarGraph.jsx';

const CriticalFindingsGraph = ({ startTimestamp, endTimestamp, linkText, linkUrl }) => {
    const [criticalFindingsData, setCriticalFindingsData] = useState([])
    const [showTestingComponents, setShowTestingComponents] = useState(false)

    function convertSubCategoryInfo(tempSubCategoryMap) {
        const entries = Object.keys(tempSubCategoryMap).map((x) => {
            let tempObj = tempSubCategoryMap[x];
            tempObj.key = x
            return tempObj
        })
        entries.sort((a, b) => b.text - a.text);
        const topEntries = entries.slice(0, 5);
        const data = topEntries.map(entry => {return {text: entry.key, value: entry.text, color: entry.color}});
        setCriticalFindingsData(data)
    }

    const fetchGraphData = async () => {
        setShowTestingComponents(false)
        const subcategoryDataResp = await testingApi.getSummaryInfo(startTimestamp, endTimestamp)
        const tempResult = testingFunc.convertSubIntoSubcategory(subcategoryDataResp)
        convertSubCategoryInfo(tempResult.subCategoryMap)
        setShowTestingComponents(true)
    }

    useEffect(() => {
        fetchGraphData()
    }, [startTimestamp, endTimestamp])
    
    const defaultChartOptions = {
        "legend": {
            enabled: false
        },
    }

    const runTestEmptyCardComponent = <Text alignment='center' color='subdued'>There’s no data to show. <Link url="/dashboard/testing" target='_blank'>Run test</Link> to get data populated. </Text>

    const criticalFindings = (criticalFindingsData && criticalFindingsData.length > 0) ?
    <InfoCard
        component={
            <BarGraph
                data={criticalFindingsData}
                areaFillHex="true"
                height={"320px"}
                defaultChartOptions={defaultChartOptions}
                barGap={12}
                showGridLines={true}
                showYAxis={true}
                yAxisTitle="Number of Issues"
                barWidth={30}
            />
        }
        title="Vulnerabilities findings by the top 5 categories"
        titleToolTip="Overview of the most critical security issues detected, including the number of issues and APIs affected for each of the top 5 vulnerability categories."
        linkText={linkText}
        linkUrl={linkUrl}
    /> : <EmptyCard title="Vulnerabilities findings by the top 5 categories" subTitleComponent={showTestingComponents ? <Text alignment='center' color='subdued'>No Vulnerabilities found</Text>: runTestEmptyCardComponent} />

    return (
        {...criticalFindings}
      )
}

export default CriticalFindingsGraph