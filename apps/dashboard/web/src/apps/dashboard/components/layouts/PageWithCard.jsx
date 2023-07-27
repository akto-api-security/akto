import { Button, LegacyCard, Page } from '@shopify/polaris'
import React from 'react'
import { useNavigate } from 'react-router-dom'

function PageWithCard(props) {
  const navigate = useNavigate()

  return (
    <Page
      backAction={{ content: props.content, onAction: () => navigate(props.backUrl) }}
      title={props.title}
      primaryAction={props.docsAction}
      secondaryActions={props?.secondaryAction}
      divider
    >
      <LegacyCard title={props.cardTitle} sectioned>
        <p>{props.cardContent}</p>
      </LegacyCard>
      {props.component}
    </Page>
  )
}

export default PageWithCard