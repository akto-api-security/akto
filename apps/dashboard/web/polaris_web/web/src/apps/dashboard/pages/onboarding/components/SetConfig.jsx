import React, { useEffect, useState } from 'react'
import api from "../../testing/api"
import OnboardingStore from '../OnboardingStore'
import TextFieldWithInfo from "../../../components/shared/TextFieldWithInfo"
import { Spinner, VerticalStack } from '@shopify/polaris'

function SetConfig() {

    const [loading, setLoading] = useState(true)
    const setAuthObject = OnboardingStore(state => state.setAuthObject)
    const authObject = OnboardingStore(state => state.authObject)

    const fetchAuthMechanism = async() =>{
        let interval = setInterval(async () => {
            let localCopy = {}
            if(!authObject){
                await api.fetchAuthMechanismData().then((resp)=> {
                    const authObject = resp.authMechanism.authParams[0]
                    const {showHeader, ...obj} = authObject
                    localCopy = JSON.parse(JSON.stringify(obj))
                    setAuthObject(obj)
                })
            }
            if(Object.keys(localCopy).length > 0 || Object.keys(authObject).length > 0){
                setLoading(false)
                clearInterval(interval)
            }
        },1000)
    }
 
    useEffect(()=> {
        fetchAuthMechanism()
    },[])

    const updateObj = (val,param) => {
        const updatedObj = {...authObject}
        updatedObj[param] = val
        setAuthObject(updatedObj)
    }

    const formLayout = (
        <VerticalStack gap="4">
            <TextFieldWithInfo  value={authObject?.key} 
                                labelTooltip="Attacker token header key" 
                                labelText="Auth header key" 
                                setValue={(val) => updateObj(val,"key")} 
            />

            <TextFieldWithInfo  value={authObject?.value} 
                                labelTooltip="Attacker token value" 
                                labelText="Auth token value" 
                                setValue={(val) => updateObj(val,"value")}
                                type="text" 
            />
        </VerticalStack>
    )

    return (
        loading ? <div style={{margin: "auto"}}><Spinner size='small'/></div>
        : formLayout
    )
}

export default SetConfig