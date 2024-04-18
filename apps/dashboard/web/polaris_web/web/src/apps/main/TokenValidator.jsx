import React from 'react'
import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import PersistStore from './PersistStore'

function TokenValidator() {

    let navigate = useNavigate()
    const accessToken = PersistStore(state => state.accessToken)
  useEffect(() => {
    console.log(accessToken)
    if (accessToken === null || accessToken === '') {
        navigate('/login')
    } else {
        navigate('/dashboard/observe/inventory')
    }
  },[accessToken])
  return (
    <></>
  )
}

export default TokenValidator