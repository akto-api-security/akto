import React, { useEffect, useState } from 'react'
import NewConnection from './components/NewConnection'
import api from './api'
import UpdateConnections from './components/UpdateConnections'
import SpinnerCentered from "../../components/progress/SpinnerCentered"


function QuickStart() {

    const [myConnections, setMyConnections] = useState([])
    const [loading, setLoading] = useState(false)

    const fetchConnections = async () => {
        setLoading(true)
        await api.fetchQuickStartPageState().then((resp)=> {
            setMyConnections(resp.configuredItems)
            setLoading(false)
        })
    }

    useEffect(()=>{
        setLoading(true)
        fetchConnections()
    },[])

    const component = (
        loading ? <SpinnerCentered/> :
            myConnections.length > 0 ? <UpdateConnections myConnections={myConnections}/> : <NewConnection/>
    )

    return (
        component
    )
}

export default QuickStart