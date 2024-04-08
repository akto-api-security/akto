import React, { useEffect, useState, useRef } from 'react'
import Store from '../../../store'
import BannerComponent from './shared/BannerComponent'
import { Button, ProgressBar, Text, VerticalStack } from '@shopify/polaris'
import quickStartFunc from '../transform'
import api from '../api'
import NoAccessComponent from './shared/NoAccessComponent'
import SpinnerCentered from "../../../components/progress/SpinnerCentered"
import func from "@/util/func"
import QuickStartStore from '../quickStartStore'

function CompleteSetup({deploymentMethod, localComponentText, bannerTitle, docsUrl, bannerContent, noAccessText, setupButtonText, stackCompleteComponent}) {
    const [aktoDashboardRoleName, setAktoDashboardRoleName] = useState(null)
    const [policyLines, setPolicyLines] = useState(quickStartFunc.getPolicyLines(deploymentMethod))
    const [hasRequiredAccess, setHasRequiredAccess] = useState(false)
    const [loading, setLoading] = useState(false)
    const [stackStatus, setStackStatus] = useState("")
    const [initialClicked, setInitialClicked] = useState(false)
    const [statusText, setStatusText] = useState('')
    const [progressBar, setProgressBar] = useState({show: false, value: 0, max_deployment_time_in_ms: 8 * 60 * 1000})
    const [yaml, setYaml] = useState(quickStartFunc.getYamlLines(deploymentMethod))

    const ref = useRef(null)

    const setYamlContent = QuickStartStore(state => state.setYamlContent)

    const isLocalDeploy = Store(state => state.isLocalDeploy)
    const isAws = Store(state => state.isAws)

    const setToastConfig = Store(state => state.setToastConfig)
    const setToast = (isActive, isError, message) => {
        setToastConfig({
            isActive: isActive,
            isError: isError,
            message: message
        })
    }

    const renderProgressBar = (createTime) => {
        setProgressBar(quickStartFunc.renderProgressBar(createTime, progressBar))
        }
    
        const removeProgressBarAndStatuschecks = (intervalId) => {
        clearInterval(intervalId)
        setProgressBar(quickStartFunc.removeProgressBarAndStatuschecks(progressBar))
        }
    

    const handleStackState = (stackState, intervalId) => {
        switch (stackState.status) {
            case 'CREATE_IN_PROGRESS':
            renderProgressBar(stackState.creationTime);
            setStatusText('We are setting up a daemonset stack for you! Grab a cup of coffee, sit back and relax while we work our magic!')
            break;
            case 'CREATE_COMPLETE':
            removeProgressBarAndStatuschecks(intervalId);
            break;
            case 'DOES_NOT_EXISTS':
            removeProgressBarAndStatuschecks(intervalId);
            setStatusText('Stack not setup yet, click on the above button!')
            break;
            case 'TEMP_DISABLE':
            removeProgressBarAndStatuschecks(intervalId)
            setStatusText('Current deployment is in progress, please refresh this page in sometime.')
            break;
            default:
            removeProgressBarAndStatuschecks(intervalId);
            setStatusText('Something went wrong while setting up stack, please write to us at support@akto.io')
        }      
        }

    const checkStackState = () => {
        if(isAws){
            let intervalId = null;
            setLoading(true)

            intervalId = setInterval(async () => {
                await api.fetchStackCreationStatus({deploymentMethod: deploymentMethod}).then((resp) => {
                    setLoading(false)
                    setStackStatus(resp.stackState.status)
                    handleStackState(resp.stackState, intervalId)
                    if(resp.aktoNLBIp && resp.aktoMongoConn){
                        let yamlCopy = yaml
                        for(let i=0; i< yaml.length; i++){
                            let line = yamlCopy[i];
                            line = line.replace('<AKTO_NLB_IP>', resp.aktoNLBIp);
                            line = line.replace('<AKTO_MONGO_CONN>', resp.aktoMongoConn);
                            yamlCopy[i] = line;
                        }
                        setYaml(yamlCopy)
                        const yamlContent = yaml.join('\n')
                        setYamlContent(yamlContent)
                    }
                })
            }, 5000)
        }
    }

    const fetchLBs = async() => {
        if(isAws){
            setLoading(true)
            await api.fetchLBs({deploymentMethod: deploymentMethod}).then((resp) => {
                if (!resp.dashboardHasNecessaryRole) {
                    let policyLinesCopy = policyLines
                    for (let i = 0; i < policyLines.length ; i++) {
                        let line = policyLinesCopy[i];
                        line = line.replaceAll('AWS_REGION', resp.awsRegion);
                        line = line.replaceAll('AWS_ACCOUNT_ID', resp.awsAccountId);
                        policyLinesCopy[i] = line;
                    }
                    setPolicyLines(policyLinesCopy)
                }
                setHasRequiredAccess(resp.dashboardHasNecessaryRole)
                // setHasRequiredAccess(true)
                setAktoDashboardRoleName(resp.aktoDashboardRoleName); 
                setLoading(false)
                checkStackState()
            })
        }
    } 

    useEffect(() => {
        fetchLBs()
        checkStackState()
    },[])

    useEffect(() => {
        const yamlContent = yaml.join('\n')
        setYamlContent(yamlContent)
    }, [yaml])

    const urlFargate = "https://us-east-1.console.aws.amazon.com/iam/home#/roles/" + aktoDashboardRoleName  + "$createPolicy?step=edit";

    const steps = quickStartFunc.getDesiredSteps(urlFargate)
    const formattedJson = func.convertPolicyLines(policyLines)

    const copyRequest = () => {
        func.copyToClipboard(formattedJson, ref, "Policy copied to clipboard.")
    }
    
    const creatFargateStack = async() => {
        setLoading(true)
        setStatusText("Starting Deployment!!")
        await api.createRuntimeStack(deploymentMethod).then((resp)=> {
            setInitialClicked(true)
            setLoading(false)
            checkStackState()
        })
    }

    const checkConditions = () => {
        return (stackStatus === "CREATE_COMPLETE" || stackStatus === "CREATE_IN_PROGRESS" || stackStatus === "CREATION_FAILED")
    }

    const buttonActive = () => {
        const conditions = checkConditions()
        return !(initialClicked || conditions)
    }

    const isButtonActive = buttonActive()

    const accessComponent = (
        loading ? 
        <SpinnerCentered />
        :
        <VerticalStack gap="2">
            {isButtonActive ? <Button primary onClick={creatFargateStack} loading={loading}>{setupButtonText}</Button> : null}
            <Text variant="bodyMd" as="h3">{statusText}</Text>
            {progressBar.show ? <ProgressBar progress={progressBar.value} size="small" color="primary" /> : null }
            {/* {stackCompleteComponent} */}
            {stackStatus === "CREATE_COMPLETE" ?
                stackCompleteComponent
                : null
            }
        </VerticalStack>
    )

    const localDeployObj = {
        text:  localComponentText,
        component: <BannerComponent title={bannerTitle} docsUrl={docsUrl} content={bannerContent} />
    }

    const noAccessObject = {
        text: noAccessText,
        component: <NoAccessComponent  dataString={formattedJson} onClickFunc={() => copyRequest()} steps={steps} title="Policy JSON" toolTipContent="Copy JSON"/>
    }

    const accessObj = {
        component: accessComponent
    }

    const displayFunc = () => {
        if (isLocalDeploy || !isAws) {
            return localDeployObj
        }
        if (hasRequiredAccess) {
            return accessObj
        }
        return noAccessObject
    }

    const displayObj = displayFunc();

    return (
        <div className='card-items'>
            {loading ? null : <Text>{displayObj?.text}</Text>}
            {loading ? <SpinnerCentered /> : displayObj?.component}
            <div ref = {ref}/>
        </div>
    )
}

export default CompleteSetup