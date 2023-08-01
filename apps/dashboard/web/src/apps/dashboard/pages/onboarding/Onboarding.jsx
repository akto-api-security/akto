import React from 'react'
import { Avatar, Button, ButtonGroup, Frame, TopBar } from "@shopify/polaris"
import {ClipboardMinor} from "@shopify/polaris-icons"
import "./Onboarding.css"

function Onboarding() {

    const openUrl = (url) =>{
      window.open(url)
    }
    const avatar = (
      <Avatar customer size='extraSmall' source='/public/discord.svg' name='discord' />
    )

    const topbarButtons = (
      <ButtonGroup>
        <Button onClick={() => openUrl("https://docs.akto.io")} icon={ClipboardMinor}> Docs </Button>
        <Button onClick={()=> openUrl("https://discord.com/invite/Wpc6xVME4s")} icon={avatar}>
          Discord
        </Button>
      </ButtonGroup>
    )

    const topBar = (
      <TopBar secondaryMenu={topbarButtons} />
    )

    const logo = {
      width: 124,
      topBarSource:
        '/public/akto_name_with_logo.svg',
      url: '/dashboard',
      accessibilityLabel: 'Akto Icon',
    };

    return (
      <div className='onboarding'>
        <Frame logo={logo} topBar={topBar}>
        </Frame>
      </div>
    )
}

export default Onboarding