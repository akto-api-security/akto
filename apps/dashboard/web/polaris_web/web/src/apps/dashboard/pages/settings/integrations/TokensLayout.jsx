import React, { useEffect, useState } from 'react'
import {Box, Divider, EmptyState, LegacyCard, Text} from '@shopify/polaris';
import settingFunctions from '../module';
import func from '@/util/func';
import IntegrationsLayout from './IntegrationsLayout';
import PasswordTextField from '../../../components/layouts/PasswordTextField';
import EmptyScreensLayout from '../../../components/banners/EmptyScreensLayout';

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
      <div data-testid={`data_${index + 1}`}>
      <LegacyCard.Section title={`Token ${index + 1}`} key={index} 
        actions={[{ content: <div data-testid={`delete_token_${index + 1}`}>Delete</div>, destructive: true, onAction: () => deleteToken(item.id)}]}>
          <div style={{ paddingBottom: "5px" }}>
            <Text variant="bodyMd">{func.prettifyEpoch(item.timestamp)}</Text>
          </div>
        <PasswordTextField field={item.key} />
      </LegacyCard.Section>     
      </div>     
    ))
  )

  const emptyComponent = (
    <EmptyScreensLayout key={"emptyScreen"}
                    iconSrc={"/public/key_icon.svg"}
                    headingText={"No tokens found"}
                    description={"A token is required to use this integration. Click \"Generate token\" to generate one."}
                    buttonText={"Generate Token"}
                    onAction={generateNewToken}
                />
  )
  

  
  const BurpSuiteCard = (
    <LegacyCard  
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
  )
  return (
    <IntegrationsLayout title= {props.title} cardContent={props.cardContent} component={BurpSuiteCard} docsUrl={props.docsUrl}/> 
  )
}

export default TokensLayout