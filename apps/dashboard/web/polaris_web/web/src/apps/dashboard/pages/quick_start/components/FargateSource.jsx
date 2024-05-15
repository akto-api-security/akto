import React from 'react'
import CompleteSetup from './CompleteSetup'
import JsonComponent from './shared/JsonComponent'
import { HorizontalStack, VerticalStack } from '@shopify/polaris'
import QuickStartStore from '../quickStartStore'
import func from '@/util/func'
import { useRef } from 'react'

function FargateSource({docsUrl,bannerTitle, innerUrl}) {

    const yamlContent = QuickStartStore(state => state.yamlContent)
    const ref = useRef(null)

    const copyYaml = () => {
        func.copyToClipboard(yamlContent, ref, "Variables Copied to Clipboard !")
    }

    const deploymentMethod = "FARGATE"
    const localComponentText = "Use generic traffic collector to send traffic to Akto."
    const bannerContent = "Akto container config can duplicate your container-traffic and send to Akto dashboard." 
    const noAccessText = "Use this for AWS Fargate, AWS ECS, TCP-collector, Docker, Docker-compose. Your dashboard's instance needs relevant access to setup traffic processors, please do the following steps:"
    const setupButtonText = "Setup traffic processors"

    const stackCompleteComponent = (
        <VerticalStack gap="2">
            <div ref = {ref}/>
            <HorizontalStack gap="1">
                <span>Your stack is ready. Now follow the steps mentioned 
                {" "} <a target="_blank" href={innerUrl}>here</a>. You will need the following variables for the next steps.
                </span>
            </HorizontalStack>

            <JsonComponent title="Variables" toolTipContent="Copy your variables" onClickFunc={()=> copyYaml()} dataString={yamlContent} language="yaml" minHeight="100px"/>
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