import React from 'react'
import PageWithCard from '../../../components/layouts/PageWithCard'

function IntegrationsLayout(props) {
  return (
    <PageWithCard 
        content="Settings" 
        backUrl='/dashboard/settings/integrations' 
        title={props.title} primaryActionContent="See Docs"
        cardTitle="About the Integration"
        cardContent={props.cardContent}
        component={props.component}
    />
  )
}

export default IntegrationsLayout