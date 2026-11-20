import { Text, VerticalStack } from '@shopify/polaris'
import { useState, useRef } from 'react'
import JsonComponent from './shared/JsonComponent'
import func from "@/util/func"
import InformationBannerComponent from './shared/InformationBannerComponent'
import RuntimeTokenField from './RuntimeTokenField'

// This page is specifically for the hybrid SaaS runtime helm chart, so the token it
// generates is always scoped to the runtime module. Token generation for other scopes
// (guardrails, threat-detection, etc.) has its own separate entry - see authenticationTokenObj
// in transform.js.
const RUNTIME_SCOPE = ['MINI_RUNTIME']
const TOKEN_PLACEHOLDER = "<generate the token in step 3>"

const defaultTokenName = () => `mini-runtime-helm-${new Date().toISOString().slice(0, 10)}`

function HybridSaasSource() {
    const [apiToken, setApiToken] = useState("");
    const ref = useRef(null)
    const runtimeSvcCommand = "helm install akto-mini-runtime akto/akto-mini-runtime -n dev --set mini_runtime.aktoApiSecurityRuntime.env.databaseAbstractorToken=\"" + (apiToken || TOKEN_PLACEHOLDER) + "\"";
    const rcopyCommand = ()=>{func.copyToClipboard(runtimeSvcCommand, ref, null)}
    const helmAddCommand = "helm repo add akto https://akto-api-security.github.io/helm-charts/";
    const copyCommandUtil = (data)=>{func.copyToClipboard(data, ref, null)}

    const hybridSaasComponent = (
        <VerticalStack gap="2">
          <div ref = {ref}/>

          <span>1. Run the below command to add akto helm repo. </span>

          <VerticalStack gap="1">
            <JsonComponent title="Add akto helm repo" toolTipContent="Copy command" onClickFunc={()=> copyCommandUtil(helmAddCommand)} dataString={helmAddCommand} language="text" minHeight="60px" />
          </VerticalStack>

          <RuntimeTokenField
              id="select-expiry"
              startStep={2}
              fixedScope={RUNTIME_SCOPE}
              showToken={false}
              initialName={defaultTokenName()}
              expiryStepText="Name the token and select the expiry time of the jwt token used in the command."
              tokenStepText="Token generated. It is included in the command below."
              onTokenChange={setApiToken}
          />

          <span>4. Run the below command to setup Akto Runtime service. Change the namespace according to your requirements. </span>

          <VerticalStack gap="1">
            <JsonComponent title="Runtime Service Command" toolTipContent="Copy command" onClickFunc={()=> rcopyCommand()} dataString={runtimeSvcCommand} language="text" minHeight="450px" />
          </VerticalStack>

        </VerticalStack>
      )

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Seamlessly deploy Akto with our hybrid setup and start viewing your API traffic in few minutes.
            </Text>
            <InformationBannerComponent
                content='The token generated below is scoped to the runtime module only. To generate a token for another module (guardrails, threat detection, etc.), select "Generate Authentication Token" from the quick start list instead.'
                docsUrl=''
            />
            {hybridSaasComponent}

        </div>
    )
}

export default HybridSaasSource
