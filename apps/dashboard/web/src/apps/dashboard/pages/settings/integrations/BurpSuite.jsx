import React, { useEffect, useState } from 'react'
import {LegacyCard} from '@shopify/polaris';
import settingFunctions from '../module';
import globalFunctions from '@/util/func';
import IntegrationsLayout from './IntegrationsLayout';
import PasswordTextField from '../../../components/layouts/PasswordTextField';

function BurpSuite() {
  const [tokenList , setTokenList] = useState([])
  async function getTokenList (){
    let arr = await settingFunctions.getTokenList()
    setTokenList(arr)
  }
  
  useEffect(() =>{
    getTokenList()
  },[])

  const deleteToken = async(id) => {
    await settingFunctions.deleteToken(id)
    getTokenList()
  }

  const seeWork = () => {
    console.log("see Working")
  }

  const generateNewToken = async() => {
    let arr = await settingFunctions.getNewToken(globalFunctions.testingResultType().BURP)
    setTokenList(arr)
  }

  const BurpSuiteCard = (
    <LegacyCard title="Tokens" 
        secondaryFooterActions={[{content: 'See how it works', onAction: seeWork}]}
        primaryFooterAction={{content: 'Generate Token', onAction: generateNewToken}}
    >
        {tokenList.map((item,index) =>(
          <LegacyCard.Section title={`Token ${index + 1}`} key={index} 
            actions={[{ content: 'Delete', destructive: true, onAction: () => deleteToken(item.id)}]}>
            <p>{globalFunctions.prettifyEpoch(item.timestamp)}</p>
            <PasswordTextField text={item.key} />
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