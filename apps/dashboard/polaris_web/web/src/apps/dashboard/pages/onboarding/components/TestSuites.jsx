import React, { useEffect, useState } from 'react'
import api from '../api'
import SuitesCard from './SuitesCard'
import GridRows from '../../../components/shared/GridRows'
import SpinnerCentered from '../../../components/progress/SpinnerCentered'

function TestSuites() {

    const [testSuites, setTestSuites] = useState([])
    const [loading, setLoading] = useState(false)

    const fetchTestSuites = async() => {
        setLoading(true)
        await api.fetchTestSuites().then((resp) => {
            setLoading(false)
            setTestSuites(resp.testSuites)
        })
    }

    useEffect(()=> {
        fetchTestSuites()
    },[])

    return (
        loading ? <SpinnerCentered /> :
        <GridRows columns={1} items={testSuites} CardComponent={SuitesCard} />
    )
}

export default TestSuites