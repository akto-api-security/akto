import React, { useEffect, useState } from 'react'
import NewConnection from './components/NewConnection'
import api from './api'
import UpdateConnections from './components/UpdateConnections'
import SpinnerCentered from "../../components/progress/SpinnerCentered"
import QuickStartStore from './quickStartStore'


function QuickStart() {

    const [myConnections, setMyConnections] = useState([])
    const [loading, setLoading] = useState(false)
    const setActive = QuickStartStore(state => state.setActive)

    const fetchConnections = async () => {
        setLoading(true)
        await api.fetchQuickStartPageState().then((resp)=> {
            setMyConnections(resp.configuredItems)
            resp.configuredItems.length > 0 ? setActive("update") : setActive("new")
            setLoading(false)
        })
    }

    useEffect(()=>{
        setLoading(true)
        fetchConnections()
    },[])

    const component = (
        loading ? <SpinnerCentered/> :
            myConnections.length > 0 ?
            <div className='update-connections'>
                <UpdateConnections myConnections={myConnections}/>
            </div> : <NewConnection/>
    )

    return (
        component
    )
}

export default QuickStart