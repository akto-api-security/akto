import React, { useEffect, useState } from 'react'
import StackedChart from '../../../components/charts/StackedChart'
import EmptyCard from '../../dashboard/new_components/EmptyCard'
import { Link, Text } from '@shopify/polaris'
import InfoCard from '../../dashboard/new_components/InfoCard'
import dashboardApi from "../../dashboard/api.js"
import func from '@/util/func.js'
import { getDashboardCategory, mapLabel } from '../../../../main/labelHelper.js'

const CriticalUnsecuredAPIsOverTimeGraph = ({ startTimestamp, endTimestamp, linkText, linkUrl }) => {
    const [unsecuredAPIs, setUnsecuredAPIs] = useState([])
    const [showTestingComponents, setShowTestingComponents] = useState(false)
    const [isDataAvailable, setIsDataAvailable] = useState(false)

    function buildUnsecuredAPIs(input) {

        const {epochKey, issuesTrend} = input;

        const SEVERITY_CONFIG = {
            CRITICAL: { color: "#DF2909", name: "Critical Issues", data: [] },
            HIGH: { color: "#FED3D1", name: "High Issues", data: [] }
        };

        const transformed = []
        let dataAvailability = false
        for (const [severity, epochs] of Object.entries(issuesTrend)) {
            const dataset = SEVERITY_CONFIG[severity] || SEVERITY_CONFIG.HIGH;

            for (const epoch in epochs) {
                dataset.data.push([func.getEpochMillis(epoch, epochKey), epochs[epoch]]);
                dataAvailability = true
            }
        }
        transformed.push(SEVERITY_CONFIG.CRITICAL, SEVERITY_CONFIG.HIGH);

        setUnsecuredAPIs(transformed)
        setIsDataAvailable(dataAvailability)
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

    const runTestEmptyCardComponent = <Text alignment='center' color='subdued'>There's no data to show. <Link url="/dashboard/testing" target='_blank'>{mapLabel('Run test', getDashboardCategory())}</Link> to get data populated. </Text>

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
        title={`Critical or high severity unsecured ${mapLabel("APIs", getDashboardCategory())} Over Time`}
        titleToolTip={`Chart showing the number of ${mapLabel("APIs", getDashboardCategory())} detected(risk score >= 3) each month over the past year. Helps track security trends over time.`}
        linkText={linkText}
        linkUrl={linkUrl}
    /> : <EmptyCard title={`Critical or high severity unsecured ${mapLabel("APIs", getDashboardCategory())} Over Time`} subTitleComponent={showTestingComponents ? <Text alignment='center' color='subdued'>{`No unsecured ${mapLabel("APIs", getDashboardCategory())} found`}</Text>: runTestEmptyCardComponent} />

    return (
        {...criticalUnsecuredAPIsOverTime}
    )
}

export default CriticalUnsecuredAPIsOverTimeGraph