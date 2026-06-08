import { useState, useEffect } from "react";
import PropTypes from "prop-types";
import func from "@/util/func";
import SummaryCard from '../../dashboard/new_components/SummaryCard'
import observeFunc from "../../observe/transform.js"
import SmoothAreaChart from '../../dashboard/new_components/SmoothChart'
import api from "../api";

export const ThreatSummary = ({ startTimestamp, endTimestamp }) => {
    const [criticalActorsChart, setCriticalActorsChart] = useState([]);
    const [activeActorsChart, setActiveActorsChart] = useState([]);
    const [criticalActorsDelta, setCriticalActorsDelta] = useState(0);
    const [totalActorsDelta, setTotalActorsDelta] = useState(0);
    const [totalCritical, setTotalCritical] = useState(0);
    const [totalActive, setTotalActive] = useState(0);

    const fetchThreatActorsCount = async () => {
        const response = await api.getDailyThreatActorsCount(startTimestamp, endTimestamp);
        const actorsCountByDay = response.actorsCounts || [];
        const criticalActors = actorsCountByDay.map(item => item.criticalActors);
        const activeActors = actorsCountByDay.map(item => item.totalActors);
        observeFunc.setIssuesState(criticalActors, setCriticalActorsChart, setCriticalActorsDelta, true);
        observeFunc.setIssuesState(activeActors, setActiveActorsChart, setTotalActorsDelta, true);
        setTotalCritical(response.totalCriticalActors || 0);
        setTotalActive(response.activeActorsCount || 0);
    }

    useEffect(() => {
        fetchThreatActorsCount();
    }, [startTimestamp, endTimestamp]);

    const summaryInfo = [
        {
            title: 'Critical Actors',
            data: observeFunc.formatNumberWithCommas(totalCritical),
            variant: 'heading2xl',
            color: 'critical',
            // byLineComponent: observeFunc.generateByLineComponent(criticalActorsDelta, func.timeDifference(startTimestamp, endTimestamp)),
            smoothChartComponent: (<SmoothAreaChart tickPositions={criticalActorsChart} />)
        },
        {
            title: 'Active Actors',
            data: observeFunc.formatNumberWithCommas(totalActive),
            variant: 'heading2xl',
            // byLineComponent: observeFunc.generateByLineComponent(totalActorsDelta, func.timeDifference(startTimestamp, endTimestamp)),
            smoothChartComponent: (<SmoothAreaChart tickPositions={activeActorsChart} />)
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