import React, { useEffect, useState } from 'react'
import StackedChart from '../../../components/charts/StackedChart'
import EmptyCard from '../../dashboard/new_components/EmptyCard'
import { Link, Text } from '@shopify/polaris'
import InfoCard from '../../dashboard/new_components/InfoCard'
import dashboardApi from "../../dashboard/api.js"

const CriticalUnsecuredAPIsOverTimeGraph = ({ linkText, linkUrl }) => {
    const [unsecuredAPIs, setUnsecuredAPIs] = useState([])
    const [showTestingComponents, setShowTestingComponents] = useState(false)
    const [isDataAvailable, setIsDataAvailable] = useState(false)

    function buildUnsecuredAPIs(input) {

        const SEVERITY_CONFIG = {
            CRITICAL: { color: "#E45357", name: "Critical Issues", data: [] },
            HIGH: { color: "#EF864C", name: "High Issues", data: [] }
        };
                
        const transformed = []
        let dataAvailability = false
        for (const [severity, epochs] of Object.entries(input)) {
            const dataset = SEVERITY_CONFIG[severity] || SEVERITY_CONFIG.HIGH;
            
            for (const epoch in epochs) {
                dataset.data.push([Number(epoch) * 86400000, epochs[epoch]]);
                dataAvailability = true
            }
        }
        transformed.push(SEVERITY_CONFIG.CRITICAL, SEVERITY_CONFIG.HIGH);

        setUnsecuredAPIs(transformed)
        setIsDataAvailable(dataAvailability)
    }


    const fetchGraphData = async () => {
        setShowTestingComponents(false)
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

    const runTestEmptyCardComponent = <Text alignment='center' color='subdued'>There’s no data to show. <Link url="/dashboard/testing" target='_blank'>Run test</Link> to get data populated. </Text>

    const criticalUnsecuredAPIsOverTime = (unsecuredAPIs && unsecuredAPIs.length > 0 && isDataAvailable) ? <InfoCard
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
        title="Critical or high severity Unsecured APIs Over Time"
        titleToolTip="Chart showing the number of APIs detected(risk score >= 3) each month over the past year. Helps track security trends over time."
        linkText={linkText}
        linkUrl={linkUrl}
    /> : <EmptyCard title="Critical or high severity Unsecured APIs Over Time" subTitleComponent={showTestingComponents ? <Text alignment='center' color='subdued'>No Unsecured APIs found</Text>: runTestEmptyCardComponent} />

    return (
        {...criticalUnsecuredAPIsOverTime}
    )
}

export default CriticalUnsecuredAPIsOverTimeGraph