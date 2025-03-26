import React, { useEffect, useState } from 'react'
import NewConnection from './components/NewConnection'
import api from './api'
import UpdateConnections from './components/UpdateConnections'
import SpinnerCentered from "../../components/progress/SpinnerCentered"
import QuickStartStore from './quickStartStore'
import quickStartFunc from './transform'


function QuickStart() {

    const [myConnections, setMyConnections] = useState([])
    const [loading, setLoading] = useState(false)
    const setActive = QuickStartStore(state => state.setActive)
    const setCurrentCardObj = QuickStartStore(state => state.setCurrentConnector)

    const sourceCodeObjectList = quickStartFunc.getSourceCodeConnectors()
    const searchParams = new URLSearchParams(window.location.search);

    const queryConnector = sourceCodeObjectList.filter((x) => x.key.toLowerCase() === searchParams.get('connect'))

    const fetchConnections = async () => {
        
        if(queryConnector.length > 0) {
            setActive("update")
            setLoading(false)
            setCurrentCardObj(queryConnector[0])
            return;
        }
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
            (myConnections.length > 0 || queryConnector.length) > 0?
            <div className='update-connections'>
                <UpdateConnections myConnections={[]}/>
            </div> : <NewConnection/>
    )

    return (
        component
    )
}

export default QuickStart