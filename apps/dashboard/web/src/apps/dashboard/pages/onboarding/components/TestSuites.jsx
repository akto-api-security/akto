import React, { useEffect } from 'react'
import api from '../api'

function TestSuites() {

    const fetchTestSuites = async() => {
        await api.fetchTestSuites().then((resp) => {
            console.log(resp)
        })
    }

    useEffect(()=> {
        fetchTestSuites()
    },[])

    return (
        <div>TestSuites</div>
    )
}

export default TestSuites