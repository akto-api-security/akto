import { Link, Text } from '@shopify/polaris';
import React, { useEffect, useState } from 'react'
import InfoCard from '../../dashboard/new_components/InfoCard';
import EmptyCard from '../../dashboard/new_components/EmptyCard';
import testingApi from "../../testing/api.js"
import testingFunc from "../../testing/transform.js"
import BarGraph from '../../../components/charts/BarGraph.jsx';
import LocalStore from "../../../../main/LocalStorageStore";
import { getDashboardCategory, mapLabel } from '../../../../main/labelHelper';

const CriticalFindingsGraph = ({ startTimestamp, endTimestamp, linkText, linkUrl, complianceMode }) => {
    const subCategoryMap = LocalStore(state => state.subCategoryMap);

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
        let tempResultSubCategoryMap = {}
        if (complianceMode) {
            Object.entries(subcategoryDataResp).forEach(([testId, count]) => {
                let clauses = (subCategoryMap[testId]?.compliance?.mapComplianceToListClauses || {})[complianceMode] || []
                clauses.forEach(clause => {
                    tempResultSubCategoryMap[clause] = tempResultSubCategoryMap[clause] || {text: 0, key: clause}
                    tempResultSubCategoryMap[clause].text += count
                });
            })
        } else {
            tempResultSubCategoryMap = testingFunc.convertSubIntoSubcategory(subcategoryDataResp).subCategoryMap
        }
        convertSubCategoryInfo(tempResultSubCategoryMap)
        setShowTestingComponents(true)
    }

    useEffect(() => {
        fetchGraphData()
    }, [startTimestamp, endTimestamp, complianceMode])

    const defaultChartOptions = {
        "legend": {
            enabled: false
        },
    }

    const runTestEmptyCardComponent = <Text alignment='center' color='subdued'>There's no data to show. <Link url="/dashboard/testing" target='_blank'>{mapLabel('Run test', getDashboardCategory())}</Link> to get data populated. </Text>

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
        title={complianceMode ? (complianceMode + " clauses") : "Vulnerabilities findings"}
        titleToolTip={"Overview of the most critical security issues detected, including the number of issues and " + mapLabel("APIs", getDashboardCategory()) + " affected for each type of vulnerability."}
        linkText={linkText}
        linkUrl={linkUrl}
    /> : <EmptyCard title={complianceMode ? (complianceMode + " clauses") : "Vulnerabilities findings"} subTitleComponent={showTestingComponents ? <Text alignment='center' color='subdued'>No Vulnerabilities found</Text>: runTestEmptyCardComponent} />

    return (
        {...criticalFindings}
      )
}

export default CriticalFindingsGraph