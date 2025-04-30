import React from 'react'
import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import SessionStore from './SessionStore'

function TokenValidator() {

    let navigate = useNavigate()
    const accessToken = SessionStore(state => state.accessToken)
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