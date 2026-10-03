import React from 'react'
import { Banner, LegacyCard } from '@shopify/polaris'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'

function IntegrationsLayout(props) {


  const components = [      
      ...(props.readOnly ? [<Banner status="info" key="readOnlyBanner">You can view this integration, but your role can't change it.</Banner>] : []),
      <LegacyCard title="About the Integration" sectioned key="aboutSection">
        {(typeof (props.cardContent) === 'string') ? <p>{props.cardContent}</p> : props.cardContent}
      </LegacyCard>,
      <div key="primaryComponent">{props.component}</div>,
      <div key="secondaryComponent">{props.secondaryComponent}</div>,
]

  return (
    <PageWithMultipleCards 
        // content="Settings" 
        backUrl='/dashboard/settings/integrations' 
        title={props.title}
        components={components}
        divider={true}
        fullWidth={false}
        secondaryActions={props?.secondaryAction}
    />
  )
}

export default IntegrationsLayout