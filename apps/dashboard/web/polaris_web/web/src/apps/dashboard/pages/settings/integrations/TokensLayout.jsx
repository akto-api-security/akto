import React, { useEffect, useState } from 'react'
import {Box, Divider, EmptyState, LegacyCard, Text} from '@shopify/polaris';
import settingFunctions from '../module';
import func from '@/util/func';
import IntegrationsLayout from './IntegrationsLayout';
import PasswordTextField from '../../../components/layouts/PasswordTextField';
import { usePermissions, whenAllowed } from '@/util/permissions';

function TokensLayout(props) {
  const { canCall } = usePermissions()
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
      <div data-testid={`data_${index + 1}`}>
      <LegacyCard.Section title={`Token ${index + 1}`} key={index} 
        actions={[{ content: <div data-testid={`delete_token_${index + 1}`}>Delete</div>, destructive: true, onAction: () => deleteToken(item.id), ...whenAllowed(canCall('api/deleteApiToken'))}]}>
          <div style={{ paddingBottom: "5px" }}>
            <Text variant="bodyMd">{func.prettifyEpoch(item.timestamp)}</Text>
          </div>
        <PasswordTextField field={item.key} />
      </LegacyCard.Section>     
      </div>     
    ))
  )
  
  const emptyComponent = (
      <LegacyCard.Section>
        <EmptyState
          heading='No tokens found'
          action={{content: <div data-testid="generate_token_button">Generate Token</div>,onAction: generateNewToken, ...whenAllowed(canCall('api/addApiToken'))}}
          // secondaryAction={{
          //   content: 'Learn more',
          // }}
        >
          <p>A token is required to use this integration. Click "Generate token" to generate one.</p>
        </EmptyState>
      </LegacyCard.Section>
  )
  
  const BurpSuiteCard = (
    <LegacyCard title="Tokens" 
        secondaryFooterActions={tokenList.length > 0 ? [{content: 'See how it works', onAction: seeWork}] : []}
        primaryFooterAction={tokenList.length > 0 ? {content: 'Generate token', onAction: generateNewToken, ...whenAllowed(canCall('api/addApiToken'))} : null}
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
  )
  return (
    <IntegrationsLayout title= {props.title} cardContent={props.cardContent} component={BurpSuiteCard} docsUrl={props.docsUrl} readOnly={!canCall('api/addApiToken')}/> 
  )
}

export default TokensLayout