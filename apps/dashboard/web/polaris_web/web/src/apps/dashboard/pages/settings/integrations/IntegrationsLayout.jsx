import React from 'react'
import PageWithCard from '../../../components/layouts/PageWithCard'
import { Button, LegacyCard } from '@shopify/polaris'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'

function IntegrationsLayout(props) {

  const redirectDocs = () => {
    window.open(props.docsUrl)
  }

  const components = [      
      <LegacyCard title="About the Integration" sectioned key="aboutSection">
        <p>{props.cardContent}</p>
      </LegacyCard>,
      <div key="primaryComponent">{props.component}</div>,
      <div key="secondaryComponent">{props.secondaryComponent}</div>,
]

  return (
    <PageWithMultipleCards 
        // content="Settings" 
        backUrl='/dashboard/settings/integrations' 
        title={props.title}
        primaryAction={<Button onClick={redirectDocs} primary>See Docs</Button>}
        components={components}
        divider={true}
        fullWidth={false}
        secondaryActions={props?.secondaryAction}
    />
  )
}

export default IntegrationsLayout