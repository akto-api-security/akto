import React, { useEffect, useState } from 'react'
import {LegacyCard} from '@shopify/polaris';
import settingRequests from '../api';
import globalFunctions from '@/util/func';
import IntegrationsLayout from './IntegrationsLayout';

function BurpSuite() {
  const [tokenList , setTokenList] = useState([])
  async function getTokenList(){
    let resp = await settingRequests.fetchApiTokens()
    setTokenList(resp.apiTokenList)
  }
  useEffect(() =>{
    getTokenList()
  },[])

  const BurpSuiteCard = (
    <LegacyCard title="Tokens">
        {tokenList.map((item,index) =>(
          <LegacyCard.Section title = {`Token ${index + 1}`} key={index}>
            <p>{item.key}</p>
          </LegacyCard.Section>     
        ))}
    </LegacyCard>
  )

  let cardContent = "Seamlessly enhance your web application security with Burp Suite integration, empowering you to efficiently detect vulnerabilities, analyze and intercept web traffic, and fortify your digital defenses. "
  return (
    <IntegrationsLayout title= "Burp Suite" cardContent={cardContent} component={BurpSuiteCard} /> 
  )
}

export default BurpSuite