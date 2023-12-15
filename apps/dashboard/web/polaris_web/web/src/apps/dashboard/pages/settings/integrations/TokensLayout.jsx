import React, { useEffect, useState } from 'react'
import {Box, Divider, EmptyState, LegacyCard, Text} from '@shopify/polaris';
import settingFunctions from '../module';
import func from '@/util/func';
import IntegrationsLayout from './IntegrationsLayout';
import PasswordTextField from '../../../components/layouts/PasswordTextField';
import FeatureWrapper from "../../../components/FeatureWrapper"

function TokensLayout(props) {
  const [tokenList , setTokenList] = useState([])
  async function getTokenList (){
    let arr = await settingFunctions.getTokenList(props.type)
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
    window.open(props.docsUrl)
  }

  const generateNewToken = async() => {
    let arr = await settingFunctions.getNewToken(props.type)
    setTokenList(arr)
  }

  const listComponent = (
    tokenList.map((item,index) =>(
      <LegacyCard.Section title={`Token ${index + 1}`} key={index} 
        actions={[{ content: 'Delete', destructive: true, onAction: () => deleteToken(item.id)}]}>
          <div style={{ paddingBottom: "5px" }}>
            <Text variant="bodyMd">{func.prettifyEpoch(item.timestamp)}</Text>
          </div>
        <PasswordTextField field={item.key} />
      </LegacyCard.Section>     
    ))
  )
  
  const emptyComponent = (
      <LegacyCard.Section>
        <EmptyState
          heading='No tokens found'
          action={{content: 'Generate Token',onAction: generateNewToken}}
          // secondaryAction={{
          //   content: 'Learn more',
          // }}
        >
          <p>A token is required to use this integration. Click "Generate token" to generate one.</p>
        </EmptyState>
      </LegacyCard.Section>
  )
  
  function getFeatureLabel() {
    switch (props.type) {
      case func.testingResultType().CICD:
        return "CI_CD_INTEGRATION"
      case func.testingResultType().EXTERNAL_API:
        return "AKTO_EXTERNAL_API"
      case func.testingResultType().BURP:
        return "BASIC_CONNECTORS"
      default:
        return ""
    }
  }

  const BurpSuiteCard = (
    <FeatureWrapper featureLabel={getFeatureLabel()}>
    <LegacyCard title="Tokens" 
        secondaryFooterActions={tokenList.length > 0 ? [{content: 'See how it works', onAction: seeWork}] : []}
        primaryFooterAction={tokenList.length > 0 ? {content: 'Generate token', onAction: generateNewToken} : null}
    >
        {tokenList.length > 0 ? 
          (
            <div>
              {listComponent}
              <Divider />
              <br/>
            </div>
          )
          : emptyComponent}
      
    </LegacyCard>
    </FeatureWrapper>
  )
  return (
    <IntegrationsLayout title= {props.title} cardContent={props.cardContent} component={BurpSuiteCard} docsUrl={props.docsUrl}/> 
  )
}

export default TokensLayout