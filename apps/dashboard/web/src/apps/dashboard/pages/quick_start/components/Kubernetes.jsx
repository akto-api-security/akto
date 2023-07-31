import React from 'react'
import CompleteSetup from './CompleteSetup'
import QuickStartStore from '../quickStartStore'
import Store from '../../../store'
import { HorizontalStack, VerticalStack } from '@shopify/polaris'
import JsonComponent from './shared/JsonComponent'

function Kubernetes() {

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

    const copyText = () => {
      navigator.clipboard.writeText("kubectl apply -f akto-daemonset-deploy.yaml -n <NAMESPACE>")
      setToast(true, false, "Command Copied !!")
    }

    const deploymentMethod = "KUBERNETES"
    const localComponentText = "Use Kubernetes based traffic collector to send traffic to Akto."
    const bannerTitle = "Setup using Kubernetes"
    const docsUrl =  "https://docs.akto.io/traffic-connections/kubernetes"
    const bannerContent = "Akto daemonset config can duplicate your node-traffic inside Kubernetes and send to Akto dashboard." 
    const noAccessText = "Your dashboard's instance needs relevant access to setup daemonset stack, please do the following steps:"
    const setupButtonText = "Setup Daemonset stack"

    const stackCompleteComponent = (
      <VerticalStack gap="2">
        <span>You need to setup a daemonset for your Kubernetes environment:</span>

        <VerticalStack gap="1">
          <span>1. Create a file akto-daemonset-deploy.yaml with the following config:</span>
          <JsonComponent title="Yaml Template" toolTipContent="Copy the yaml template" onClickFunc={()=> copyYaml()} contentValue={yamlContent} language="yaml" />
        </VerticalStack>

        <VerticalStack gap="1">
          <span>2. Replace the following values:</span>
          <VerticalStack gap="1">
            <span>a. {'{' + 'NAMESPACE' + '}'} : With the namespace of your app</span>
            <span>b. {'{' + 'APP_NAME' + '}'} : Replace with the name of the app where daemonset will be deployed. Note that this has to be done at 3 places in the config</span>
          </VerticalStack>
        </VerticalStack>

        <VerticalStack gap="1">
          <span>3. Run the following command with appropriate namespace:</span>
          <JsonComponent title="Command" toolTipContent="Copy the command" onClickFunc={()=> copyText()} contentValue="kubectl apply -f akto-daemonset-deploy.yaml -n <NAMESPACE>" language="text/plain" minHeight="50px"/>
        </VerticalStack>

        <HorizontalStack gap="1">
          <span>4. Add traffic sources from our docs. Click</span>
          <a href='dashboard/observe/inventory'>here</a>
        </HorizontalStack>
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

export default Kubernetes