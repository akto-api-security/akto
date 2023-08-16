import { Box, Card, Divider, LegacyCard, Page, Text } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import settingFunctions from '../module'

function About() {

    const [objArr, setObjectArr] = useState([])
    async function fetchDetails(){
        const arr = await settingFunctions.fetchAdminInfo()
        setObjectArr(arr)
    }

    useEffect(()=>{
        fetchDetails()
    },[])

    const titleComponent = (
        <Box paddingBlockEnd="4">
            <Text variant="headingMd">Account Information</Text>
            <Box paddingBlockStart="2">
                <Text variant="bodyMd">Take control of your profile, privacy settings, and preferences all in one place.</Text>
            </Box>
        </Box>
    )

    const infoComponent = (
        objArr.map((item,index)=>(
            <Box key={item.title} >
                <Text fontWeight='semi-bold' color='subdued'>{item.title}</Text>
                <Text fontWeight='bold'>{item.text}</Text>
            </Box>
        ))
    )
  return (
    <Page
        title="About"
        divider
    >
        <LegacyCard title={titleComponent}>
            <Divider />
            <LegacyCard.Section  >
                {infoComponent}
            </LegacyCard.Section>
            <LegacyCard.Section subdued>
                View our <a href='#'>terms of service</a> and <a href='#'>privacy policy  </a>
            </LegacyCard.Section>
        </LegacyCard>
    </Page>
  )
}
Card

export default About