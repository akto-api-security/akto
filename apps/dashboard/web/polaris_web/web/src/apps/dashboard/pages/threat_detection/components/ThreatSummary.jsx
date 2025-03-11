import { useState, useEffect } from "react";
import { Box, Card, HorizontalGrid, HorizontalStack, Text, VerticalStack } from "@shopify/polaris"
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"
import PropTypes from "prop-types";
import { SummaryLineChart } from "./SummaryLineChart";
import func from "@/util/func";
import SummaryCard from '../../dashboard/new_components/SummaryCard'
import observeFunc from "../../observe/transform.js"
import SmoothAreaChart from '../../dashboard/new_components/SmoothChart'

const THREAT_SUMMARY_DATA = {
    "apis": null,
    "categoryCounts": null,
    "dailyThreatActorsCount": "SUCCESS",
    "endTimestamp": 0,
    "skip": 0,
    "sort": null,
    "startTimestamp": 0,
    "total": 0,
    "actorsCounts": [
      {
        "ts": 1741660863,
        "totalActors": 2,
        "severityActors": 3
      },
      {
        "ts": 1741660863,
        "totalActors": 5,
        "severityActors": 1
      },
      {
        "ts": 1741660863,
        "totalActors": 2,
        "severityActors": 2
      },
      {
        "ts": 1741660863,
        "totalActors": 1,
        "severityActors": 2
      },
      {
        "ts": 1741660863,
        "totalActors": 4,
        "severityActors": 1
      },
      {
        "ts": 1741660863,
        "totalActors": 2,
        "severityActors": 1
      },
      {
        "ts": 1741660863,
        "totalActors": 4,
        "severityActors": 0
      },
    ],
    "totalActors": 20,
    "criticalActors": 10
  }

export const ThreatSummary = ({ startTimestamp, endTimestamp }) => {
    const [criticalActors, setCriticalActors] = useState([]);
    const [activeActors, setActiveActors] = useState([]);
    const [criticalActorsDelta, setCriticalActorsDelta] = useState(0);
    const [totalActorsDelta, setTotalActorsDelta] = useState(0);

    const getThreatActorsCount = async () => {
        const actorsCountByDay = THREAT_SUMMARY_DATA.actorsCounts;
        const criticalActors = actorsCountByDay.map(item => item.severityActors);
        const activeActors = actorsCountByDay.map(item => item.totalActors);
        observeFunc.setIssuesState(criticalActors, setCriticalActors, setCriticalActorsDelta, true);
        observeFunc.setIssuesState(activeActors, setActiveActors, setTotalActorsDelta, true);
    }

    useEffect(() => {
        getThreatActorsCount();
    }, [startTimestamp, endTimestamp]);

    const summaryInfo = [
        {
            title: 'Critical Actors',
            data: observeFunc.formatNumberWithCommas(criticalActors[criticalActors.length - 1]),
            variant: 'heading2xl',
            color: 'critical',
            byLineComponent: observeFunc.generateByLineComponent(criticalActorsDelta, func.timeDifference(startTimestamp, endTimestamp)),
            smoothChartComponent: (<SmoothAreaChart tickPositions={criticalActors} />)
        },
        {
            title: 'Active Actors',
            data: observeFunc.formatNumberWithCommas(activeActors[activeActors.length - 1]),
            variant: 'heading2xl',
            byLineComponent: observeFunc.generateByLineComponent(totalActorsDelta, func.timeDifference(startTimestamp, endTimestamp)),
            smoothChartComponent: (<SmoothAreaChart tickPositions={activeActors} />)
        }
    ]

    return (
        <SummaryCard summaryItems={summaryInfo} />
    )
}

ThreatSummary.propTypes = {
    criticalActors: PropTypes.number.isRequired,
    activeActors: PropTypes.number.isRequired,
    blockedActors: PropTypes.number.isRequired,
    attackSuccessRate: PropTypes.number.isRequired,
}