import React from 'react'
import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import SessionStore from './SessionStore'
import PersistStore from './PersistStore'

function TokenValidator() {

    let navigate = useNavigate()
    const accessToken = SessionStore(state => state.accessToken)
    const dashboardCategory = PersistStore((state) => state.dashboardCategory) || "API Security"

  useEffect(() => {
    if (accessToken === null || accessToken === '') {
        navigate('/login')
    } else {
        const targetPath = dashboardCategory === "Endpoint Security"
            ? "/dashboard/observe/agentic-assets"
            : "/dashboard/observe/inventory"
        navigate(targetPath)
    }
  },[accessToken, dashboardCategory])
  return (
    <></>
  )
}

export default TokenValidator