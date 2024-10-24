import React from 'react'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import TitleWithInfo from '../../../components/shared/TitleWithInfo'
import { Button } from '@shopify/polaris'

const NewActiveTest = () => {
    const components = []
    return (
        <PageWithMultipleCards
            fullWidth={false}
            title={
              <TitleWithInfo
                titleText={"New test"}
              />
            }
            backUrl={"/dashboard/testing/active-testing"}
            secondaryActions={<Button secondaryActions>Documentation</Button>}
            components={components}
        />
    )
}

export default NewActiveTest