import React, { useEffect } from 'react'
import { Avatar, Button, ButtonGroup, Frame, TopBar } from "@shopify/polaris"
import {ClipboardMinor} from "@shopify/polaris-icons"
import "./Onboarding.css"
import OnboardingBuilder from './components/OnboardingBuilder'
import transform from "../testing/transform"

function Onboarding() {

    const openUrl = (url) =>{
      window.open(url)
    }
    const avatar = (
      <Avatar customer size='extraSmall' source='/public/discord.svg' name='discord' />
    )

    const topbarButtons = (
      <ButtonGroup>
        <Button size="slim" onClick={() => openUrl("https://docs.akto.io")} icon={ClipboardMinor}> Docs </Button>
        <Button size="slim" onClick={()=> openUrl("https://discord.com/invite/Wpc6xVME4s")} icon={avatar}>
          Discord
        </Button>
      </ButtonGroup>
    )

    const topBar = (
      <TopBar secondaryMenu={topbarButtons} />
    )

    useEffect(()=> {
      transform.setTestMetadata()
    },[])

    const logo = {
      width: 76,
      topBarSource:
        '/public/akto_name_with_logo.svg',
      url: '/dashboard',
      accessibilityLabel: 'Akto Icon',
    };

    return (
      <div className='onboarding'>
        <Frame logo={logo} topBar={topBar}>
          <OnboardingBuilder/>
        </Frame>
      </div>
    )
}

export default Onboarding