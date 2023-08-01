import React from 'react'
import CompleteSetup from './CompleteSetup'
import JsonComponent from './shared/JsonComponent'
import { HorizontalStack, VerticalStack } from '@shopify/polaris'
import Store from '../../../store'
import QuickStartStore from '../quickStartStore'

function FargateSource() {

    const yamlContent = QuickStartStore(state => state.yamlContent)
    const setToastConfig = Store(state => state.setToastConfig)
    const setToast = (isActive, isError, message) => {
        setToastConfig({
            isActive: isActive,
            isError: isError,
            message: message
        })
    }

    const copyYaml = () => {
        navigator.clipboard.writeText(yamlContent)
        setToast(true, false, "Variables Copied to Clipboard !")
    }

    const deploymentMethod = "FARGATE"
    const localComponentText = "Use generic traffic collector to send traffic to Akto."
    const bannerTitle = "Setup using Fargate"
    const docsUrl =  "https://docs.akto.io/traffic-connections/aws-fargate"
    const bannerContent = "Akto container config can duplicate your container-traffic and send to Akto dashboard." 
    const noAccessText = "Use this for AWS Fargate, AWS ECS, TCP-collector, Docker, Docker-compose. Your dashboard's instance needs relevant access to setup traffic processors, please do the following steps:"
    const setupButtonText = "Setup traffic processors"

    const stackCompleteComponent = (
        <VerticalStack gap="2">
            <HorizontalStack gap="1">
                <span>Add traffic sources from our docs. Click</span>
                <a href='dashboard/observe/inventory'>here</a>
            </HorizontalStack>

            <JsonComponent title="Variables" toolTipContent="Copy your variables" onClickFunc={()=> copyYaml()} dataString={yamlContent} language="yaml" minHeight="50px"/>
        </VerticalStack>
    )

    return (
        <CompleteSetup
            deploymentMethod={deploymentMethod}
            localComponentText={localComponentText}
            bannerTitle={bannerTitle}
            docsUrl={docsUrl}
            bannerContent={bannerContent}
            noAccessText={noAccessText}
            setupButtonText={setupButtonText}
            stackCompleteComponent={stackCompleteComponent}
        />
    )
}

export default FargateSource