import { Banner, Text, Page, Layout, LegacyCard } from '@shopify/polaris'
import React from 'react'

function ComingSoonPage({ title, description }) {
    const defaultDescription = `${title} feature is currently under development.
Stay tuned for updates!
If you have an immediate use case for this feature, please email us at hello@akto.io`

    return (
        <Page title={title}>
            <Layout>
                <Layout.Section>
                    <LegacyCard>
                        <LegacyCard.Section>
                            <Banner status="info" title='Coming Soon'>
                                <Text variant="headingSm" as="h4">
                                    {description || defaultDescription}
                                </Text>
                            </Banner>
                        </LegacyCard.Section>
                    </LegacyCard>
                </Layout.Section>
            </Layout>
        </Page>
    )
}

export default ComingSoonPage 