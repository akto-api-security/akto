import React, { useEffect, useState } from 'react'
import api from './api';
import func from '@/util/func';

function HomeDashboard() {

    const [riskScoreRangeMap, setRiskScoreRangeMap] = useState({});
    const [issuesTrendMap, setIssuesTrendMap] = useState({});

    const fetchData = async() =>{
        await api.getRiskScoreRangeMap().then((resp)=>{
            console.log(resp)
            setRiskScoreRangeMap(resp)
        })
        await api.getIssuesTrend(0, func.timeNow()).then((resp)=>{
            console.log(resp);
            setIssuesTrendMap(resp);
        })
    }

    useEffect(()=>{
        fetchData()
    },[])

    return (
        <div>HomeDashboard</div>
    )
}

export default HomeDashboard