import React from 'react'
import {Page, LegacyCard} from '@shopify/polaris';

function BurpSuite() {
  return (
    <Page
      backAction={{content: 'Settings', url: '/settings/integrations'}}
      title="Burp Suite"
      primaryAction={{content: 'See Docs'}}
      divider
    >
    </Page>
  )
}

export default BurpSuite