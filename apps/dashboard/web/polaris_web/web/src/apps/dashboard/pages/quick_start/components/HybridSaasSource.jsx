import { Box, Text, VerticalStack } from '@shopify/polaris'
import { useEffect, useState, useRef } from 'react'
import api from '../api'
import JsonComponent from './shared/JsonComponent'
import func from "@/util/func"
import Dropdown from '../../../components/layouts/Dropdown'

function HybridSaasSource() {
    const [apiToken, setApiToken] = useState("");
    const ref = useRef(null)
    const runtimeSvcCommand = "helm install akto-mini-runtime akto/akto-mini-runtime -n dev --set mini_runtime.aktoApiSecurityRuntime.env.databaseAbstractorToken=\"" + apiToken + "\"";
    const rcopyCommand = ()=>{func.copyToClipboard(runtimeSvcCommand, ref, null)}
    const helmAddCommand = "helm repo add akto https://akto-api-security.github.io/helm-charts/";
    const copyCommandUtil = (data)=>{func.copyToClipboard(data, ref, null)}

    const [selectedExpiryDuration, setSelectedExpiryDuration] = useState(6);
    const [selectedScope, setSelectedScope] = useState([]);
    const [scopeOptions, setScopeOptions] = useState([]);

    const fetchRuntimeHelmCommand = async(selectedExpiryDuration, selectedScope) => {
        await api.fetchRuntimeHelmCommand(selectedExpiryDuration, selectedScope).then((resp) => {
            if (!resp) return
            setApiToken(resp?.apiToken)
        })
    }

    const fetchModuleTypes = async() => {
        await api.fetchModuleTypes().then((resp) => {
            const moduleTypes = resp?.moduleTypes || []
            setScopeOptions(moduleTypes.map(value => ({
                label: value.split('_').map(func.toSentenceCase).join(' '),
                value
            })))
        })
    }

    const expiryDurationOptions = [
        { label: '1 month', value: 1 },
        { label: '3 months', value: 3 },
        { label: '6 months', value: 6 },
        { label: '9 months', value: 9 },
        { label: '12 months', value: 12 },
        { label: 'Never expire', value: -1 }
    ]

    const getLabelFromValue = (value) => {
        const option = expiryDurationOptions.find(option => option.value === value);
        return option ? option.label : '';
    }

    const hybridSaasComponent = (
        <VerticalStack gap="2">
          <div ref = {ref}/>

          <span>1. Run the below command to add akto helm repo. </span>

          <VerticalStack gap="1">
            <JsonComponent title="Add akto helm repo" toolTipContent="Copy command" onClickFunc={()=> copyCommandUtil(helmAddCommand)} dataString={helmAddCommand} language="text" minHeight="60px" />
          </VerticalStack>

          <span>2. Select the expiry time of the jwt token used in the command. </span>
          <Box maxWidth="180px" paddingInlineStart={"4"}>
            <Dropdown
                id={`select-expiry`}
                menuItems={expiryDurationOptions}
                value={getLabelFromValue(selectedExpiryDuration)}
                initial={selectedExpiryDuration}
                selected={(type) => {setSelectedExpiryDuration(type); fetchRuntimeHelmCommand(type, selectedScope)}}
            />
          </Box>

          <span>3. (Optional) Select the scope(s) this token should be restricted to. Leave empty to generate a legacy (unscoped) token. </span>
          <Box maxWidth="280px" paddingInlineStart={"4"}>
            <Dropdown
                id={`select-scope`}
                allowMultiple
                placeHolder="Select scope(s)"
                menuItems={scopeOptions}
                preSelected={selectedScope}
                selected={(scope) => {setSelectedScope(scope); fetchRuntimeHelmCommand(selectedExpiryDuration, scope)}}
            />
            </Box>

          <span>4. Run the below command to setup Akto Runtime service. Change the namespace according to your requirements. </span>

          <VerticalStack gap="1">
            <JsonComponent title="Runtime Service Command" toolTipContent="Copy command" onClickFunc={()=> rcopyCommand()} dataString={runtimeSvcCommand} language="text" minHeight="450px" />
          </VerticalStack>

        </VerticalStack>
      )

    useEffect(()=> {
        fetchRuntimeHelmCommand(selectedExpiryDuration, selectedScope)
        fetchModuleTypes()
    },[])

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Seamlessly deploy Akto with our hybrid setup and start viewing your API traffic in few minutes.
            </Text>
            {hybridSaasComponent}

        </div>
    )
}

export default HybridSaasSource