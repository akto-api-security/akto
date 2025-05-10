import { useState, useEffect } from "react";
import PropTypes from "prop-types";
import func from "@/util/func";
import SummaryCard from '../../dashboard/new_components/SummaryCard'
import observeFunc from "../../observe/transform.js"
import SmoothAreaChart from '../../dashboard/new_components/SmoothChart'
import api from "../api";

export const ThreatSummary = ({ startTimestamp, endTimestamp }) => {
    const [criticalActors, setCriticalActors] = useState([]);
    const [activeActors, setActiveActors] = useState([]);
    const [criticalActorsDelta, setCriticalActorsDelta] = useState(0);
    const [totalActorsDelta, setTotalActorsDelta] = useState(0);

    const fetchThreatActorsCount = async () => {
        const response = await api.getDailyThreatActorsCount(startTimestamp, endTimestamp);
        const actorsCountByDay =  response.actorsCounts;
        const criticalActors = actorsCountByDay.map(item => item.criticalActors);
        const activeActors = actorsCountByDay.map(item => item.totalActors);
        observeFunc.setIssuesState(criticalActors, setCriticalActors, setCriticalActorsDelta, true);
        observeFunc.setIssuesState(activeActors, setActiveActors, setTotalActorsDelta, true);
    }

    useEffect(() => {
        fetchThreatActorsCount();
    }, [startTimestamp, endTimestamp]);

    const summaryInfo = [
        {
            title: 'Critical Actors',
            data: observeFunc.formatNumberWithCommas(criticalActors[criticalActors.length - 1]),
            variant: 'heading2xl',
            color: 'critical',
            // byLineComponent: observeFunc.generateByLineComponent(criticalActorsDelta, func.timeDifference(startTimestamp, endTimestamp)),
            smoothChartComponent: (<SmoothAreaChart tickPositions={criticalActors} />)
        },
        {
            title: 'Active Actors',
            data: observeFunc.formatNumberWithCommas(activeActors[activeActors.length - 1]),
            variant: 'heading2xl',
            // byLineComponent: observeFunc.generateByLineComponent(totalActorsDelta, func.timeDifference(startTimestamp, endTimestamp)),
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