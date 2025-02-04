import React, { useEffect, useState } from 'react'
import StackedChart from '../../../components/charts/StackedChart'
import EmptyCard from '../../dashboard/new_components/EmptyCard'
import { Link, Text } from '@shopify/polaris'
import InfoCard from '../../dashboard/new_components/InfoCard'
import dashboardApi from "../../dashboard/api.js"

const CriticalUnsecuredAPIsOverTimeGraph = ({ startTimestamp, endTimestamp, linkText, linkUrl }) => {
    const [unsecuredAPIs, setUnsecuredAPIs] = useState([])
    const [showTestingComponents, setShowTestingComponents] = useState(false)

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
        const criticalIssuesTrendResp = await dashboardApi.fetchCriticalIssuesTrend(startTimestamp, endTimestamp)

        buildUnsecuredAPIs(criticalIssuesTrendResp)
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
        title="Critical & High Issues Over Time"
        titleToolTip="Chart showing the number of APIs detected(risk score >= 4) each month over the past year. Helps track security trends over time."
        linkText={linkText}
        linkUrl={linkUrl}
    /> : <EmptyCard title="Critical & High Issues Over Time" subTitleComponent={showTestingComponents ? <Text alignment='center' color='subdued'>No critical issue found</Text>: runTestEmptyCardComponent} />

    return (
        {...criticalUnsecuredAPIsOverTime}
    )
}

export default CriticalUnsecuredAPIsOverTimeGraph