import React from 'react'
import PageWithCard from '../../../components/layouts/PageWithCard'
import { Button } from '@shopify/polaris'

function IntegrationsLayout(props) {

  const redirectDocs = () => {
    window.open(props.docsUrl)
  }

  return (
    <PageWithCard 
        content="Settings" 
        backUrl='/dashboard/settings/integrations' 
        title={props.title}
        docsAction={<Button onClick={redirectDocs} primary>See Docs</Button>}
        cardTitle="About the Integration"
        cardContent={props.cardContent}
        component={props.component}
    />
  )
}

export default IntegrationsLayout