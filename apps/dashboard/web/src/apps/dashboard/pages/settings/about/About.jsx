import { Box, LegacyCard, Page, Text } from '@shopify/polaris'
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

    const infoComponent = (
        objArr.map((item,index)=>(
            <Box key={item.title}>
                <Text fontWeight='semi-bold' color='subdued'>{item.title}</Text>
                <Text fontWeight='bold'>{item.text}</Text>
                {index < objArr.length - 1 ? <br/> : null}
            </Box>
        ))
    )
  return (
    <Page
        title="About"
        divider
    >
        <LegacyCard title="Account information">
            <LegacyCard.Section>
                <p>Take control of your profile, privacy settings, and preferences all in one place.</p>
            </LegacyCard.Section>
            <LegacyCard.Section>
                {infoComponent}
            </LegacyCard.Section>
            <LegacyCard.Section>
                View our <a href='#'>terms of service</a> and <a href='#'>privacy policy  </a>
            </LegacyCard.Section>
        </LegacyCard>
    </Page>
  )
}

export default About