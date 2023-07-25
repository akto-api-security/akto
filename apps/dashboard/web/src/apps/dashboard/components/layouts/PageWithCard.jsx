import { LegacyCard, Page } from '@shopify/polaris'
import React from 'react'

function PageWithCard(props) {
  return (
    <Page
      backAction={{content: props.content, url: props.backUrl}}
      title={props.title}
      primaryAction={props.docsAction}
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